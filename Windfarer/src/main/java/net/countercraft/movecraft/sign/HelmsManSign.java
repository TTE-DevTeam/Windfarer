package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.controller.directControl.HelmsManManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.jetbrains.annotations.Nullable;

public class HelmsManSign extends AbstractInformationSign {
    @Override
    protected @Nullable Component getUpdateString(int lineIndex, Component oldData, Craft craft) {
        if (lineIndex == 1 && (craft instanceof PlayerCraft playerCraft)) {
            final Player helmsman = HelmsManManager.getHelmsMan(playerCraft);
            if (helmsman == null) {
                return EMPTY;
            } else {
                return helmsman.displayName();
            }
        } else {
            return oldData;
        }
    }

    @Override
    protected @Nullable Component getDefaultString(int lineIndex, Component oldComponent) {
        if (lineIndex == 1) {
            return EMPTY;
        }
        return null;
    }

    @Override
    protected void performUpdate(Component[] newComponents, SignListener.SignWrapper sign, REFRESH_CAUSE refreshCause) {
        for (int i = 0; i < newComponents.length; i++) {
            Component newComp = newComponents[i];
            if (newComp != null) {
                sign.line(i, newComp);
            }
        }
        if (refreshCause != REFRESH_CAUSE.SIGN_MOVED_BY_CRAFT && sign.block() != null) {
            sign.block().update(true);
        }
    }

    @Override
    protected void onCraftIsBusy(Player player, Craft craft) {

    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Player player) {
        boolean resultTmp = false;
        if (craft instanceof PlayerCraft playerCraft) {
            final Player currentHelmsMan = HelmsManManager.getHelmsMan(playerCraft);
            if (currentHelmsMan == player) {
                HelmsManManager.removeActivePilot(playerCraft);
                playerCraft.getAudience().sendMessage(Component.text(String.format(I18nSupport.getInternationalisedString("Crew - Helmsman left post"), player.getName())));
            }
            else if (HelmsManManager.setActivePilot(player, playerCraft)) {
                playerCraft.getAudience().sendMessage(Component.text(String.format(I18nSupport.getInternationalisedString("Crew - Helmsman took post"), player.getName())));
                resultTmp = true;
            } else {
                player.sendMessage(Component.text(I18nSupport.getInternationalisedString("Crew - Helmsman post taken")));
            }
        }

        return super.internalProcessSignWithCraft(clickType, sign, craft, player) || resultTmp;
    }

}
