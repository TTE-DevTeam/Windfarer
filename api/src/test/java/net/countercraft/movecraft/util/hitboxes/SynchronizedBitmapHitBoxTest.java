package net.countercraft.movecraft.util.hitboxes;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.exception.EmptyHitBoxException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SynchronizedBitmapHitBoxTest {

    // ------------------------------------------------------------------------------------------------ helpers

    private static MovecraftLocation loc(int x, int y, int z) {
        return new MovecraftLocation(x, y, z);
    }

    private static SynchronizedBitmapHitBox boxOf(MovecraftLocation... locations) {
        return new SynchronizedBitmapHitBox(Arrays.asList(locations));
    }

    private static Set<MovecraftLocation> contentOf(HitBox hitBox) {
        return new HashSet<>(hitBox.asSet());
    }

    private static MovecraftLocation randomLocation(Random random, int xzRange, int yRange) {
        return loc(
                random.nextInt(2 * xzRange + 1) - xzRange,
                random.nextInt(2 * yRange + 1) - yRange,
                random.nextInt(2 * xzRange + 1) - xzRange
        );
    }

    private static List<MovecraftLocation> randomLocations(Random random, int count, int xzRange, int yRange) {
        final List<MovecraftLocation> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(randomLocation(random, xzRange, yRange));
        }
        return out;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Compares everything the hitbox exposes with a plain {@link Set} that is used as the reference model.
     */
    private static void assertMatchesModel(SynchronizedBitmapHitBox box, Set<MovecraftLocation> model) {
        assertEquals(model.size(), box.size());
        assertEquals(model.isEmpty(), box.isEmpty());
        assertEquals(model, contentOf(box));
        for (MovecraftLocation location : model) {
            assertTrue(box.contains(location));
            assertTrue(box.contains(location.getX(), location.getY(), location.getZ()));
        }

        if (model.isEmpty()) {
            assertThrows(EmptyHitBoxException.class, box::getBounds);
            assertThrows(EmptyHitBoxException.class, box::getMinX);
            assertThrows(EmptyHitBoxException.class, box::getMaxZ);
            assertThrows(EmptyHitBoxException.class, () -> box.getMinYAt(0, 0));
            assertEquals(0, box.getXLength());
            assertFalse(box.inBounds(0, 0, 0));
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        final Map<Long, Integer> minYAt = new HashMap<>();
        for (MovecraftLocation l : model) {
            minX = Math.min(minX, l.getX());
            minY = Math.min(minY, l.getY());
            minZ = Math.min(minZ, l.getZ());
            maxX = Math.max(maxX, l.getX());
            maxY = Math.max(maxY, l.getY());
            maxZ = Math.max(maxZ, l.getZ());
            minYAt.merge(column(l.getX(), l.getZ()), l.getY(), Math::min);
        }
        assertEquals(new SynchronizedBitmapHitBox.Bounds(minX, minY, minZ, maxX, maxY, maxZ), box.getBounds());
        assertEquals(minX, box.getMinX());
        assertEquals(minY, box.getMinY());
        assertEquals(minZ, box.getMinZ());
        assertEquals(maxX, box.getMaxX());
        assertEquals(maxY, box.getMaxY());
        assertEquals(maxZ, box.getMaxZ());
        assertEquals(maxX - minX + 1, box.getXLength());
        assertEquals(maxY - minY + 1, box.getYLength());
        assertEquals(maxZ - minZ + 1, box.getZLength());
        for (Map.Entry<Long, Integer> entry : minYAt.entrySet()) {
            final long key = entry.getKey();
            assertEquals((int) entry.getValue(), box.getMinYAt((int) (key >> 32), (int) key));
        }
        // No block in that column
        assertEquals(-1, box.getMinYAt(1000, 1000));
    }

    private static void runConcurrently(List<Callable<Void>> tasks) throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                // A timeout here means a deadlock, an ExecutionException wraps the failed assertion/exception
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------------------------------------ single threaded

    @Test
    public void emptyHitBox() {
        assertMatchesModel(new SynchronizedBitmapHitBox(), new HashSet<>());
    }

    @Test
    public void addReportsWhetherTheBlockWasNew() {
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();
        assertTrue(box.add(loc(1, 2, 3)));
        assertFalse(box.add(loc(1, 2, 3)));
        assertEquals(1, box.size());
        assertTrue(box.contains(loc(1, 2, 3)));
        assertFalse(box.contains(loc(3, 2, 1)));
    }

    @Test
    public void boundsWithNegativeCoordinates() {
        final SynchronizedBitmapHitBox box = boxOf(loc(-5, -3, -7), loc(10, 20, 30));
        assertEquals(new SynchronizedBitmapHitBox.Bounds(-5, -3, -7, 10, 20, 30), box.getBounds());
        assertEquals(box.boundingHitBox().getMidPoint(), box.getMidPoint());
        assertTrue(box.inBounds(0, 0, 0));
        assertFalse(box.inBounds(11, 0, 0));
        assertEquals(16, box.getXLength());
        assertEquals(24, box.getYLength());
        assertEquals(38, box.getZLength());
    }

    @Test
    public void midPoint() {
        assertEquals(loc(5, 5, 5), boxOf(loc(0, 0, 0), loc(10, 10, 10)).getMidPoint());
    }

    @Test
    public void minYAtWorksForNegativeZ() {
        // The key of the column used to be corrupted by a negative z
        final SynchronizedBitmapHitBox box = boxOf(loc(0, 5, -1), loc(1, 7, -1), loc(0, 9, -1), loc(-3, 2, -8));
        assertEquals(5, box.getMinYAt(0, -1));
        assertEquals(7, box.getMinYAt(1, -1));
        assertEquals(2, box.getMinYAt(-3, -8));
        assertEquals(-1, box.getMinYAt(2, -1));
    }

    @Test
    public void boundsShrinkAfterRemoval() {
        final SynchronizedBitmapHitBox box = boxOf(loc(0, 0, 0), loc(5, 5, 5), loc(10, 10, 10));
        assertTrue(box.remove(loc(10, 10, 10)));
        assertEquals(new SynchronizedBitmapHitBox.Bounds(0, 0, 0, 5, 5, 5), box.getBounds());
        assertTrue(box.remove(loc(0, 0, 0)));
        assertEquals(new SynchronizedBitmapHitBox.Bounds(5, 5, 5, 5, 5, 5), box.getBounds());
        assertFalse(box.remove(loc(0, 0, 0)));
    }

    @Test
    public void localMinYIsRecalculatedAfterRemoval() {
        final SynchronizedBitmapHitBox box = boxOf(loc(0, 1, 0), loc(0, 4, 0), loc(9, 9, 9));
        assertEquals(1, box.getMinYAt(0, 0));
        box.remove(loc(0, 1, 0));
        assertEquals(4, box.getMinYAt(0, 0));
        box.remove(loc(0, 4, 0));
        assertEquals(-1, box.getMinYAt(0, 0));
    }

    @Test
    public void removingTheLastBlockEmptiesTheHitBoxAndItCanBeReused() {
        final SynchronizedBitmapHitBox box = boxOf(loc(100, 100, 100));
        assertTrue(box.remove(loc(100, 100, 100)));
        assertMatchesModel(box, new HashSet<>());
        box.add(loc(-4, -4, -4));
        assertEquals(new SynchronizedBitmapHitBox.Bounds(-4, -4, -4, -4, -4, -4), box.getBounds());
    }

    @Test
    public void bulkOperationsReportChanges() {
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();
        final List<MovecraftLocation> locations = List.of(loc(0, 0, 0), loc(1, 1, 1));
        assertTrue(box.addAll(locations));
        assertFalse(box.addAll(locations));
        assertTrue(box.containsAll(locations));
        assertTrue(box.removeAll(locations));
        assertFalse(box.removeAll(locations));
        assertFalse(box.containsAll(locations));
        assertTrue(box.isEmpty());
    }

    @Test
    public void bulkOperationsWithHitBoxes() {
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();
        final SolidHitBox cube = new SolidHitBox(loc(0, 0, 0), loc(1, 1, 1));
        assertTrue(box.addAll(cube));
        assertFalse(box.addAll(cube));
        assertEquals(8, box.size());
        assertEquals(new SynchronizedBitmapHitBox.Bounds(0, 0, 0, 1, 1, 1), box.getBounds());
        assertTrue(box.removeAll(boxOf(loc(1, 1, 1), loc(5, 5, 5))));
        assertEquals(7, box.size());
        assertEquals(new SynchronizedBitmapHitBox.Bounds(0, 0, 0, 1, 1, 1), box.getBounds());
    }

    @Test
    public void clearResetsEverything() {
        final SynchronizedBitmapHitBox box = boxOf(loc(1, 2, 3), loc(4, 5, 6));
        box.getBounds();
        box.clear();
        assertMatchesModel(box, new HashSet<>());
        box.add(loc(-1, -1, -1));
        assertEquals(new SynchronizedBitmapHitBox.Bounds(-1, -1, -1, -1, -1, -1), box.getBounds());
    }

    @Test
    public void boundingHitBox() {
        final SolidHitBox bounding = boxOf(loc(-2, 0, 3), loc(4, 8, 9)).boundingHitBox();
        assertEquals(-2, bounding.getMinX());
        assertEquals(0, bounding.getMinY());
        assertEquals(3, bounding.getMinZ());
        assertEquals(4, bounding.getMaxX());
        assertEquals(8, bounding.getMaxY());
        assertEquals(9, bounding.getMaxZ());
    }

    @Test
    public void setOperations() {
        final SynchronizedBitmapHitBox a = boxOf(loc(0, 0, 0), loc(1, 0, 0), loc(2, 0, 0));
        final SynchronizedBitmapHitBox b = boxOf(loc(1, 0, 0), loc(2, 0, 0), loc(3, 0, 0));

        assertEquals(Set.of(loc(0, 0, 0), loc(1, 0, 0), loc(2, 0, 0), loc(3, 0, 0)), contentOf(a.union(b)));
        assertEquals(Set.of(loc(1, 0, 0), loc(2, 0, 0)), contentOf(a.intersection(b)));
        assertEquals(Set.of(loc(0, 0, 0)), contentOf(a.difference(b)));
        assertEquals(Set.of(loc(0, 0, 0), loc(3, 0, 0)), contentOf(a.symmetricDifference(b)));

        // The operands are untouched
        assertEquals(3, a.size());
        assertEquals(3, b.size());
    }

    @Test
    public void setOperationResultsAreIndependentAndConsistent() {
        final SynchronizedBitmapHitBox a = boxOf(loc(0, 0, 0), loc(1, 0, 0));
        final SynchronizedBitmapHitBox union = a.union(boxOf(loc(5, 5, 5)));
        union.add(loc(9, 9, 9));
        union.remove(loc(0, 0, 0));
        assertEquals(Set.of(loc(0, 0, 0), loc(1, 0, 0)), contentOf(a));
        assertMatchesModel(union, Set.of(loc(1, 0, 0), loc(5, 5, 5), loc(9, 9, 9)));
    }

    @Test
    public void setOperationsWithOtherHitBoxTypes() {
        final SynchronizedBitmapHitBox a = boxOf(loc(0, 0, 0), loc(5, 5, 5));
        final SolidHitBox cube = new SolidHitBox(loc(0, 0, 0), loc(1, 1, 1));
        assertEquals(Set.of(loc(0, 0, 0)), contentOf(a.intersection(cube)));
        assertEquals(Set.of(loc(5, 5, 5)), contentOf(a.difference(cube)));
        assertEquals(9, a.union(cube).size());
    }

    @Test(timeout = 30_000)
    public void operationsWithItselfAreSafe() {
        final SynchronizedBitmapHitBox box = boxOf(loc(0, 0, 0), loc(1, 2, 3));
        assertFalse(box.addAll(box));
        assertTrue(box.containsAll(box.asSet()));
        assertEquals(2, box.size());
        assertEquals(contentOf(box), contentOf(box.union(box)));
        assertTrue(box.difference(box).isEmpty());
        assertEquals(2, box.intersection(box).size());
        assertTrue(box.symmetricDifference(box).isEmpty());
        assertTrue(box.removeAll(box));
        assertTrue(box.isEmpty());
    }

    @Test
    public void iteratorWorksOnASnapshot() {
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();
        for (int i = 0; i < 100; i++) {
            box.add(loc(i, i, i));
        }
        final Iterator<MovecraftLocation> iterator = box.iterator();
        // Neither of these may disturb the running iteration
        box.add(loc(-1, -1, -1));
        box.remove(loc(50, 50, 50));
        int seen = 0;
        while (iterator.hasNext()) {
            iterator.next();
            seen++;
            if (seen == 10) {
                box.clear();
            }
        }
        assertEquals(100, seen);
        assertTrue(box.isEmpty());
    }

    @Test
    public void snapshotIsAnIndependentCopy() {
        final SynchronizedBitmapHitBox box = boxOf(loc(0, 0, 0), loc(3, 4, 5));
        final BitmapHitBox snapshot = box.snapshot();
        box.add(loc(9, 9, 9));
        box.remove(loc(0, 0, 0));
        assertEquals(Set.of(loc(0, 0, 0), loc(3, 4, 5)), contentOf(snapshot));
        assertEquals(0, snapshot.getMinX());
        assertEquals(Set.of(loc(3, 4, 5), loc(9, 9, 9)), contentOf(box));
    }

    @Test
    public void copyConstructorsCopy() {
        final SynchronizedBitmapHitBox original = boxOf(loc(0, 0, 0), loc(1, 1, 1));
        final SynchronizedBitmapHitBox copy = new SynchronizedBitmapHitBox(original);
        copy.add(loc(2, 2, 2));
        assertEquals(2, original.size());
        assertEquals(3, copy.size());
        assertEquals(2, new SynchronizedBitmapHitBox(new BitmapHitBox(original)).size());
    }

    @Test
    public void randomOperationsMatchTheModel() {
        final Random random = new Random(1234);
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();
        final Set<MovecraftLocation> model = new HashSet<>();

        for (int i = 0; i < 4_000; i++) {
            switch (random.nextInt(7)) {
                case 0, 1, 2 -> {
                    final MovecraftLocation l = randomLocation(random, 5, 512);
                    assertEquals(model.add(l), box.add(l));
                }
                case 3 -> {
                    final MovecraftLocation l = randomExistingOrNew(random, model);
                    assertEquals(model.remove(l), box.remove(l));
                }
                case 4 -> {
                    final List<MovecraftLocation> l = randomLocations(random, 1 + random.nextInt(20), 5, 512);
                    assertEquals(model.addAll(l), box.addAll(l));
                }
                case 5 -> {
                    final int count = 1 + random.nextInt(10);
                    final List<MovecraftLocation> l = new ArrayList<>(count);
                    for (int k = 0; k < count; k++) {
                        l.add(randomExistingOrNew(random, model));
                    }
                    assertEquals(model.removeAll(l), box.removeAll(l));
                }
                default -> assertMatchesModel(box, model);
            }
        }
        assertMatchesModel(box, model);
    }

    private static MovecraftLocation randomExistingOrNew(Random random, Set<MovecraftLocation> model) {
        if (!model.isEmpty() && random.nextInt(4) != 0) {
            return new ArrayList<>(model).get(random.nextInt(model.size()));
        }
        return randomLocation(random, 5, 512);
    }

    // ------------------------------------------------------------------------------------------------ multi threaded

    /**
     * The situation that crashed with the plain BitmapHitBox: several threads call isEmpty()/size() (which used to
     * fill a lazy cache inside the Roaring bitmap) while another thread modifies the hitbox.
     */
    @Test(timeout = 120_000)
    public void readersNeverObserveAnInconsistentState() throws Exception {
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();
        final MovecraftLocation sentinel = loc(0, 0, 0);
        // The sentinel is never removed, so readers can rely on it at any time
        box.add(sentinel);
        final AtomicBoolean writerDone = new AtomicBoolean(false);

        final List<Callable<Void>> tasks = new ArrayList<>();
        tasks.add(() -> {
            try {
                final Random random = new Random(99);
                final List<MovecraftLocation> added = new ArrayList<>();
                for (int i = 0; i < 20_000; i++) {
                    switch (random.nextInt(5)) {
                        case 0, 1 -> {
                            final MovecraftLocation l = randomLocation(random, 30, 100);
                            if (!l.equals(sentinel) && box.add(l)) {
                                added.add(l);
                            }
                        }
                        case 2 -> {
                            if (!added.isEmpty()) {
                                box.remove(added.remove(random.nextInt(added.size())));
                            }
                        }
                        case 3 -> {
                            final List<MovecraftLocation> l = randomLocations(random, 50, 30, 100);
                            l.removeIf(sentinel::equals);
                            box.addAll(l);
                            added.addAll(l);
                        }
                        default -> {
                            final List<MovecraftLocation> l = new ArrayList<>();
                            for (int k = 0; k < 50 && !added.isEmpty(); k++) {
                                l.add(added.remove(random.nextInt(added.size())));
                            }
                            box.removeAll(l);
                        }
                    }
                }
            } finally {
                writerDone.set(true);
            }
            return null;
        });

        for (int reader = 0; reader < 4; reader++) {
            tasks.add(() -> {
                while (!writerDone.get()) {
                    assertFalse(box.isEmpty());
                    assertTrue(box.size() >= 1);
                    assertTrue(box.contains(sentinel));
                    assertTrue(box.contains(0, 0, 0));
                    assertTrue(box.inBounds(0, 0, 0));

                    final SynchronizedBitmapHitBox.Bounds bounds = box.getBounds();
                    assertTrue(bounds.minX() <= 0 && 0 <= bounds.maxX());
                    assertTrue(bounds.minY() <= 0 && 0 <= bounds.maxY());
                    assertTrue(bounds.minZ() <= 0 && 0 <= bounds.maxZ());
                    assertTrue(box.getMinYAt(0, 0) <= 0);
                    assertTrue(box.getXLength() >= 1);

                    boolean sawSentinel = false;
                    for (MovecraftLocation l : box) {
                        if (l.equals(sentinel)) {
                            sawSentinel = true;
                        }
                    }
                    assertTrue(sawSentinel);
                }
                return null;
            });
        }
        runConcurrently(tasks);

        // Afterwards the (now quiet) hitbox has to be fully consistent with its own content
        assertMatchesModel(box, contentOf(box));
    }

    @Test(timeout = 120_000)
    public void concurrentWritersOnDisjointRangesProduceTheExactResult() throws Exception {
        final int threads = 8;
        final int perThread = 5_000;
        final SynchronizedBitmapHitBox box = new SynchronizedBitmapHitBox();

        final List<List<MovecraftLocation>> perThreadLocations = new ArrayList<>();
        final Set<MovecraftLocation> all = new HashSet<>();
        final Set<MovecraftLocation> remaining = new HashSet<>();
        for (int t = 0; t < threads; t++) {
            final List<MovecraftLocation> locations = new ArrayList<>(perThread);
            for (int i = 0; i < perThread; i++) {
                // Negative and positive coordinates, a different x range and z plane per thread
                final MovecraftLocation l = loc(t * 100 + i % 100 - 400, i / 100 - 25, t - 4);
                locations.add(l);
                all.add(l);
                if (i % 2 != 0) {
                    remaining.add(l);
                }
            }
            perThreadLocations.add(locations);
        }
        assertEquals(threads * perThread, all.size());

        final List<Callable<Void>> adders = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final List<MovecraftLocation> locations = perThreadLocations.get(t);
            final boolean bulk = t % 2 == 0;
            adders.add(() -> {
                if (bulk) {
                    box.addAll(locations);
                } else {
                    for (MovecraftLocation l : locations) {
                        box.add(l);
                    }
                }
                return null;
            });
        }
        runConcurrently(adders);
        assertMatchesModel(box, all);

        final List<Callable<Void>> removers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final List<MovecraftLocation> locations = perThreadLocations.get(t);
            removers.add(() -> {
                for (int i = 0; i < locations.size(); i += 2) {
                    box.remove(locations.get(i));
                }
                return null;
            });
        }
        runConcurrently(removers);
        assertMatchesModel(box, remaining);
    }

    /**
     * a.addAll(b) racing with b.addAll(a) is the classic lock ordering problem. A deadlock shows up as a timeout.
     */
    @Test(timeout = 120_000)
    public void operationsBetweenTwoHitBoxesDoNotDeadlock() throws Exception {
        final Random random = new Random(5);
        final SynchronizedBitmapHitBox a = new SynchronizedBitmapHitBox();
        final SynchronizedBitmapHitBox b = new SynchronizedBitmapHitBox();
        a.addAll(randomLocations(random, 1_000, 20, 50));
        b.addAll(randomLocations(random, 1_000, 20, 50));

        final List<Callable<Void>> tasks = new ArrayList<>();
        for (int copy = 0; copy < 2; copy++) {
            tasks.add(() -> {
                for (int i = 0; i < 300; i++) {
                    a.addAll(b);
                    a.union(b);
                    a.intersection(b);
                    a.containsAll(b.asSet());
                    a.removeAll(b.asSet());
                    a.addAll(b.difference(a));
                }
                return null;
            });
            tasks.add(() -> {
                for (int i = 0; i < 300; i++) {
                    b.addAll(a);
                    b.symmetricDifference(a);
                    b.difference(a);
                    b.containsAll(a.asSet());
                    b.removeAll(a);
                    b.addAll(a.intersection(b));
                }
                return null;
            });
        }
        runConcurrently(tasks);

        assertMatchesModel(a, contentOf(a));
        assertMatchesModel(b, contentOf(b));
    }
}
