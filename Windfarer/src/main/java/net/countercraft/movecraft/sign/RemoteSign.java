package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static net.countercraft.movecraft.util.ChatUtils.ERROR_PREFIX;

public class RemoteSign extends AbstractCraftSign {
    private static final String HEADER = "Remote Sign";

    public RemoteSign() {
        super(null, false);
    }

    @Override
    protected void onCraftIsBusy(Entity interactor, Craft craft) {
        // TODO: How to react?
    }

    @Override
    protected void onCraftNotFound(Entity interactor, SignListener.SignWrapper sign) {
        interactor.sendMessage(ERROR_PREFIX+I18nSupport.getInternationalisedString("Remote Sign - Must be a part of a piloted craft"));
    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Entity interactor) {
        Map<AbstractMovecraftSign, LinkedList<SignListener.SignWrapper>> foundTargetSigns = new HashMap<>();
        boolean firstError = true;
        final String targetIdent = sign.getRaw(1);
        final CraftSignManager signManager = CraftSignManager.of(craft);
        if (signManager == null) {
            return false;
        }
        for (MovecraftLocation tloc : signManager.getSignsOfClass(AbstractMovecraftSign.class)) {
            BlockState tstate = craft.getWorld().getBlockAt(tloc.getX(), tloc.getY(), tloc.getZ()).getState();
            if (!Tag.ALL_SIGNS.isTagged(tstate.getType())) {
                continue;
            }
            if (!(tstate instanceof Sign)) {
                continue;
            }
            Sign ts = (Sign) tstate;

            SignListener.SignWrapper[] targetSignWrappers = SignListener.INSTANCE.getSignWrappers(ts);

            if (targetSignWrappers != null) {
                for (SignListener.SignWrapper wrapper : targetSignWrappers) {
                    // Matches source?
                    final String signHeader = PlainTextComponentSerializer.plainText().serialize(wrapper.line(0));
                    AbstractMovecraftSign signHandler = AbstractMovecraftSign.get(signHeader);
                    // Ignore other remove signs
                    if (signHandler == null || signHandler instanceof RemoteSign) {
                        continue;
                    }
                    // But does it match the source man?
                    if (matchesDescriptor(targetIdent, wrapper)) {
                        // Forbidden strings
                        if (hasForbiddenString(wrapper)) {
                            if (firstError) {
                                interactor.sendMessage(I18nSupport.getInternationalisedString("Remote Sign - Forbidden string found"));
                                firstError = false;
                            }
                            interactor.sendMessage(" - ".concat(tloc.toString()).concat(" : ").concat(ts.getLine(0)));
                        } else {
                            LinkedList<SignListener.SignWrapper> value = foundTargetSigns.computeIfAbsent(signHandler, (a) -> new LinkedList<>());
                            value.add(wrapper);
                        }
                    }
                }
            }
        }
        if (!firstError) {
            return false;
        }
        else if (foundTargetSigns.isEmpty()) {
            interactor.sendMessage(I18nSupport.getInternationalisedString("Remote Sign - Could not find target sign"));
            return false;
        }

        if (Settings.MaxRemoteSigns > -1) {
            int foundLocCount = foundTargetSigns.size();
            if(foundLocCount > Settings.MaxRemoteSigns) {
                interactor.sendMessage(String.format(I18nSupport.getInternationalisedString("Remote Sign - Exceeding maximum allowed"), foundLocCount, Settings.MaxRemoteSigns));
                return false;
            }
        }

        // call the handlers!
        foundTargetSigns.entrySet().forEach(entry -> {
            AbstractMovecraftSign signHandler = entry.getKey();
            for (SignListener.SignWrapper wrapper : entry.getValue()) {
                signHandler.processSignClick(clickType, wrapper, interactor);
            }
        });

        return true;
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        String target = sign.getRaw(1);
        if (target.isBlank()) {
            interactor.sendMessage(ERROR_PREFIX + I18nSupport.getInternationalisedString("Remote Sign - Cannot be blank"));
            return false;
        }

        if (hasForbiddenString(sign)) {
            interactor.sendMessage(I18nSupport.getInternationalisedString("Remote Sign - Forbidden string found"));
            return false;
        }

        return true;
    }

    public static boolean hasForbiddenString(SignListener.SignWrapper wrapper) {
        for (int i = 0; i < wrapper.lines().size(); i++) {
            String s = wrapper.getRaw(i).toLowerCase();
            if(Settings.ForbiddenRemoteSigns.contains(s))
                return true;
        }
        return false;
    }

    // Walks through all strings on the wrapper and if any of the non-header strings match it returns true
    public static boolean matchesDescriptor(final String descriptor, final SignListener.SignWrapper potentialTarget) {
        for (int i = 1; i < potentialTarget.lines().size(); i++) {
            String targetStr = potentialTarget.getRaw(i);
            if (descriptor.equalsIgnoreCase(targetStr)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        return isSignValid(Action.PHYSICAL, sign, event.getPlayer());
    }

    @Override
    protected boolean canPlayerUseSignOn(Entity interactor, @Nullable Craft craft) {
        if (!craft.getCraftProperties().get(PropertyKeys.ALLOW_REMOTE_SIGN)) {
            interactor.sendMessage(ERROR_PREFIX + I18nSupport.getInternationalisedString("Remote Sign - Not allowed on this craft"));
            return false;
        }

        if (super.canPlayerUseSignOn(interactor, craft)) {
            return true;
        }

        return craft.getHitBox().inBounds(interactor.getLocation().getX(), interactor.getLocation().getY(), interactor.getLocation().getZ());
    }
}
