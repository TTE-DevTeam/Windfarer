/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftCache;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.util.MathUtils;
import net.countercraft.movecraft.util.Tags;
import net.countercraft.movecraft.util.hitboxes.BitmapHitBox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.material.Attachable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Optional;

public class BlockListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent e) {
        if (!Settings.ProtectPilotedCrafts)
            return;
        if (e.getBlock().getType() == Material.FIRE)
            return; // allow players to punch out fire

        Location location = e.getBlock().getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            // TODO: check against flag in crafttype
            final Craft craft = optCraft.get();
            boolean craftAllowsBlockBreaking = !craft.getCraftProperties().get(PropertyKeys.REQUIRE_DISABLED_TO_BREAK_BLOCKS) || (craft.getCraftProperties().get(PropertyKeys.ALLOW_BLOCK_BREAKING_WHEN_DISABLED) && craft.getDisabled());
            if (craftAllowsBlockBreaking)
                return;

            e.setCancelled(true);
            return;
        }
    }

    //Prevents non pilots from placing blocks on your ship.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!Settings.ProtectPilotedCrafts)
            return;

        Location location = e.getBlockAgainst().getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Player p = e.getPlayer();
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            if (craft.getDisabled() || !(craft instanceof PilotedCraft))
                return;
            if (((PilotedCraft) craft).getPilotUUID().equals(p.getUniqueId()))
                return;

            e.setCancelled(true);
            return;
        }
    }

    // prevent items from dropping from moving crafts
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(@NotNull ItemSpawnEvent e) {
        Location location = e.getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            if (craft.isNotProcessing())
                return;

            e.setCancelled(true);
            return;
        }
    }

    // process certain redstone on cruising crafts
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRedstoneEvent(@NotNull BlockRedstoneEvent e) {
        // Toggleable as this is rather expensive
        if (!Settings.suppressRedstoneEventOnMovingCrafts)
            return;

        Block block = e.getBlock();
        // Only react if we are sticky piston, normal piston or dispenser
        // TODO: In the case of droppers and dispensers, try checking via the attached data container like we do for signs!
        if (!(block.getType() == Material.STICKY_PISTON || block.getType() == Material.PISTON || block.getType() == Material.DISPENSER))
            return;

        Location location = block.getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            if (craft.isNotProcessing())
                return;

            e.setNewCurrent(e.getOldCurrent()); // don't allow piston movement on cruising crafts
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtendEvent(@NotNull BlockPistonExtendEvent e) {
        onPistonEvent(e, e.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetractEvent(@NotNull BlockPistonRetractEvent e) {
        onPistonEvent(e, e.getBlocks());
    }

    public void onPistonEvent(@NotNull BlockPistonEvent e, final @NotNull List<Block> affectedBlocks) {
        Block block = e.getBlock();
        Location location = block.getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();

           if (!craft.isNotProcessing())
               e.setCancelled(true); // prevent pistons on cruising crafts           
            // merge piston extensions to craft if the property is true
           if (!craft.getCraftProperties().get(PropertyKeys.MERGE_PISTON_EXTENSIONS))
                return;

           BitmapHitBox hitBox = new BitmapHitBox();
           for (Block b : affectedBlocks) {
               Vector dir = e.getDirection().getDirection();
               hitBox.add(new MovecraftLocation(b.getX() + dir.getBlockX(), b.getY() + dir.getBlockY(), b.getZ() + dir.getBlockZ()));
           }
           craft.setHitBox(craft.getHitBox().union(hitBox));
        }
    }


    // prevent hoppers on cruising crafts
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperEvent(@NotNull InventoryMoveItemEvent e) {
        if ((e.getSource().getHolder(false) instanceof Hopper hopper)) {
            Location location = hopper.getLocation();
            MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
            Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
            if (optCraft.isPresent()) {
                final Craft craft = optCraft.get();
                if (craft.isNotProcessing())
                    return;

                e.setCancelled(true);
                return;
            }
        }
    }

    // prevent fragile items from dropping on cruising crafts
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(@NotNull BlockPhysicsEvent e) {
        Block block = e.getBlock();
        if (!Tags.FRAGILE_MATERIALS.contains(block.getType()))
            return;

        Location location = block.getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();

            BlockData m = block.getBlockData();
            BlockFace face = BlockFace.DOWN;
            boolean faceAlwaysDown = block.getType() == Material.COMPARATOR || block.getType() == Material.REPEATER;
            if (m instanceof Attachable && !faceAlwaysDown)
                face = ((Attachable) m).getAttachedFace();

            if (e.getBlock().getRelative(face).getType().isSolid())
                return;

            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(@NotNull BlockDispenseEvent e) {
        Location location = e.getBlock().getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            if (craft.isNotProcessing())
                return;

            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(@NotNull BlockFromToEvent e) {
        if (Settings.DisableSpillProtection)
            return;
        Block block = e.getBlock();
        if (!Tags.FLUID.contains(block.getType()) && (!(block.getBlockData() instanceof Waterlogged waterlogged) || !waterlogged.isWaterlogged()))
            return; // If the source is not a fluid or waterlogged, exit

        Location location = block.getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        MovecraftLocation toLoc = MathUtils.bukkit2MovecraftLoc(e.getToBlock().getLocation());
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            if (craft.getFluidLocations().contains(toLoc))
                return;

            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIceForm(@NotNull BlockFormEvent e) {
        if (!Settings.DisableIceForm)
            return;
        if (Tags.WATER.contains(e.getBlock().getType()))
            return;

        Location location = e.getBlock().getLocation();
        MovecraftLocation loc = MathUtils.bukkit2MovecraftLoc(location);
        Optional<Craft> optCraft = CraftCache.getCraftAt(location.getWorld(), loc);
        if (optCraft.isPresent()) {
            e.setCancelled(true);
            return;
        }
    }
}
