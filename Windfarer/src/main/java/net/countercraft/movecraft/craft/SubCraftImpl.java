package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.state.CraftStateHandle;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class SubCraftImpl extends BaseCraft implements SubCraft {
    @NotNull
    private Craft parent;
    private volatile CraftStateHandle parentStateHandle;

    public SubCraftImpl(@NotNull TypeSafeCraftType type, @NotNull World world, @NotNull Craft parent) {
        super(type, world);
        this.parent = parent;
    }

    @Override
    @NotNull
    public Craft getParent() {
        return parent;
    }

    @Override
    public void setParent(@NotNull Craft parent) {
        this.parent = parent;
    }

    @Override
    public CraftStateHandle getParentStateHandle() {
        return this.parentStateHandle;
    }

    @Override
    public void setParentStateHandle(CraftStateHandle handle) {
        this.parentStateHandle = handle;
    }
}
