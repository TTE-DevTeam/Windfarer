package net.countercraft.movecraft.craft.state;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static net.countercraft.movecraft.craft.state.CraftStateFlag.MODIFY_CRAFT_SHAPE;
import static net.countercraft.movecraft.craft.state.CraftStateFlag.MOVEMENT_CALCULATION;
import static net.countercraft.movecraft.craft.state.CraftStateFlag.MOVEMENT_PROCESSING;
import static net.countercraft.movecraft.craft.state.CraftStateFlag.READ_CRAFT_SHAPE;
import static net.countercraft.movecraft.craft.state.CraftStateFlag.SUBCRAFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CraftStateTrackerTest {

    private static final long WAIT_SECONDS = 5;

    // ------------------------------------------------------------------------------------------------ helpers

    private static CraftStateHandle acquire(CraftStateTracker tracker, CraftState state) {
        Optional<CraftStateHandle> handle = tracker.tryAcquire(state);
        assertTrue(handle.isPresent(), "expected " + state.name() + " to be acquirable, tracker: " + tracker);
        return handle.get();
    }

    private static void assertBlocked(CraftStateTracker tracker, CraftState state) {
        assertTrue(tracker.tryAcquire(state).isEmpty(), "expected " + state.name() + " to be blocked, tracker: " + tracker);
    }

    private static CraftState state(String name, EnumSet<CraftStateFlag> holds, EnumSet<CraftStateFlag> blockedBy) {
        return CraftState.of(name, holds, blockedBy);
    }

    private static final EnumSet<CraftStateFlag> NONE = EnumSet.noneOf(CraftStateFlag.class);

    /** A plain reader, which is allowed next to a movement calculation but not next to its execution */
    private static final CraftState READER = state("READER", EnumSet.of(READ_CRAFT_SHAPE), EnumSet.of(MOVEMENT_PROCESSING));

    // ------------------------------------------------------------------------------------------------ basics

    @Test
    public void newTrackerIsIdle() {
        CraftStateTracker tracker = new CraftStateTracker();
        assertTrue(tracker.isIdle());
        assertFalse(tracker.isMoving());
        assertFalse(tracker.isMovingOrChangingShape());
        assertTrue(tracker.getActiveHandles().isEmpty());
        for (CraftStateFlag flag : CraftStateFlag.values()) {
            assertFalse(tracker.isActive(flag));
            assertEquals(0, tracker.count(flag));
        }
    }

    @Test
    public void acquireHoldsTheFlagsOfTheStateAndReleaseGivesThemBack() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle handle = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        assertFalse(tracker.isIdle());
        assertTrue(tracker.isActive(MOVEMENT_CALCULATION));
        assertTrue(tracker.isActive(READ_CRAFT_SHAPE));
        assertFalse(tracker.isActive(MOVEMENT_PROCESSING));
        assertTrue(tracker.isMoving());
        assertEquals(CraftState.MOVEMENT_CALCULATION, handle.getState());
        assertFalse(handle.isReleased());

        assertTrue(handle.release());
        assertTrue(handle.isReleased());
        assertTrue(tracker.isIdle());
        assertFalse(tracker.isMoving());
    }

    @Test
    public void releasingTwiceDoesNotCorruptTheCounters() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle first = acquire(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        CraftStateHandle second = acquire(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        assertEquals(2, tracker.count(MODIFY_CRAFT_SHAPE));

        assertTrue(first.release());
        assertFalse(first.release());
        first.close();
        assertEquals(1, tracker.count(MODIFY_CRAFT_SHAPE));

        second.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void severalHandlesOfTheSameStateAreAllowedIfItDoesNotBlockItself() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle a = acquire(tracker, CraftState.SUBCRAFT_CUT_OUT);
        CraftStateHandle b = acquire(tracker, CraftState.SUBCRAFT_CUT_OUT);
        CraftStateHandle c = acquire(tracker, CraftState.SUBCRAFT_CUT_OUT);
        assertEquals(3, tracker.count(SUBCRAFT));
        assertEquals(3, tracker.getActiveHandles().size());

        a.close();
        b.close();
        assertTrue(tracker.isActive(SUBCRAFT));
        c.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void aStateWhichBlocksItselfIsExclusive() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle first = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(tracker, CraftState.MOVEMENT_CALCULATION);
        first.close();
        acquire(tracker, CraftState.MOVEMENT_CALCULATION).close();
    }

    @Test
    public void customStatesAreSupported() {
        CraftState reader = state("READER", EnumSet.of(READ_CRAFT_SHAPE), NONE);
        CraftState writer = state("WRITER", EnumSet.of(MODIFY_CRAFT_SHAPE), EnumSet.of(READ_CRAFT_SHAPE, MODIFY_CRAFT_SHAPE));
        assertEquals("WRITER", writer.name());
        assertEquals(EnumSet.of(MODIFY_CRAFT_SHAPE), writer.holds());
        assertEquals(EnumSet.of(READ_CRAFT_SHAPE, MODIFY_CRAFT_SHAPE), writer.blockedBy());

        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle r1 = acquire(tracker, reader);
        CraftStateHandle r2 = acquire(tracker, reader);
        assertBlocked(tracker, writer);
        r1.close();
        assertBlocked(tracker, writer);
        r2.close();
        CraftStateHandle w = acquire(tracker, writer);
        assertBlocked(tracker, writer);
        w.close();
    }

    // ------------------------------------------------------------------------------------------------ the states of Windfarer

    @Test
    public void movementCalculationBlocksOtherMovementsAndShapeChangesButNotReaders() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle calculation = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        assertBlocked(tracker, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        assertBlocked(tracker, CraftState.SUBCRAFT_MOVEMENT_PARENT);
        // Reading is fine, it only delays the execution of the movement
        // (status updates themselves are blocked by the calculation flag, see below)
        calculation.close();
    }

    @Test
    public void statusUpdateIsBlockedByEverythingButReadingAndSubcrafts() {
        CraftStateTracker tracker = new CraftStateTracker();
        assertTrue(CraftState.STATUS_UPDATE.blockedBy().contains(MODIFY_CRAFT_SHAPE));
        assertTrue(CraftState.STATUS_UPDATE.blockedBy().contains(MOVEMENT_CALCULATION));
        assertTrue(CraftState.STATUS_UPDATE.blockedBy().contains(MOVEMENT_PROCESSING));
        assertFalse(CraftState.STATUS_UPDATE.blockedBy().contains(READ_CRAFT_SHAPE));
        assertFalse(CraftState.STATUS_UPDATE.blockedBy().contains(SUBCRAFT));

        CraftStateHandle modification = acquire(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        assertBlocked(tracker, CraftState.STATUS_UPDATE);
        modification.close();

        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(tracker, CraftState.STATUS_UPDATE);
        movement.close();

        // Readers do not block each other
        acquire(tracker, CraftState.STATUS_UPDATE);
        acquire(tracker, CraftState.STATUS_UPDATE);
        acquire(tracker, CraftState.FUEL_BURN);
    }

    @Test
    public void executionIsBlockedByEverythingAndBlocksEverything() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertTrue(movement.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));

        assertTrue(tracker.isActive(MOVEMENT_CALCULATION));
        assertTrue(tracker.isActive(MOVEMENT_PROCESSING));
        assertTrue(tracker.isActive(MODIFY_CRAFT_SHAPE));
        assertFalse(tracker.isActive(READ_CRAFT_SHAPE));
        assertBlocked(tracker, CraftState.STATUS_UPDATE);
        assertBlocked(tracker, CraftState.FUEL_BURN);
        assertBlocked(tracker, CraftState.SUBCRAFT_CUT_OUT);
        assertBlocked(tracker, CraftState.MOVEMENT_CALCULATION);

        movement.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void executionWaitsForReaders() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        // Calculation and reading may run next to each other
        CraftStateHandle reader = acquire(tracker, READER);

        assertFalse(movement.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));
        // Nothing changed
        assertEquals(CraftState.MOVEMENT_CALCULATION, movement.getState());
        assertFalse(tracker.isActive(MOVEMENT_PROCESSING));

        reader.close();
        assertTrue(movement.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));
    }

    @Test
    public void subcraftStatesAllowReadingOperationsButNoMovement() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle cutOut = acquire(tracker, CraftState.SUBCRAFT_CUT_OUT);

        acquire(tracker, CraftState.STATUS_UPDATE);
        acquire(tracker, CraftState.FUEL_BURN);
        acquire(tracker, CraftState.SUBCRAFT_DETECTION);
        // Another torpedo may be cut out at the same time
        acquire(tracker, CraftState.SUBCRAFT_CUT_OUT);

        assertBlocked(tracker, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        assertBlocked(tracker, CraftState.SUBCRAFT_MOVEMENT_PARENT);
        assertTrue(tracker.isMovingOrChangingShape());
        assertFalse(tracker.isMoving());
        cutOut.close();
    }

    @Test
    public void subcraftLifetimeStateIsSequentialAndReplacesTheDetectionWithoutAGap() {
        CraftStateTracker parent = new CraftStateTracker();
        // The parent is read while the subcraft is detected, which is no problem
        acquire(parent, CraftState.STATUS_UPDATE);
        acquire(parent, CraftState.FUEL_BURN);

        CraftStateHandle detection = acquire(parent, CraftState.SUBCRAFT_DETECTION);
        assertBlocked(parent, CraftState.MOVEMENT_CALCULATION);

        assertTrue(detection.tryTransitionTo(CraftState.SUBCRAFT_MOVEMENT_PARENT));
        assertTrue(parent.isActive(SUBCRAFT));
        assertEquals(CraftState.SUBCRAFT_MOVEMENT_PARENT, detection.getState());
        // Only one subcraft per parent at a time
        assertBlocked(parent, CraftState.SUBCRAFT_MOVEMENT_PARENT);
        assertBlocked(parent, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(parent, CraftState.CRAFT_SHAPE_MODIFICATION);

        detection.close();
        // Only the two readers are left
        assertEquals(2, parent.count(READ_CRAFT_SHAPE));
        assertFalse(parent.isActive(SUBCRAFT));
    }

    @Test
    public void aSecondDetectionCanNotTurnIntoTheLifetimeStateWhileAnotherSubcraftIsActive() {
        CraftStateTracker parent = new CraftStateTracker();
        CraftStateHandle first = acquire(parent, CraftState.SUBCRAFT_DETECTION);
        CraftStateHandle second = acquire(parent, CraftState.SUBCRAFT_DETECTION);

        // Both hold the subcraft flag, so neither can turn into the exclusive lifetime state
        assertFalse(first.tryTransitionTo(CraftState.SUBCRAFT_MOVEMENT_PARENT));
        second.close();
        assertTrue(first.tryTransitionTo(CraftState.SUBCRAFT_MOVEMENT_PARENT));
    }

    @Test
    public void detectionIsBlockedByRunningMovements() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(tracker, CraftState.SUBCRAFT_DETECTION);
        assertBlocked(tracker, CraftState.SUBCRAFT_CUT_OUT);
        movement.close();
        acquire(tracker, CraftState.SUBCRAFT_DETECTION);
    }

    @Test
    public void fuelBurnOnlyReadsButDoesNotRunDuringMovements() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle fuel = acquire(tracker, CraftState.FUEL_BURN);
        assertTrue(tracker.isActive(READ_CRAFT_SHAPE));
        assertFalse(tracker.isMoving());
        fuel.close();

        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertBlocked(tracker, CraftState.FUEL_BURN);
        movement.close();
    }

    // ------------------------------------------------------------------------------------------------ transitions

    @Test
    public void aFailedTransitionLeavesTheStateAsItWas() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        CraftStateHandle reader = acquire(tracker, READER);

        assertFalse(movement.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));
        assertEquals(CraftState.MOVEMENT_CALCULATION, movement.getState());
        assertTrue(tracker.isActive(MOVEMENT_CALCULATION));
        assertFalse(tracker.isActive(MOVEMENT_PROCESSING));
        assertFalse(tracker.isActive(MODIFY_CRAFT_SHAPE));
        reader.close();
    }

    @Test
    public void transitionIgnoresTheFlagsOfTheOwnCurrentState() {
        CraftStateTracker tracker = new CraftStateTracker();
        // Calculation holds the flag the execution is blocked by: only because it is the own flag it works
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertTrue(movement.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));
        assertEquals(1, tracker.count(MOVEMENT_CALCULATION));
        assertEquals(1, tracker.count(MOVEMENT_PROCESSING));
        assertEquals(1, tracker.count(MODIFY_CRAFT_SHAPE));
        assertEquals(0, tracker.count(READ_CRAFT_SHAPE));
    }

    @Test
    public void aReleasedHandleCanNotTransition() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle handle = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        handle.close();
        assertFalse(handle.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));
        assertTrue(tracker.isIdle());
    }

    @Test
    public void transitionAndWaitCompletesOnceTheBlockingReaderIsGone() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        CraftStateHandle reader = acquire(tracker, READER);

        CompletableFuture<CraftStateHandle> future = movement.transitionToAndWait(CraftState.MOVEMENT_EXECUTION);
        assertFalse(future.isDone());
        assertEquals(CraftState.MOVEMENT_CALCULATION, movement.getState());

        reader.close();
        assertSame(movement, future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(CraftState.MOVEMENT_EXECUTION, movement.getState());
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }

    @Test
    public void transitionAndWaitFailsIfTheHandleWasReleasedMeanwhile() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        CraftStateHandle reader = acquire(tracker, READER);

        CompletableFuture<CraftStateHandle> future = movement.transitionToAndWait(CraftState.MOVEMENT_EXECUTION);
        movement.close();

        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        reader.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void transitionAndWaitWithTimeoutKeepsTheOldState() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        CraftStateHandle reader = acquire(tracker, READER);

        CompletableFuture<CraftStateHandle> future = movement.transitionToAndWait(CraftState.MOVEMENT_EXECUTION, 50, TimeUnit.MILLISECONDS);
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, exception.getCause());
        assertEquals(CraftState.MOVEMENT_CALCULATION, movement.getState());

        // The expired transition must not happen later
        reader.close();
        assertEquals(CraftState.MOVEMENT_CALCULATION, movement.getState());
        assertFalse(tracker.isActive(MOVEMENT_PROCESSING));
    }

    // ------------------------------------------------------------------------------------------------ reservations

    @Test
    public void reservationIsGrantedAtOnceIfTheCraftIsFree() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CompletableFuture<CraftStateHandle> future = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        assertTrue(future.isDone());
        CraftStateHandle handle = future.get();
        assertTrue(tracker.isActive(MOVEMENT_CALCULATION));
        handle.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void reservationWaitsUntilTheBlockingStateIsReleased() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle blocker = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        CompletableFuture<CraftStateHandle> future = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        assertFalse(future.isDone());

        blocker.close();
        assertTrue(future.isDone());
        CraftStateHandle granted = future.get();
        assertTrue(tracker.isActive(MOVEMENT_CALCULATION));
        granted.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void reservationsAreServedInOrder() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle blocker = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        CompletableFuture<CraftStateHandle> first = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        CompletableFuture<CraftStateHandle> second = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        CompletableFuture<CraftStateHandle> third = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);

        blocker.close();
        assertTrue(first.isDone());
        assertFalse(second.isDone());
        assertFalse(third.isDone());

        first.get().close();
        assertTrue(second.isDone());
        assertFalse(third.isDone());

        second.get().close();
        assertTrue(third.isDone());
        third.get().close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void aWaitingReservationCanNotBeOvertakenByStatesWhichWouldDelayIt() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        // A status update has to wait for a shape modification ...
        CraftStateHandle modification = acquire(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        CompletableFuture<CraftStateHandle> statusUpdate = tracker.reserveStateAndWait(CraftState.STATUS_UPDATE);
        assertFalse(statusUpdate.isDone());

        // ... a state which is free to start (nothing blocks it), but takes what the status update is blocked by,
        // must not overtake it. Otherwise a steady stream of such states would starve the status update.
        CraftState calculationLike = state("CALCULATION_LIKE", EnumSet.of(MOVEMENT_CALCULATION), NONE);
        assertBlocked(tracker, calculationLike);
        CompletableFuture<CraftStateHandle> reserved = tracker.reserveStateAndWait(calculationLike);
        assertFalse(reserved.isDone());

        modification.close();
        assertTrue(statusUpdate.isDone());
        // Now nothing is waiting in front of it anymore
        assertTrue(reserved.isDone());
        statusUpdate.get().close();
        reserved.get().close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void aReservationDoesNotBlockStatesWhichDoNotConflictWithIt() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle modification = acquire(tracker, CraftState.CRAFT_SHAPE_MODIFICATION);
        CompletableFuture<CraftStateHandle> statusUpdate = tracker.reserveStateAndWait(CraftState.STATUS_UPDATE);
        assertFalse(statusUpdate.isDone());

        // A reader takes nothing the waiting status update is blocked by, so it may start
        acquire(tracker, READER).close();

        modification.close();
        statusUpdate.get().close();
    }

    @Test
    public void reservationWithTimeoutFailsAndLeavesNothingBehind() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle blocker = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        CompletableFuture<CraftStateHandle> future = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION, 50, TimeUnit.MILLISECONDS);
        ExecutionException exception = assertThrows(ExecutionException.class, () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, exception.getCause());

        // The expired reservation must not be granted later
        blocker.close();
        assertTrue(tracker.isIdle());
        assertTrue(tracker.getActiveHandles().isEmpty());
        acquire(tracker, CraftState.MOVEMENT_CALCULATION).close();
    }

    @Test
    public void cancellingAReservationRemovesItAndFreesTheOnesBehindIt() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle blocker = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        CompletableFuture<CraftStateHandle> first = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        CompletableFuture<CraftStateHandle> second = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        first.cancel(false);

        blocker.close();
        assertTrue(second.isDone());
        assertFalse(second.isCompletedExceptionally());
        second.get().close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void completionCallbacksMayUseTheTrackerWithoutDeadlock() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle blocker = acquire(tracker, CraftState.MOVEMENT_CALCULATION);

        AtomicInteger activeInCallback = new AtomicInteger(-1);
        CompletableFuture<Void> future = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION)
                .thenAccept(handle -> {
                    // Futures are completed outside of the lock, so this must work
                    activeInCallback.set(tracker.count(MOVEMENT_CALCULATION));
                    assertTrue(tracker.getActiveHandles().contains(handle));
                    handle.close();
                });
        blocker.close();
        future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, activeInCallback.get());
        assertTrue(tracker.isIdle());
    }

    // ------------------------------------------------------------------------------------------------ reset

    @Test
    public void forceResetReleasesEverythingAndMakesOldHandlesHarmless() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        CraftStateHandle cutOut = acquire(tracker, READER);
        tracker.setLegacyProcessing(true);

        assertEquals(3, tracker.forceReset());
        assertTrue(tracker.isIdle());
        assertTrue(movement.isReleased());
        assertTrue(cutOut.isReleased());

        // Somebody who still holds an old handle releases it later and a new state was set meanwhile
        CraftStateHandle fresh = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        assertFalse(movement.release());
        assertFalse(cutOut.release());
        assertEquals(1, tracker.count(MOVEMENT_CALCULATION));
        assertFalse(movement.tryTransitionTo(CraftState.MOVEMENT_EXECUTION));
        fresh.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void forceResetServesWaitingReservations() throws Exception {
        CraftStateTracker tracker = new CraftStateTracker();
        acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        CompletableFuture<CraftStateHandle> waiting = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION);
        assertFalse(waiting.isDone());

        assertEquals(1, tracker.forceReset());
        assertTrue(waiting.isDone());
        assertTrue(tracker.isActive(MOVEMENT_CALCULATION));
        waiting.get().close();
        assertTrue(tracker.isIdle());
    }

    @Test
    public void forceResetOnAnIdleTrackerDoesNothing() {
        CraftStateTracker tracker = new CraftStateTracker();
        assertEquals(0, tracker.forceReset());
        assertTrue(tracker.isIdle());
    }

    // ------------------------------------------------------------------------------------------------ legacy bridge

    @Test
    @SuppressWarnings("removal")
    public void legacyProcessingBlocksAndHoldsEverything() {
        CraftStateTracker tracker = new CraftStateTracker();
        assertTrue(tracker.tryAcquireLegacyProcessing());
        assertFalse(tracker.isIdle());
        assertFalse(tracker.tryAcquireLegacyProcessing());
        for (CraftStateFlag flag : CraftStateFlag.values()) {
            assertTrue(tracker.isActive(flag));
        }
        assertBlocked(tracker, CraftState.STATUS_UPDATE);

        tracker.setLegacyProcessing(false);
        assertTrue(tracker.isIdle());
        // releasing without a state does nothing
        tracker.setLegacyProcessing(false);
        assertTrue(tracker.isIdle());
    }

    @Test
    @SuppressWarnings("removal")
    public void legacyProcessingIsForcedAndOnlyReleasesItself() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle movement = acquire(tracker, CraftState.MOVEMENT_CALCULATION);
        // The old flag was set unconditionally
        tracker.setLegacyProcessing(true);
        tracker.setLegacyProcessing(true);
        assertEquals(1, tracker.count(MOVEMENT_PROCESSING));
        assertEquals(2, tracker.count(MOVEMENT_CALCULATION));

        tracker.setLegacyProcessing(false);
        // The movement is not touched
        assertEquals(1, tracker.count(MOVEMENT_CALCULATION));
        assertFalse(movement.isReleased());
        movement.close();
        assertTrue(tracker.isIdle());
    }

    @Test
    @SuppressWarnings("removal")
    public void legacyProcessingCanNotBeSetWhileSomethingIsActive() {
        CraftStateTracker tracker = new CraftStateTracker();
        CraftStateHandle reader = acquire(tracker, CraftState.STATUS_UPDATE);
        assertFalse(tracker.tryAcquireLegacyProcessing());
        reader.close();
        assertTrue(tracker.tryAcquireLegacyProcessing());
    }

    // ------------------------------------------------------------------------------------------------ concurrency

    @Test
    public void exclusiveStateNeverRunsTwiceAtTheSameTime() throws Exception {
        final int threads = 8;
        final int rounds = 300;
        CraftStateTracker tracker = new CraftStateTracker();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        CraftStateHandle handle = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION).get(WAIT_SECONDS, TimeUnit.SECONDS);
                        int now = inside.incrementAndGet();
                        maxInside.accumulateAndGet(now, Math::max);
                        inside.decrementAndGet();
                        handle.close();
                        completed.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(threads * rounds, completed.get());
        assertEquals(1, maxInside.get());
        assertTrue(tracker.isIdle());
        assertTrue(tracker.getActiveHandles().isEmpty());
    }

    @Test
    public void parallelStatesAreCountedCorrectlyUnderContention() throws Exception {
        final int threads = 8;
        final int rounds = 2000;
        CraftStateTracker tracker = new CraftStateTracker();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        CraftStateHandle handle = tracker.tryAcquire(CraftState.SUBCRAFT_CUT_OUT).orElseThrow();
                        assertTrue(tracker.isActive(SUBCRAFT));
                        handle.close();
                        handle.close();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(tracker.isIdle());
        assertEquals(0, tracker.count(SUBCRAFT));
    }

    @Test
    public void movementAndReadersNeverOverlapDuringExecution() throws Exception {
        final int rounds = 300;
        CraftStateTracker tracker = new CraftStateTracker();
        AtomicInteger readers = new AtomicInteger();
        AtomicInteger violations = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> mover = executor.submit(() -> {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    CraftStateHandle handle = tracker.reserveStateAndWait(CraftState.MOVEMENT_CALCULATION).get(WAIT_SECONDS, TimeUnit.SECONDS);
                    handle.transitionToAndWait(CraftState.MOVEMENT_EXECUTION).get(WAIT_SECONDS, TimeUnit.SECONDS);
                    // Nobody may read while the movement is applied
                    if (readers.get() != 0) {
                        violations.incrementAndGet();
                    }
                    handle.close();
                }
                return null;
            });
            List<Future<?>> readerFutures = new java.util.ArrayList<>();
            for (int t = 0; t < 3; t++) {
                readerFutures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        CraftStateHandle handle = tracker.reserveStateAndWait(READER).get(WAIT_SECONDS, TimeUnit.SECONDS);
                        readers.incrementAndGet();
                        if (tracker.isActive(MOVEMENT_PROCESSING)) {
                            violations.incrementAndGet();
                        }
                        readers.decrementAndGet();
                        handle.close();
                    }
                    return null;
                }));
            }
            start.countDown();
            mover.get(60, TimeUnit.SECONDS);
            for (Future<?> future : readerFutures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(0, violations.get());
        assertTrue(tracker.isIdle());
        assertNotNull(tracker.toString());
    }
}
