package net.countercraft.movecraft.util;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public class NamespacedIDUtil {

    public static NamespacedKey getBlockID(final Block block) {
        BlockData blockData = block.getBlockData();
        return getBlockID(blockData);
    }

    public static NamespacedKey getBlockID(final BlockData blockData) {
        if (blockData == null || !(blockData instanceof Keyed)) {
            // Super ugly workaround but egh, works
            String asString = blockData.getAsString();
            int dataStartIndex = asString.indexOf('[');
            if (dataStartIndex >= 0) {
                asString = asString.substring(0, dataStartIndex);
            }
            if (asString.isEmpty()) {
                return blockData.getMaterial().getKey();
            } else {
                return NamespacedKey.fromString(asString);
            }
        } else {
            Keyed keyed = (Keyed) blockData;
            return keyed.getKey();
        }
    }
}
