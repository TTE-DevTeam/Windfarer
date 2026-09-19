package net.countercraft.movecraft.support.v26_3;

import net.countercraft.movecraft.NMSHelper;
import net.countercraft.movecraft.util.ReflectUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Furnace;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.block.CraftFurnace;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Optional;

public class INMSHelper extends NMSHelper {

    @Override
    public boolean isFuel(ItemStack itemStack, World world) {
        net.minecraft.world.item.ItemStack nmsItemStack = ((CraftItemStack)itemStack).handle;
        return nmsItemStack.has(DataComponents.COOKING_FUEL);
    }

    @Override
    public int getBurnDuration(ItemStack itemStack, World world) {
        if (isFuel(itemStack, world)) {
            net.minecraft.world.item.ItemStack nmsItemStack = ((CraftItemStack)itemStack).handle;
            CookingFuel component = nmsItemStack.get(DataComponents.COOKING_FUEL);
            return component.burnTime().get(
                    new LootContext.Builder(
                            new LootParams.Builder(
                                    ((CraftWorld)world).getHandle()
                            ).create(
                                    LootContextParamSets.CONTAINER_PROCESS
                            )
                    ).create(Optional.empty()), 0);
        }
        return 0;
    }

    static final @NotNull Field FURNACE_LIT_TOTAL_TIME;
    static final @NotNull Field CRAFT_BLOCK_ENTITY_STATE_SNAPSHOT;

    static {
        try {
            FURNACE_LIT_TOTAL_TIME = ReflectUtils.getField(AbstractFurnaceBlockEntity.class, "litTotalTime");
            CRAFT_BLOCK_ENTITY_STATE_SNAPSHOT = ReflectUtils.getField(CraftBlockEntityState.class, "snapshot");
            CRAFT_BLOCK_ENTITY_STATE_SNAPSHOT.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setFurnaceBurnTime(int burnTime, int totalBurnTime, Furnace furnace) {
        Object snapshot = null;
        try {
            snapshot = CRAFT_BLOCK_ENTITY_STATE_SNAPSHOT.get(((CraftFurnace)furnace));
        } catch (Exception exception) {
            exception.printStackTrace();
            return;
        }
        if (snapshot == null) {
            return;
        }
        if (!(snapshot instanceof AbstractFurnaceBlockEntity)) {
            return;
        }

        AbstractFurnaceBlockEntity furnaceBlockEntity = (AbstractFurnaceBlockEntity) snapshot;
        try {
            FURNACE_LIT_TOTAL_TIME.set(furnaceBlockEntity, totalBurnTime);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        furnaceBlockEntity.litTimeRemaining = burnTime;
    }

    @Override
    public boolean tryInteractLever(Location bukkitLoc) {
        final CraftBlock craftBlock = (CraftBlock) bukkitLoc.getBlock();
        final BlockState blockState = craftBlock.getBlockState();
        final Block block = blockState.getBlock();

        if (block instanceof LeverBlock leverBlock) {
            leverBlock.pull(blockState, craftBlock.getCraftWorld().getHandle(), new BlockPos(bukkitLoc.getBlockX(), bukkitLoc.getBlockY(), bukkitLoc.getBlockZ()), null);
            return true;
        } else
        if (block instanceof ButtonBlock buttonBlock) {
            buttonBlock.press(blockState, craftBlock.getCraftWorld().getHandle(), new BlockPos(bukkitLoc.getBlockX(), bukkitLoc.getBlockY(), bukkitLoc.getBlockZ()), null);
            return true;
        }
        return false;
    }
}
