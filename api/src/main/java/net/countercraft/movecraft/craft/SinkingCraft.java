package net.countercraft.movecraft.craft;

public interface SinkingCraft extends Craft {

    @Override
    default boolean shouldAutoRelease(final long autoReleaseTimeout, final long maxTimeBetweenCruiseUpdates) {
        return false;
    }
}
