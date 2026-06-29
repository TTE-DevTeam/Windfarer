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

package net.countercraft.movecraft.async;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.features.fuel.FuelDataTags;
import net.countercraft.movecraft.features.fuel.FuelUtil;

public abstract class FuelAwareAsyncTask extends AsyncTask {

    protected FuelAwareAsyncTask(Craft c) {
        super(c);
    }

    // DONE: Move into it's own async task
    // DONE: Check against craft datatag
    protected boolean checkFuel() {
        final Craft craft = this.getCraft();
        if (!FuelUtil.doesBurnFuel(craft)) {
            return true;
        }
        // Workaround for stick movement being treated as passive
        // If we are not cruising, we are either sinking or stick-moving
        // Or if we only consume on movement, we will consume fuel
        boolean stick = !craft.getCruising();
        if (stick || FuelUtil.onlyBurnsFuelOnMovement(craft)) {
            FuelBurnRunnable.runFuelBurnLogic(craft, stick);
        }
        return this.getCraft().getDataTag(FuelDataTags.IS_FUELED);
    }
}
