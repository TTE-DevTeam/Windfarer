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

package net.countercraft.movecraft.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Settings {
    public static boolean IGNORE_RESET = false;
    public static boolean Debug = false;
    public static boolean DisableNMSCompatibilityCheck = false;
    public static String LOCALE;
    public static Material PilotTool = Material.STICK;
    public static int SilhouetteViewDistance = 200;
    public static int SilhouetteBlockCount = 20;
    public static double SinkCheckTicks = 100.0;
    public static boolean ProtectPilotedCrafts = false;
    public static boolean DisableSpillProtection = false;
    public static boolean DisableIceForm = true;
    public static boolean RequireCreatePerm = false;
    public static boolean RequireNamePerm = false;
    public static boolean ReleaseCraftOnLogout = true;
    public static long ReleaseCraftTimeOutAfterLogOut = 6000; //Ticks, this means 5 minutes
    public static int FadeWrecksAfter = 0;
    public static int FadeTickCooldown = 20;
    public static double FadePercentageOfWreckPerCycle = 10.0;
    public static Map<Material, Integer> ExtraFadeTimePerBlock = new HashMap<>();
    public static int ManOverboardTimeout = 60;
    public static int ManOverboardCooldown = 30;
    public static double ManOverboardDistSquared = 1000000;
    public static int MaxRemoteSigns = -1;
    public static boolean CraftsUseNetherPortals = false;
    public static HashSet<String> ForbiddenRemoteSigns;
    public static boolean ReleaseOnDeath = false;
    public static boolean displayBlockLists = false;
    public static boolean suppressRedstoneEventOnMovingCrafts = true;
    public static String displayBlockListsAtlasName = "minecraft:blocks";
    public static String displayBlockListsAtlasPrefix = "block/";
    public static long maxElapsedTimeForWorldChanges = 50000;
    public static long maxElapsedTimeForSyncTaskProcessing = 50000;
    public static Set<NamespacedKey> alwaysMovedEntities = Set.of(
            EntityType.TNT.getKey(),
            EntityType.ARMOR_STAND.getKey(),
            EntityType.PAINTING.getKey(),
            EntityType.ITEM_FRAME.getKey(),
            EntityType.ITEM_DISPLAY.getKey(),
            EntityType.TEXT_DISPLAY.getKey(),
            EntityType.MARKER.getKey(),
            EntityType.INTERACTION.getKey(),
            EntityType.GLOW_ITEM_FRAME.getKey(),
            EntityType.MINECART.getKey(),
            EntityType.TNT_MINECART.getKey(),
            EntityType.CHEST_MINECART.getKey(),
            EntityType.FURNACE_MINECART.getKey(),
            EntityType.HOPPER_MINECART.getKey()
    );

}
