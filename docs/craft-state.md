# Craft states

Several tasks work on a craft at the same time: a movement is calculated, a status update reads the hitbox, fuel is
burned, a subcraft is cut out, blocks of a sinking craft disintegrate. They must not step on each other's toes,
but they must not block each other more than necessary either.

The craft state system describes **what a task does to a craft** and lets the craft decide **who may run next to whom**.
It replaces the old single "processing" boolean (`Craft#setProcessing`, `Craft#isNotProcessing`), which could only say
"something is going on".

* Package: `net.countercraft.movecraft.craft.state` (module `api`)
* Entry point: `craft.getStateTracker()`
* Tests: `CraftStateTrackerTest`

## The four building blocks

| Class | What it is |
|---|---|
| `CraftStateFlag` | A single property of "what is going on with the craft". Flags are *counted*, so many tasks can hold the same flag at the same time. |
| `CraftState` | The immutable *description* of a kind of task: which flags it **holds** while it is active, and which flags **block** it from being set. A static constant per kind of task, see below. |
| `CraftStateHandle` | One *active instance* of a state, owned by exactly one task. Releasing it gives its flags back. |
| `CraftStateTracker` | One per craft. Counts the flags, decides if a state may be set, queues waiting requests. All state changes are atomic. |

### Flags

| Flag | Meaning |
|---|---|
| `READ_CRAFT_SHAPE` | The shape (hitbox) is being read and must not change. |
| `MODIFY_CRAFT_SHAPE` | The shape is being modified. |
| `MOVEMENT_CALCULATION` | A movement (translation, rotation) is being calculated. |
| `MOVEMENT_PROCESSING` | A calculated movement is being applied to the world. |
| `SUBCRAFT` | A subcraft is being detected, cut out of this craft, moves, or is merged back. Reading operations are fine meanwhile, movements are not. |

### The one rule

A state `S` can be set if **none of the flags in `S.blockedBy()` is currently held by anybody else**.
When it is set, the flags in `S.holds()` are held (each one counted once more) until the handle is released.

* A state is compatible with itself (many handles of it can be active) unless it lists one of its own flags in `blockedBy`.
  `SUBCRAFT_CUT_OUT` holds `SUBCRAFT` but is not blocked by it: several torpedoes may be cut out at the same time.
  `MOVEMENT_CALCULATION` holds `MOVEMENT_CALCULATION` and is blocked by it: only one movement is calculated at a time.
* "Blocked by" is only checked when a state is **set**. A held flag never kicks anybody out. Whoever should be kept
  out of a running task must list the flags of that task in its own `blockedBy`.

## The states of Windfarer

Defined as constants in `CraftState`. Read "holds" as "what others have to take into account", read "blocked by" as
"when I may not start".

| State | Used by | Holds | Blocked by |
|---|---|---|---|
| `STATUS_UPDATE` | `StatusUpdateTask` | `READ_CRAFT_SHAPE` | `MODIFY_CRAFT_SHAPE`, `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING` |
| `FUEL_BURN` | `FuelBurnTask` | `READ_CRAFT_SHAPE` | `MODIFY_CRAFT_SHAPE`, `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING` |
| `MOVEMENT_CALCULATION` | Translation-/RotationTask (via `AsyncManager`) | `MOVEMENT_CALCULATION`, `READ_CRAFT_SHAPE` | `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING`, `MODIFY_CRAFT_SHAPE`, `SUBCRAFT` |
| `MOVEMENT_EXECUTION` | Result of a movement is applied | `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING`, `MODIFY_CRAFT_SHAPE` | all flags |
| `CRAFT_SHAPE_MODIFICATION` | Sinking (disintegration, bottom layer) | `MODIFY_CRAFT_SHAPE` | `READ_CRAFT_SHAPE`, `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING`, `SUBCRAFT` |
| `PISTON_MODIFICATION` | Merging piston extensions | `MODIFY_CRAFT_SHAPE` | `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING` |
| `SUBCRAFT_DETECTION` | Parent while a subcraft is detected | `READ_CRAFT_SHAPE`, `SUBCRAFT` | `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING`, `MODIFY_CRAFT_SHAPE` |
| `SUBCRAFT_MOVEMENT_PARENT` | Parent during the whole lifetime of a short lived subcraft | `SUBCRAFT` | `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING`, `MODIFY_CRAFT_SHAPE`, `SUBCRAFT` |
| `SUBCRAFT_CUT_OUT` | Parent while a subcraft that does not come back (torpedo) is cut out | `SUBCRAFT` | `MOVEMENT_CALCULATION`, `MOVEMENT_PROCESSING`, `MODIFY_CRAFT_SHAPE` |
| `LEGACY_PROCESSING` | Compatibility for the old API only | all flags | all flags |

Consequences you can rely on:

* Readers (`STATUS_UPDATE`, `FUEL_BURN`, `SUBCRAFT_DETECTION`) run next to each other and next to subcrafts.
* A movement calculation may run next to a reader, but its **execution** (`MOVEMENT_EXECUTION`) waits until nobody reads.
  While it runs, nobody reads.
* Nothing moves or is modified while a subcraft is active (`SUBCRAFT`), but reading is allowed.
* Status updates, fuel burn, subcraft detection and subcraft cut out are not allowed during a running movement or a shape modification.

Questions like "is anything moving?" are answered by the tracker:

```java
CraftStateTracker tracker = craft.getStateTracker();
tracker.isMoving();                 // a movement is calculated or applied
tracker.isMovingOrChangingShape();  // additionally: shape is modified or a subcraft is active
tracker.isActive(CraftStateFlag.READ_CRAFT_SHAPE);
tracker.count(CraftStateFlag.SUBCRAFT);
tracker.isIdle();                   // no flag at all
tracker.getActiveHandles();         // snapshot, for debugging
```

## Using it

### Set a state right now, or not at all

```java
Optional<CraftStateHandle> handle = craft.getStateTracker().tryAcquire(CraftState.SUBCRAFT_CUT_OUT);
if (handle.isEmpty()) {
    // busy: tell the player, abort, or try again later
    return;
}
try {
    // ... work ...
} finally {
    handle.get().release();   // or close(); releasing twice is harmless
}
```

`tryAcquire` also fails if an earlier request is waiting and your state would take flags that request is blocked
by (see "Fairness").

### Wait for it without blocking a thread

```java
craft.getStateTracker()
        .reserveStateAndWait(CraftState.FUEL_BURN, 5, TimeUnit.SECONDS)   // timeout is optional
        .whenComplete((handle, throwable) -> {
            if (throwable != null) {
                // TimeoutException: stayed busy. Nothing is held then.
                return;
            }
            // You own the handle now. Release it when you are done.
        });
```

* The future completes with the handle as soon as the state can be set. Requests are served **in order**.
* After a timeout (or `cancel`) the request is removed and is never granted later.
* The future completes on **whatever thread releases the blocking state**, which may be the main thread. Do not do heavy
  work in the callback, and do not call `get()`/`join()` on a worker or the main thread.
  Hand the work to the right place, see "Where the work runs" below.

### Change the state of a running task (transition)

A movement is first calculated, then applied. Between the two, the craft must not be free for others, so the state
is **replaced atomically**:

```java
// the flags of the current state are ignored when checking what blocks the new one
if (handle.tryTransitionTo(CraftState.MOVEMENT_EXECUTION)) { ... }          // now or not at all, handle keeps its state if blocked
handle.transitionToAndWait(CraftState.MOVEMENT_EXECUTION)                    // or wait for it, completes with the same handle
        .thenAccept(h -> { ... });
```

Waiting transitions are not blocked by the fairness rule: the task is already running, it must not starve.

### Define your own state

```java
public static final CraftState MY_TASK = CraftState.of(
        "MY_TASK",
        EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE),                                // holds
        EnumSet.of(CraftStateFlag.MODIFY_CRAFT_SHAPE, CraftStateFlag.MOVEMENT_PROCESSING)  // blocked by
);
```

Ask yourself two questions: *what do I need to stay as it is?* (that goes into `blockedBy`) and *what must others not
do while I run?* (that goes into `holds`, and they list it in their `blockedBy`). Prefer reusing an existing state.
If a new kind of exclusion is needed that the flags can not express, a new flag is the right answer (that is how
`SUBCRAFT` came to be), not a state that blocks too much.

## Rules for tasks

1. **Always release.** In a `finally`, or hand the handle to whoever finishes the job (an effect, a command) and let
   that one release it. Exactly one owner. Releasing twice is harmless, never releasing locks the craft.
2. **Hold the state as long as the result depends on it.** `FuelBurnTask` holds `FUEL_BURN` until its main-thread effect
   has run, because the effect uses the positions it calculated.
3. **Never block a thread to wait for a state.** No sleep loops, no `get()`. Use `reserveStateAndWait`.
4. **Do not rely on the order of unrelated states**, only on the flags.
5. **No hard resets in code.** If something is stuck, find who does not release it. `forceReset()` exists for admins.

## Fairness

Without a rule, a steady stream of states that conflict with a waiting request could keep it waiting forever
(e.g. status updates never run because movements keep starting). Therefore:

> A state is not granted before an **earlier waiting request** if it would hold a flag that request is blocked by.

This applies to `tryAcquire` as well as to later reservations. A state that does not conflict with the waiting request
may still start. Requests are completed outside of the tracker's lock, so callbacks may use the tracker.

## The movement of a craft

Handled by `AsyncManager`, `MovementStates` and `PostMovementCommand`.

```
AsyncManager.submitTask(...)
  MovementStates.reserve(craft)        reserveStateAndWait(MOVEMENT_CALCULATION), for a subcraft also the parent state
        |                              (5 s timeout: the movement is dropped and logged)
        v
  Translation-/RotationTask runs on a thread of the old async system
        |
  AsyncManager.processAlgorithmQueue   tryEnterExecution(): MOVEMENT_CALCULATION -> MOVEMENT_EXECUTION
        |                              if a reader is active the result waits (is processed again next tick)
        v
  new hitbox is set, update commands go to the MapUpdateManager,
  PostMovementCommand is queued behind them
        |
  PostMovementCommand                  post movement step (banners), which releases the states when it is done
```

If the task fails or has nothing to apply, the states are released a tick later. `MovementStates.release()` is idempotent and
reports the end of the movement to `MovementCompletion` (see below).

## Subcrafts

Two kinds exist:

* **Short lived subcrafts** from signs (`SubcraftRotateSign`, `SubcraftMoveSign`, `SubCraftImpl`): the subcraft is
  detected, cut out of its parent, moves once, is merged back and released.
* **Subcrafts that do not come back** (torpedoes, `CruiseOnPilotSubCraft`): only cut out of the parent once.

The flow of a short lived subcraft:

1. Click on the sign: `ParentDetectionLock` sets `SUBCRAFT_DETECTION` on the parent (reading is still possible). If the
   parent is busy, the click does nothing.
2. Detection runs. If it fails for any reason, the failure callback of the `DetectionTask` finishes the operation.
3. The subcraft exists: the state of the parent **turns into** `SUBCRAFT_MOVEMENT_PARENT` (a transition, no gap) and the
   subcraft keeps it for its whole lifetime (`SubCraft#getParentStateHandle`). Only one such subcraft per parent.
4. The subcraft is cut out of the parent (`SubCraft#cutOutOfParent`), moves or rotates. The movement uses the parent
   state it already holds.
5. When the movement ended, whatever the result (`MovementCompletion`), the blocks are merged back (`mergeIntoParent`),
   the parent state is released and the subcraft is released (`CraftManager#removeCraft` does the same as a safety net).

`SubcraftSignOperation` represents one use of a sign from the click to the end. Its `finish()` releases the parent lock and the
"in use" mark of the sign. It is called when detection fails, the action is aborted or the subcraft was released.
Safety nets (100 ticks for the parent lock, 600 ticks for the whole operation, 200 ticks for the release after the movement)
only exist so nothing stays locked if something ends in a way nobody noticed. They log a warning.

A torpedo is cut out under `SUBCRAFT_CUT_OUT`, held only while the hitbox of the parent is changed.
Subcrafts that act independently of their parent override `SubCraft#locksParentWhileMoving()` with `false`.

A subcraft may only consist of blocks of its parent (checked in `DetectionTask`).

## Resetting a stuck craft

If a craft stays "busy", something did not release its state. There are two ways out:

* **Command** `/resetcraftstate` (permission `movecraft.resetcraftstate`, selectors need `movecraft.resetcraftstate.others`),
  which calls `CraftStateTracker#forceReset()`. It is logged with the state of the craft before the reset.
* **Watchdog** in `PilotedCraft#shouldAutoRelease`: a cruising craft which is still moving long after its last cruise update
  is reset automatically (and logged as a warning).

`forceReset()` releases all handles, so the owners' later `release()` calls are harmless. A movement that is in progress
may be left unfinished. After a reset, find out why the state was not released: the log line contains the tracker state,
for example `CraftStateTracker[READ_CRAFT_SHAPE=0, MODIFY_CRAFT_SHAPE=0, MOVEMENT_CALCULATION=1, ...]`.

## Migrating from the old API

| Old | New |
|---|---|
| `craft.isNotProcessing()` | `craft.getStateTracker().isIdle()`, or better `isMoving()` / `isMovingOrChangingShape()` / `isActive(flag)` for what you actually care about |
| `craft.setProcessing(true)` | `tracker.tryAcquire(state)` or `reserveStateAndWait(state)`; keep the handle |
| `craft.setProcessing(false)` | `handle.release()` |
| `craft.setProcessingActiveIfAvailable()` | `tracker.tryAcquire(state)` |
| a sleep loop around `setProcessingActiveIfAvailable()` | `tracker.reserveStateAndWait(state, timeout, unit)` |
| `setProcessing(false)` as "make it work again" | `tracker.forceReset()` / `/resetcraftstate` |

The old methods are `@Deprecated(forRemoval = true)` and implemented through a bridge state (`LEGACY_PROCESSING`) which
holds and blocks everything.

## Where the work runs

A state is only the bookkeeping. Which thread does the work is a different question, see [worker-concept.md](worker-concept.md).
In short: reserve the state in `get()` of a task, and when it is granted, start the work as a **new task of the WorldManager**
which owns the handle. `FuelBurnTask` is the example (reservation in `get()`, calculation in `Calculation#get()`).
