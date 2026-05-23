package net.countercraft.movecraft.features.directControl.slot;

import io.papermc.paper.registry.RegistryKey;
import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.controller.directControl.AbstractDirectControlSlot;
import net.countercraft.movecraft.util.Counter;
import net.countercraft.movecraft.util.NamespacedIDUtil;
import net.countercraft.movecraft.util.SerializationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class BlockInteractDirectControlSlot extends AbstractDirectControlSlot {

    private final Set<NamespacedKey> interactableBlocks;
    private final Set<NamespacedKey> baseBlocks;
    private final boolean checkBaseBlocks;
    private final int maxLimit;

    public BlockInteractDirectControlSlot(final Map<String, Object> yamlData) {
        super(yamlData);
        this.interactableBlocks = SerializationUtil.deserializeNamespacedKeySet(yamlData.get("interactable_blocks"), Set.of(Material.LEVER.getKey()), RegistryKey.BLOCK);
        this.baseBlocks = SerializationUtil.deserializeNamespacedKeySet(yamlData.get("base_blocks"), Set.of(), RegistryKey.BLOCK);
        this.checkBaseBlocks = SerializationUtil.deserializeBoolean("check_base_block", yamlData, true);
        this.maxLimit = NumberConversions.toInt(yamlData.getOrDefault("limit", -1));
    }

    public BlockInteractDirectControlSlot(BlockInteractDirectControlSlot toCopy) {
        super(toCopy.cooldown);

        this.interactableBlocks = new HashSet<>(toCopy.interactableBlocks);
        this.baseBlocks = new HashSet<>(toCopy.baseBlocks);
        this.checkBaseBlocks = toCopy.checkBaseBlocks;
        this.maxLimit = toCopy.maxLimit;
    }

    @Override
    protected boolean doOnLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        return onInteraction(craft, action);
    }

    protected boolean onInteraction(Craft craft, Action action) {
        if (!(action.isLeftClick() || action.isRightClick())) {
            return false;
        }

        final Counter<NamespacedKey> blockCounter = craft.getDataTag(Craft.BLOCKS);

        boolean result = false;
        final AtomicInteger limit = new AtomicInteger(0);
        for (NamespacedKey interactorBlock : this.interactableBlocks) {
            if (blockCounter.getKeySet().contains(interactorBlock)) {
                result = this.processInteractor(interactorBlock, craft, action, limit) || result;
            }
            if (this.maxLimit > 0 && limit.get() >= this.maxLimit) {
                break;
            }
        }

        return result;
    }

    // TODO use processing system
    protected boolean processInteractor(NamespacedKey interactorBlock, Craft craft, Action action, AtomicInteger limit) {
        boolean result = false;
        for (MovecraftLocation movecraftLocation : craft.getHitBox()) {

            final Location bukkitLoc = movecraftLocation.toBukkit(craft.getWorld());
            final BlockData blockData = craft.getWorld().getBlockData(bukkitLoc);
            final NamespacedKey atLoc = NamespacedIDUtil.getBlockID(blockData);

            if (!interactorBlock.equals(atLoc)) {
                continue;
            }

            // Validate base block
            if (blockData instanceof Switch switchBLock) {
                if (this.checkBaseBlocks) {
                    BlockFace facing = switchBLock.getFacing();
                    final FaceAttachable.AttachedFace attachedFace = switchBLock.getAttachedFace();
                    Location baseLocation = bukkitLoc.clone();
                    switch (attachedFace) {
                        case FLOOR -> facing = BlockFace.DOWN;
                        case CEILING -> facing = BlockFace.UP;
                        default -> facing = facing.getOppositeFace();
                    }
                    baseLocation = baseLocation.add(facing.getDirection());
                    if (!this.baseBlocks.contains(NamespacedIDUtil.getBlockID(baseLocation.getBlock()))) {
                        continue;
                    }
                }
            }

            // Location is valid, toggle the button
            if (Movecraft.getInstance().getNMSHelper() != null) {
                result = Movecraft.getInstance().getNMSHelper().tryInteractLever(bukkitLoc);
            }

            if (this.maxLimit > 0 && limit.get() >= this.maxLimit) {
                break;
            }
        }
        return result;
    }

    @Override
    protected boolean doOnRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        return onInteraction(craft, action);
    }

    @Override
    protected boolean doOnItemDrop(ItemStack itemStack, Player interactor, Craft craft) {
        return false;
    }

    @Override
    public boolean doOnSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft) {
        return false;
    }

    @Override
    protected boolean doOnPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection) {
        return false;
    }

    @Override
    public AbstractDirectControlSlot clone() {
        return new BlockInteractDirectControlSlot(this);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = super.serialize();

        result.put("interactable_blocks", this.interactableBlocks);
        result.put("base_blocks", this.baseBlocks);
        result.put("check_base_block", this.checkBaseBlocks);
        result.put("limit", this.maxLimit);

        return result;
    }
}
