package net.countercraft.movecraft.util.hitboxes;

import net.countercraft.movecraft.MovecraftLocation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HitBoxSlicerTest {

    private static MovecraftLocation loc(int x, int y, int z) {
        return new MovecraftLocation(x, y, z);
    }

    private static List<List<MovecraftLocation>> slice(HitBox hitBox) {
        final List<List<MovecraftLocation>> out = new ArrayList<>();
        for (Iterable<MovecraftLocation> slice : new HitBoxSlicer(hitBox)) {
            final List<MovecraftLocation> locations = new ArrayList<>();
            slice.forEach(locations::add);
            out.add(locations);
        }
        return out;
    }

    /** Reference implementation of the chunk coordinate, independent from the bit tricks in the slicer. */
    private static int chunk(int coordinate) {
        return Math.floorDiv(coordinate, 16);
    }

    private static long columnOf(MovecraftLocation location) {
        return ((long) chunk(location.getZ()) << 32) | (chunk(location.getX()) & 0xFFFFFFFFL);
    }

    private static BitmapHitBox randomHitBox(long seed, int count, int xzRange, int yRange) {
        final Random random = new Random(seed);
        final BitmapHitBox hitBox = new BitmapHitBox();
        for (int i = 0; i < count; i++) {
            hitBox.add(loc(
                    random.nextInt(2 * xzRange + 1) - xzRange,
                    random.nextInt(2 * yRange + 1) - yRange,
                    random.nextInt(2 * xzRange + 1) - xzRange
            ));
        }
        return hitBox;
    }

    @Test
    public void everyBlockIsInExactlyOneSlice() {
        final BitmapHitBox hitBox = randomHitBox(42, 20_000, 100, 512);
        final List<MovecraftLocation> all = new ArrayList<>();
        for (List<MovecraftLocation> slice : slice(hitBox)) {
            all.addAll(slice);
        }
        // Same count and same content => nothing lost, nothing duplicated
        assertEquals(hitBox.size(), all.size());
        assertEquals(new HashSet<>(hitBox.asSet()), new HashSet<>(all));
    }

    @Test
    public void everySliceIsExactlyOneChunkColumn() {
        final BitmapHitBox hitBox = randomHitBox(43, 20_000, 100, 512);
        final List<List<MovecraftLocation>> slices = slice(hitBox);
        final Set<Long> columns = new HashSet<>();
        for (List<MovecraftLocation> slice : slices) {
            assertFalse(slice.isEmpty());
            final long column = columnOf(slice.get(0));
            for (MovecraftLocation location : slice) {
                assertEquals(column, columnOf(location), "Block " + location + " is in the wrong slice");
            }
            assertTrue(columns.add(column), "Chunk column is spread over several slices");
        }
        final Set<Long> expectedColumns = new HashSet<>();
        for (MovecraftLocation location : hitBox) {
            expectedColumns.add(columnOf(location));
        }
        assertEquals(expectedColumns, columns);
    }

    @Test
    public void negativeCoordinatesLandInTheCorrectChunk() {
        final BitmapHitBox hitBox = new BitmapHitBox(List.of(
                loc(-1, 0, -1),     // chunk (-1, -1)
                loc(-16, 5, -16),   // chunk (-1, -1), lowest block of that chunk
                loc(-17, -512, 15), // chunk (-2, 0)
                loc(15, 512, -17),  // chunk (0, -2)
                loc(0, 0, 0),       // chunk (0, 0)
                loc(16, 0, 16),     // chunk (1, 1)
                loc(-1, -1, 0)      // chunk (-1, 0)
        ));
        final List<List<MovecraftLocation>> slices = slice(hitBox);
        assertEquals(6, slices.size());

        List<MovecraftLocation> negativeCorner = null;
        for (List<MovecraftLocation> slice : slices) {
            if (slice.contains(loc(-1, 0, -1))) {
                negativeCorner = slice;
            }
        }
        assertEquals(Set.of(loc(-1, 0, -1), loc(-16, 5, -16)), new HashSet<>(negativeCorner));
    }

    @Test
    public void fullYRangeSurvivesTheEncoding() {
        final int[] ys = {-2048, -512, -1, 0, 1, 511, 512, 2047};
        final BitmapHitBox hitBox = new BitmapHitBox();
        final Set<MovecraftLocation> expected = new HashSet<>();
        for (int y : ys) {
            hitBox.add(loc(3, y, 4));
            expected.add(loc(3, y, 4));
        }
        final List<List<MovecraftLocation>> slices = slice(hitBox);
        assertEquals(1, slices.size());
        assertEquals(expected, new HashSet<>(slices.get(0)));
    }

    @Test
    public void emptyHitBoxHasNoSlices() {
        assertFalse(new HitBoxSlicer(new BitmapHitBox()).iterator().hasNext());
        assertFalse(new HitBoxSlicer(new SynchronizedBitmapHitBox()).iterator().hasNext());
    }

    @Test
    public void slicesCanBeIteratedRepeatedly() {
        final BitmapHitBox hitBox = randomHitBox(44, 500, 40, 64);
        final Iterable<MovecraftLocation> slice = new HitBoxSlicer(hitBox).iterator().next();
        final List<MovecraftLocation> first = new ArrayList<>();
        final List<MovecraftLocation> second = new ArrayList<>();
        slice.forEach(first::add);
        slice.forEach(second::add);
        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    public void slicesAreOrderedByChunkZThenChunkX() {
        final BitmapHitBox hitBox = randomHitBox(45, 5_000, 100, 64);
        long previousZ = Long.MIN_VALUE;
        long previousX = Long.MIN_VALUE;
        for (List<MovecraftLocation> slice : slice(hitBox)) {
            final long z = chunk(slice.get(0).getZ());
            final long x = chunk(slice.get(0).getX());
            assertTrue(z > previousZ || (z == previousZ && x > previousX), "Slices are not ordered");
            previousZ = z;
            previousX = x;
        }
    }

    @Test
    public void yOutsideOfTheSupportedRangeFailsLoudly() {
        final HitBox tooHigh = new SolidHitBox(loc(0, 2048, 0), loc(0, 2048, 0));
        final HitBox tooLow = new SolidHitBox(loc(0, -2049, 0), loc(0, -2049, 0));
        assertThrows(IllegalStateException.class, () -> new HitBoxSlicer(tooHigh).iterator());
        assertThrows(IllegalStateException.class, () -> new HitBoxSlicer(tooLow).iterator());
    }

    @Test
    public void matchesTheOldIntersectionBasedSlicing() {
        final BitmapHitBox hitBox = randomHitBox(46, 3_000, 40, 64);
        final List<List<MovecraftLocation>> slices = slice(hitBox);

        final Set<Long> expectedColumns = new HashSet<>();
        for (MovecraftLocation location : hitBox) {
            expectedColumns.add(columnOf(location));
        }
        assertEquals(expectedColumns.size(), slices.size());

        for (List<MovecraftLocation> slice : slices) {
            final int chunkX = chunk(slice.get(0).getX());
            final int chunkZ = chunk(slice.get(0).getZ());
            // This is what the previous implementation did for every chunk column
            final SolidHitBox bounds = new SolidHitBox(
                    loc(chunkX * 16, hitBox.getMinY(), chunkZ * 16),
                    loc(chunkX * 16 + 15, hitBox.getMaxY(), chunkZ * 16 + 15)
            );
            final Set<MovecraftLocation> expected = new HashSet<>(hitBox.intersection(bounds).asSet());
            assertEquals(expected, new HashSet<>(slice));
        }
    }

    @Test
    public void worksWithSynchronizedBitmapHitBox() {
        final SynchronizedBitmapHitBox hitBox = new SynchronizedBitmapHitBox(randomHitBox(47, 5_000, 60, 512));
        final List<MovecraftLocation> all = new ArrayList<>();
        for (List<MovecraftLocation> slice : slice(hitBox)) {
            all.addAll(slice);
        }
        assertEquals(hitBox.size(), all.size());
        assertEquals(new HashSet<>(hitBox.asSet()), new HashSet<>(all));
    }

    @Test
    public void slicesAreSnapshotsOfTheHitBox() {
        final SynchronizedBitmapHitBox hitBox = new SynchronizedBitmapHitBox(randomHitBox(48, 1_000, 40, 64));
        final Set<MovecraftLocation> before = new HashSet<>(hitBox.asSet());
        final List<List<MovecraftLocation>> slices = new ArrayList<>();
        final HitBoxSlicer slicer = new HitBoxSlicer(hitBox);
        final List<Iterable<MovecraftLocation>> sliceIterables = new ArrayList<>();
        slicer.forEach(sliceIterables::add);

        hitBox.clear();

        for (Iterable<MovecraftLocation> slice : sliceIterables) {
            final List<MovecraftLocation> locations = new ArrayList<>();
            slice.forEach(locations::add);
            slices.add(locations);
        }
        final Set<MovecraftLocation> after = new HashSet<>();
        slices.forEach(after::addAll);
        assertEquals(before, after);
    }
}
