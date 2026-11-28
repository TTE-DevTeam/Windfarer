package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.state.CraftStateHandle;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class SubCraftImpl extends BaseCraft implements SubCraft {
    @NotNull
    private Craft parent;
    private volatile CraftStateHandle parentStateHandle;
    private boolean cutOut;

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
    public synchronized void cutOutOfParent() {
        this.parent.setHitBox(this.parent.getHitBox().difference(this.getHitBox()));
        this.cutOut = true;
    }

    @Override
    public synchronized void mergeIntoParent() {
        if (!this.cutOut) {
            return;
        }
        this.cutOut = false;
        this.parent.setHitBox(this.parent.getHitBox().union(this.getHitBox()));
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
