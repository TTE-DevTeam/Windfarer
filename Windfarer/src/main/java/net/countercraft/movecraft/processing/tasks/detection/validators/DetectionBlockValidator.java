package net.countercraft.movecraft.processing.tasks.detection.validators;

import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.RequiredBlockEntry;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class DetectionBlockValidator extends AbstractBlockConstraintValidator {
    @Override
    protected Collection<RequiredBlockEntry> getRelevantConstraintSet(TypeSafeCraftType type) {
        return type.get(PropertyKeys.DETECTION_BLOCKS);
    }

    @Override
    protected Component getFailMessage(RequiredBlockEntry.DetectionResult result, @NotNull String errorMessage, RequiredBlockEntry failedCondition) {
        Component failMessage = Component.empty();
        // TODO: Switch to purely components!
        switch (result) {
            case NOT_ENOUGH:
                failMessage = failMessage.append(Component.text(I18nSupport.getInternationalisedString("Detection - Not enough detectionblock")));
                break;
            case TOO_MUCH:
                failMessage = failMessage.append(Component.text(I18nSupport.getInternationalisedString("Detection - Too much detectionblock")));
                break;
            default:
                break;
        }
        failMessage = failMessage.append(Component.text(": [")).append(failedCondition.getDisplayNameComponent()).append(Component.text("] ")).append(Component.text(errorMessage).color(TextColor.color(1.0F, 0.0F, 0.0F)));
        return failMessage;
    }

}
