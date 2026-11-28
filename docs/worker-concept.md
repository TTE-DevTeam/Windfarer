# The worker concept (WorldManager)

Windfarer does its heavy lifting (detecting crafts, calculating movements, status updates, fuel, ...) on **other threads
than the main thread** and brings the results back to the main thread in a controlled way. This document explains how that
works and how to write tasks for it so they stay correct and fast.

* Package: `net.countercraft.movecraft.processing` (module `api`)
* Entry point: `WorldManager.INSTANCE`

## Why

* Bukkit/Paper objects (block states, entities, events, inventories) may only be used on the main thread.
* The world is large and crafts are large. Reading thousands of blocks on the main thread costs ticks.
* World changes must be applied on the main thread, but they must not eat a whole tick either.

So the work is split: **calculate somewhere else, apply on the main thread within a time budget.**

## The pieces

| Piece | Role |
|---|---|
| `WorldManager` | Singleton. `run()` is called every tick on the main thread (`Movecraft#onEnable`). Knows the thread-local "current worker" and offers `executeMain`. |
| `WorldManagerWorkers` | The available workers: `HIGH_PRIORITY`, `NORMAL_PRIORITY`, `LOW_PRIORITY`. |
| `WorldManagerWorker` | One worker: a queue of tasks, a thread pool, a queue of requests to the main thread and a queue of effects. Implementations: `BlockingWorldManagerWorker`, `NonBlockignWorldManagerWorker` (both extend `BaseWorldManagerWorker`). |
| `WindfarerThreadPool` | The thread pool of a worker. A `ForkJoinPool` with a limited capacity and a few spare "compensation" threads for tasks which wait on child tasks. |
| Task | A `Supplier<@Nullable Effect>` (or a `Runnable`). Runs on a pool thread. |
| `Effect` | What a task wants to be done on the main thread afterwards (`Effect.run()`). Effects can be chained with `andThen`; `Effect.NONE` does nothing. |
| `MovecraftWorld` / `CachedMovecraftWorld` | Thread-safe view on the blocks of a world, built on chunk snapshots. |
| `AbstractWorker<V>` | A helper for sub tasks which run on another pool but still need the worker context. |
| `WorldManagerExecutors.WORK_STEALING_POOL` | The JVM wide fork-join pool for pure CPU work which needs nothing from the worker. |

### The workers

| Worker | Type | Intended for |
|---|---|---|
| `HIGH_PRIORITY` | blocking | Things a player is waiting for right now (e.g. detection). The main thread **waits** for its tasks in the tick, up to a timeout. |
| `NORMAL_PRIORITY` | non-blocking | Regular recurring work (fuel, status updates, banners). |
| `LOW_PRIORITY` | non-blocking | Everything else, background work. Default of the deprecated `submit` methods. |

The thread counts, the extra queue length ("overpressure") and the time budgets come from `Settings`
(`highPriorityThreadCount`, `normalPriorityThreadCount`, `...PoolOverpressure`, `maxElapsedTimeForWorldChanges`,
`maxElapsedTimeForSyncTaskProcessing`). A time budget below zero means "no limit".

## What happens in a tick

```
main thread, every tick: WorldManager.run()
  for each worker which has unfinished work:
      1. startTasks()                 take queued tasks and start them on the pool, as long as the pool has capacity
                                      (parallelism + overpressure minus what is running or queued)
      2. processMainThreadRequests()  run what worker threads asked the main thread to do (executeMain)
                                        - non-blocking worker: run what is there (within the time budget), continue
                                        - blocking worker: wait until all tasks of this worker have finished, run their
                                          requests while waiting; on timeout the tasks are cancelled and the queued
                                          effects are discarded (and logged as severe)
      3. processWorldChanges()        run the effects the finished tasks returned, within the time budget
```

A worker "has unfinished work" while it has queued tasks, running tasks, requests, effects or tracked async work.
`WorldManager.isRunning()` is true while any worker has work. Tasks of a worker run in parallel, their effects are queued in the order the tasks complete and run one after another.

## Writing a task

```java
public class MyTask implements Supplier<Effect> {
    @Override
    public Effect get() {                       // runs on a thread of the worker, NOT on the main thread
        // read, calculate ...
        return () -> { /* runs on the main thread, change the world here */ };
    }
}

WorldManager.INSTANCE.submit(new MyTask(), WorldManagerWorkers.NORMAL_PRIORITY);
```

`submitAndRunNow` starts the task immediately (if the manager is running) instead of waiting for the next tick.
A task which returns `null` or `Effect.NONE` just has no follow-up. Exceptions are logged with their stack trace and the
effect of that task is dropped, so release everything you hold in a `finally`.

### Reading the world

* `MovecraftWorld#getMaterial` and `#getData` are safe from any thread. Use them.
* `MovecraftWorld#getState` (block states, inventories) needs the main thread. It goes through
  `WorldManager.executeMain(...)`, which hands the call to the main thread and **blocks the calling thread until it is done**.
  This only works on a thread which has a worker (see "The current worker"). Fetch what you need once, not per block.
* Entities, events, inventories, the scheduler: only through `executeMain` or in an effect.

### Changing the world

Return an `Effect`. It runs on the main thread, in order, within the time budget of the worker; what does not fit is run
in a later tick. Keep effects short and cut big changes into several effects.
An effect which calls Bukkit events belongs on the main thread anyway (`CraftStatusUpdateEvent`, for example).

### The current worker

`WorldManager.INSTANCE.getCurrentWorker()` is a thread-local. It is set while a task of a worker runs (by
`WindfarerThreadPool#enqueueTask`), so that `executeMain`, `WorldManagerWorker.getThreadLocalOrDefault(...)` and nested
tasks know which worker they belong to. On any other thread it is empty.

> `RejectedExecutionException: No current worker for this thread!` means: you called `executeMain` (or `getState`) on a thread
> which is not running a task of a worker, typically a `CompletableFuture` callback or a thread of the common pool.
> **Do not fix this by setting the thread-local by hand.** That bypasses the mechanism. Instead, make the work a task of the
> WorldManager (see the pattern below), or use an `AbstractWorker` for sub tasks.

## Parallel work inside a task

* **Child tasks on the same worker:** `worker.submit(...)` returns a `CompletableFuture`. Waiting for it with `join()` inside a
  task is fine, the pool compensates with a spare thread. Do not wait on something which can only be produced by the
  main thread *and* is waiting for you.
* **Pure CPU work** (no `executeMain`, no `getState`): split it into slices and run them on
  `WorldManagerExecutors.WORK_STEALING_POOL`. If a slice needs the worker context, wrap it in an `AbstractWorker`, which sets
  the worker for its run and restores the previous one. `StatusUpdateTask` does it this way with `getData` only.
* **Do not block a worker thread while waiting for something slow.** Return from `get()` and continue in a callback, see the next section.

## Continuing after `get()` returned (asynchronous work)

If a task starts asynchronous work (a `CompletableFuture` chain) and returns from `get()` early, the worker must not conclude
that it has nothing left to do:

```java
CompletableFuture<Void> work = ... ;               // the asynchronous part
worker.trackAsyncWork(work);                       // keeps the worker alive until it completes
return Effect.NONE;
```

* The future should complete **after** the last callback that queues a follow-up (a new task, an effect) has done so.
  Then the worker never has a moment in which it looks finished, although something is still coming.
* Deliver results as effects: `worker.submitEffectToWorldChanges(effect)`.
* Callbacks of a `CompletableFuture` run on whichever thread completes it, possibly the main thread. Keep them tiny.
  If real work follows, **submit a new task** instead of doing it in the callback.

### The pattern for "wait for something, then work as a task"

This is what `FuelBurnTask` does to wait for the craft state without blocking a thread
(see [craft-state.md](craft-state.md)):

```java
@Override
public Effect get() {
    final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);

    CompletableFuture<Void> reservation = craft.getStateTracker()
            .reserveStateAndWait(CraftState.FUEL_BURN, 5, TimeUnit.SECONDS)
            .handle((handle, throwable) -> {
                if (throwable != null) { /* timed out or failed, log, clean up */ return null; }
                // The new task owns the handle and releases it. Submit BEFORE this future completes.
                WorldManager.INSTANCE.submit(new Calculation(handle), WorldManagerWorkers.NORMAL_PRIORITY);
                return null;
            });

    worker.trackAsyncWork(reservation);
    return Effect.NONE;
}
```

`Calculation#get()` then runs as a regular task on a worker thread, with a current worker, and may use `getState` and
`executeMain`. It returns the effect, which releases the handle after the main-thread part is done.

## Choosing a worker

* Does a player wait for it, and is it short? `HIGH_PRIORITY`. The main thread waits for these tasks in the same tick,
  so they must finish quickly. A task which waits for the craft state or a long calculation does **not** belong here.
* Recurring or background work: `NORMAL_PRIORITY` or `LOW_PRIORITY`.
* The thread count of a worker limits how many tasks run at once; a task that blocks occupies one of them.

## Pitfalls

| Symptom | Likely cause |
|---|---|
| `No current worker for this thread!` | `executeMain`/`getState` on a thread without a worker (callback, common pool). Submit a task instead. |
| `WorldManager timed out on task query!` (severe) | A task of the blocking worker did not finish in time (e.g. it waits for something that never comes). Its tasks are cancelled and its effects discarded. |
| Tasks start late or pile up | The pool is full (parallelism + overpressure). Look for tasks which block while waiting. |
| The worker never goes idle / effects arrive late | A future passed to `trackAsyncWork` never completes. |
| A state of a craft stays locked | A task failed before it released its state handle; release in `finally`, or hand it to the effect. |
| `getState` is slow | It is one round trip to the main thread per call. Read the data you need in one `executeMain`. |

## Debugging

* `WorldManagerWorkers.X.sendDebugInfo(consumer)` / `WorldManagerWorker#dumpDebugInfo` print the queues, the number of
  running tasks, the tracked async work and the statistics of the pool (which task classes ran how often).
* With `Settings.Debug` the pool state is printed whenever a worker checks its capacity.
* Threads are named `<WORKER>-Worker-<n>` (for example `NORMAL_PRIORITY-Worker-3`), uncaught exceptions are logged with the thread name.

## Where it is used

| Task | Worker | Notes |
|---|---|---|
| `DetectionTask` | `HIGH_PRIORITY` | Flood fill on the pool, the detect event through `executeMain`, effects for the result. |
| `StatusUpdateTask` | `NORMAL_PRIORITY` | Reserves the craft state asynchronously, counts blocks in slices on the work stealing pool using only `getData`. See the warning in the class. |
| `FuelBurnTask` | `NORMAL_PRIORITY` | Reserves the state, then a `Calculation` task reads furnace inventories (`getState`) and returns the effect. |
| `UpdateBannersTask` | `NORMAL_PRIORITY` | The post movement step of a movement. Collects the banners asynchronously, updates them in an effect and releases the movement's states. |
| `Translation-/RotationTask` | own `AsyncManager` | Older system, still used for movements. States are reserved by `AsyncManager`, see craft-state.md. |
