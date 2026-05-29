package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.events.CraftDetectEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractToggleSign extends AbstractCraftSign {

    protected final String suffixOn;
    protected final String suffixOff;
    protected final String ident;
    protected final Component headerOn;
    protected final Component headerOff;

    public AbstractToggleSign(boolean ignoreCraftIsBusy, final String ident, final String suffixOn, final String suffixOff) {
        this(null, ignoreCraftIsBusy, ident, suffixOn, suffixOff);
    }

    public AbstractToggleSign(final String permission, boolean ignoreCraftIsBusy, final String ident,  final String suffixOn, final String suffixOff) {
        super(permission, ignoreCraftIsBusy);
        this.suffixOn = suffixOn;
        this.suffixOff = suffixOff;
        this.ident = ident;

        this.headerOn = this.buildHeaderOn();
        this.headerOff = this.buildHeaderOff();
    }

    // Checks if the header is empty, if yes, it quits early (unnecessary actually as if it was empty this would never be called)
    // Afterwards the header is validated, if it's splitted variant doesn't have exactly 2 entries it is invalid
    // Finally, the "state" (second part of the header) isn't matching suffixOn or suffixOff, it is invalid
    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        if (PlainTextComponentSerializer.plainText().serialize(sign.line(0)).isBlank()) {
            return false;
        }
        String[] headerSplit = getSplitHeader(sign);
        if (headerSplit.length != 2) {
            return false;
        }
        String suffix = headerSplit[1].trim();
        return suffix.equalsIgnoreCase(this.suffixOff) || suffix.equalsIgnoreCase(this.suffixOn);
    }

    // Returns the raw header, which should consist of the ident and either the suffixOn or suffixOff value
    // Returns null if the header is blank
    @Nullable
    protected static String[] getSplitHeader(final SignListener.SignWrapper sign) {
        String header = PlainTextComponentSerializer.plainText().serialize(sign.line(0));
        if (header.isBlank()) {
            return null;
        }
        return header.split(":");
    }

    // If the suffix matches the suffixOn field it will returnt true
    // calls getSplitHeader() to retrieve the raw header string
    public boolean isOnOrOff(SignListener.SignWrapper sign) {
        String[] headerSplit = getSplitHeader(sign);
        if (headerSplit == null || headerSplit.length != 2) {
            return false;
        }
        String suffix = headerSplit[1].trim();
        return suffix.equalsIgnoreCase(this.suffixOn);
    }

    protected abstract void onAfterToggle(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor, boolean toggledToOn);
    protected abstract boolean onBeforeToggle(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor, boolean willBeOn);

    // Actual processing, determines wether the sign will switch to on or off
    // If it will be on, the CruiseDirection is retrieved and then setCraftCruising() is called
    // Otherwise, the craft will stop cruising
    // Then the sign is updated and the block resetted
    // Finally, the relevant hooks are called
    // This always returns true
    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Entity interactor) {
        boolean isOn = this.isOnOrOff(sign);
        boolean willBeOn = !isOn;

        // If we dont toggle, return false
        if (!this.onBeforeToggle(craft, sign, interactor, willBeOn)) {
            return false;
        }

        // Update sign
        sign.line(0, buildHeader(willBeOn));
        sign.block().update(true);
        //craft.resetSigns(sign.block());

        CraftSignManager.executeForSignsOfType(AbstractToggleSign.class, craft, (handler, signWrapper, craftInner) -> {
            // Use THIS handler, not the handler of that other sign! Otherwise the reset logic wont behave correctly
            return this.doReset(sign, signWrapper, craftInner, willBeOn);
        });

//        final CraftSignManager craftSignManager = CraftSignManager.of(craft);
//        if (craftSignManager != null) {
//            for (final MovecraftLocation mLoc : craftSignManager.getSignsOfClass(AbstractToggleSign.class)) {
//                final Block b = mLoc.toBukkit(craft.getWorld()).getBlock();
//                if (!(b.getState() instanceof Sign)) {
//                    continue;
//                }
//                final Sign s = (Sign) b.getState();
//                boolean update = false;
//                SignListener.SignWrapper[] wrappers = SignListener.INSTANCE.getSignWrappers(s);
//                for (SignListener.SignWrapper wrapperTmp : wrappers) {
//                    if (wrapperTmp.areSignsEqual(sign)) {
//                        continue;
//                    }
//                    if (this.doReset(sign, wrapperTmp, craft)) {
//                        update = true;
//                    }
//                }
//                if (update)
//                    s.update();
//            }
//        }

        this.onAfterToggle(craft, sign, interactor, willBeOn);

        return true;
    }

    public boolean doReset(SignListener.SignWrapper original, SignListener.SignWrapper other, Craft craft) {
        return doReset(original, other, craft, this.isOnOrOff(original));
    }

    public boolean doReset(SignListener.SignWrapper original, SignListener.SignWrapper other, Craft craft, boolean onOrOff) {
        AbstractMovecraftSign otherHandler = MovecraftSignRegistry.INSTANCE.getCraftSign(other.line(0));
        if (otherHandler != this) {
            if (otherHandler instanceof AbstractToggleSign ats && ats.canBeResettedBy(this)) {
                other.line(0, ats.buildHeaderOff());
                return true;
            }
            return false;
        }
        if (this.shouldShareSameToggleState(original, other, craft)) {
            other.line(0, this.buildHeader(onOrOff));
        } else {
            other.line(0, this.buildHeaderOff());
        }
        return true;
    }

    protected boolean canBeResettedBy(AbstractToggleSign caller) {
        return true;
    }

    protected boolean shouldShareSameToggleState(SignListener.SignWrapper sign, SignListener.SignWrapper other, Craft craft) {
        return true;
    }

    // On sign placement, if the entered header is the same as our ident, it will append the off-suffix automatically
    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        sign.line(0, buildHeaderOff());
        return true;
    }

    // On craft detection, we set all the headers to the "off" header
    @Override
    public void onCraftDetect(CraftDetectEvent event, SignListener.SignWrapper sign) {
        Player p = null;
        if (event.getCraft() instanceof PilotedCraft pc) {
            if (pc.getPilotEntity() instanceof Player) {
                p = (Player) pc.getPilotEntity();
            }
        } else if (event.getCraft() instanceof PlayerCraft pc) {
            p = pc.getPilotPlayer();
        }

        if (this.isSignValid(Action.PHYSICAL, sign, p)) {
            sign.line(0, buildHeader(false));
        } else {
            // TODO: Error? React in any way?
            sign.line(0, buildHeader(false));
        }
        sign.block().update();
    }

    // Helper method to build the headline for on or off state
    protected Component buildHeader(boolean on) {
        return on ? this.headerOn : this.headerOff;
    }

    protected Component buildHeaderOn() {
        return Component.text(this.ident).append(this.ident.endsWith(":") ? Component.text(" ") : Component.text(": ")).append(Component.text(this.suffixOn, Style.style(TextColor.color(0, 255, 0))));
    }

    protected Component buildHeaderOff() {
        return Component.text(this.ident).append(this.ident.endsWith(":") ? Component.text(" ") : Component.text(": ")).append(Component.text(this.suffixOff, Style.style(TextColor.color(255, 0, 0))));
    }

}
