package net.countercraft.movecraft.craft;

public class CraftCache {

    // Cache WeakMap<World<Map<ChunkPos<List<WeakReference<Craft>>>>>>
    // Stores a reference to all crafts per chunk
    // Updating has to happen via one AsyncTask that works down a queue of updates
    // Whenever a craft finished a movement operation, its old locations need to be removed and then recalculated
    // For that, the craft holds a reference to the last chunks it was in (SolidHitbox on chunk coordinate level + world reference)
    // Whenever a craft is to be removed or released (=> Hook in CraftManager), it needs to be removed from all lists as well
    // In theory, the oldLoc + currentLoc from FinishedMovement is already enough for us to calculate the difference

}
