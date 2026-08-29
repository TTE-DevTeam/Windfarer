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

package net.countercraft.movecraft;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.commands.*;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.ChunkManager;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.controller.directControl.DirectControlController;
import net.countercraft.movecraft.craft.controller.rotation.DefaultRotationController;
import net.countercraft.movecraft.craft.type.ConfiguredSound;
import net.countercraft.movecraft.craft.type.RequiredBlockEntry;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.craft.type.property.NamespacedKeyToDoubleProperty;
import net.countercraft.movecraft.features.contacts.ContactsCommand;
import net.countercraft.movecraft.features.contacts.ContactsManager;
import net.countercraft.movecraft.features.contacts.ContactsSign;
import net.countercraft.movecraft.features.contacts.IgnoreContactCommand;
import net.countercraft.movecraft.features.directControl.slot.BlockInteractDirectControlSlot;
import net.countercraft.movecraft.features.directControl.slot.CommandDirectControlSlot;
import net.countercraft.movecraft.features.directControl.slot.DefaultDirectControlSlot;
import net.countercraft.movecraft.features.fading.WreckManager;
import net.countercraft.movecraft.features.status.StatusManager;
import net.countercraft.movecraft.features.status.StatusSign;
import net.countercraft.movecraft.listener.*;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.mapUpdater.MapUpdateManager;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.sign.*;
import net.countercraft.movecraft.util.BukkitTeleport;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class Movecraft extends JavaPlugin {
    private static Movecraft instance;

    private Logger logger;
    private boolean shuttingDown;
    private WorldHandler worldHandler;
    private NMSHelper nmsHelper;
    private SmoothTeleport smoothTeleport;
    private AsyncManager asyncManager;
    private WreckManager wreckManager;

    public static synchronized Movecraft getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
    }

    @Override
    public void onEnable() {
        // FIRST: Register config serialization!
        ConfigurationSerialization.registerClass(ConfiguredSound.class, "Movecraft_ConfiguredSound");
        ConfigurationSerialization.registerClass(RequiredBlockEntry.class, "Movecraft_RequiredBlockEntry");
        ConfigurationSerialization.registerClass(NamespacedKeyToDoubleProperty.class, "Movecraft_NamespacedKeyToDoubleProperty");
        ConfigurationSerialization.registerClass(DefaultRotationController.class, "Movecraft_DefaultRotationController");
        ConfigurationSerialization.registerClass(DirectControlController.class, "Movecraft_DirectControlController");
        ConfigurationSerialization.registerClass(DefaultDirectControlSlot.class, "Movecraft_DefaultDirectControlSlot");
        ConfigurationSerialization.registerClass(CommandDirectControlSlot.class, "Movecraft_CommandDirectControlSlot");
        ConfigurationSerialization.registerClass(BlockInteractDirectControlSlot.class, "Movecraft_BlockInteractionDirectControlSlot");

        // Read in config
        Settings.LOCALE = getConfig().getString("Locale");
        Settings.Debug = getConfig().getBoolean("Debug", false);
        Settings.DisableNMSCompatibilityCheck = getConfig().getBoolean("IReallyKnowWhatIAmDoing", false);
        Settings.DisableSpillProtection = getConfig().getBoolean("DisableSpillProtection", false);
        Settings.DisableIceForm = getConfig().getBoolean("DisableIceForm", true);
        Settings.ReleaseOnDeath = getConfig().getBoolean("ReleaseOnDeath", false);
        Settings.ManOverboardCooldown = getConfig().getInt("ManoverboardCooldown", 30);
        Settings.suppressRedstoneEventOnMovingCrafts = getConfig().getBoolean("SuppressRedstoneEventsOnMovingCrafts", true);

        Settings.displayBlockLists = getConfig().getBoolean("DisplayBlockLists", false);
        Settings.displayBlockListsAtlasName = getConfig().getString("DisplayBlockListsAtlasName", "minecraft:blocks");
        Settings.displayBlockListsAtlasPrefix = getConfig().getString("DisplayBlockListsAtlasPrefix", "block/");

        Settings.maxElapsedTimeForWorldChanges = getConfig().getLong("MaxElapsedTimeForWorldChanges", Settings.maxElapsedTimeForWorldChanges);
        Settings.maxElapsedTimeForSyncTaskProcessing = getConfig().getLong("MaxElapsedTimeForSyncTasks", Settings.maxElapsedTimeForSyncTaskProcessing);

        String[] localisations = {"en", "cz", "nl", "fr"};
        for (String s : localisations) {
            if (!new File(getDataFolder()
                    + "/localisation/movecraftlang_" + s + ".properties").exists()) {
                saveResource("localisation/movecraftlang_" + s + ".properties", false);
            }
        }
        I18nSupport.init();


        // if the PilotTool is specified in the config.yml file, use it
        String pilotTool = getConfig().getString("PilotTool");
        if (pilotTool != null) {
            Material material = Material.getMaterial(pilotTool);
            if (material != null) {
                logger.info("Recognized PilotTool setting of: " + pilotTool);
                Settings.PilotTool = material;
            }
            else {
                logger.info("No PilotTool setting, using default of stick");
            }
        }
        else {
            logger.info("No PilotTool setting, using default of stick");
        }

        initializeNMSHandlers();


        Settings.SinkCheckTicks = getConfig().getDouble("SinkCheckTicks", 100.0);
        Settings.ManOverboardTimeout = getConfig().getInt("ManOverboardTimeout", 30);
        Settings.ManOverboardDistSquared = Math.pow(getConfig().getDouble("ManOverboardDistance", 1000), 2);
        Settings.SilhouetteViewDistance = getConfig().getInt("SilhouetteViewDistance", 200);
        Settings.SilhouetteBlockCount = getConfig().getInt("SilhouetteBlockCount", 20);
        Settings.ProtectPilotedCrafts = getConfig().getBoolean("ProtectPilotedCrafts", false);
        Settings.MaxRemoteSigns = getConfig().getInt("MaxRemoteSigns", -1);
        Settings.CraftsUseNetherPortals = getConfig().getBoolean("CraftsUseNetherPortals", false);
        Settings.RequireCreatePerm = getConfig().getBoolean("RequireCreatePerm", false);
        Settings.RequireNamePerm = getConfig().getBoolean("RequireNamePerm", true);
        Settings.FadeWrecksAfter = getConfig().getInt("FadeWrecksAfter", 0);
        Settings.FadeTickCooldown = getConfig().getInt("FadeTickCooldown", 20);
        Settings.FadePercentageOfWreckPerCycle = getConfig().getDouble("FadePercentageOfWreckPerCycle", 10.0);
        Settings.ReleaseCraftOnLogout = getConfig().getBoolean("ReleaseCraftOnLogout", true);
        Settings.ReleaseCraftTimeOutAfterLogOut = getConfig().getLong("ReleaseCraftTimeOutAfterLogOut", 6000);
        if (getConfig().contains("ExtraFadeTimePerBlock")) {
            Map<String, Object> temp = getConfig().getConfigurationSection("ExtraFadeTimePerBlock").getValues(false);
            for (String str : temp.keySet()) {
                Set<Material> materials = Tags.parseMaterials(str);
                for (Material m : materials) {
                    Settings.ExtraFadeTimePerBlock.put(m, (Integer) temp.get(str));
                }
            }
        }

        Settings.ForbiddenRemoteSigns = new HashSet<>();
        for(String s : getConfig().getStringList("ForbiddenRemoteSigns")) {
            Settings.ForbiddenRemoteSigns.add(s.toLowerCase());
        }

        if(shuttingDown && Settings.IGNORE_RESET) {
            logger.severe("Movecraft is incompatible with the reload command. Movecraft has shut down and will restart when the server is restarted.");
            logger.severe("If you wish to use the reload command and Movecraft, you may disable this check inside the config.yml by setting 'safeReload: false'");
            getPluginLoader().disablePlugin(this);
            return;
        }

        // Startup procedure
        boolean datapackInitialized = isDatapackEnabled() || initializeDatapack();
        asyncManager = new AsyncManager();
        // Register the listener first, otherwise our tasks wont work!
        getServer().getPluginManager().registerEvents(new RunnableRegistrationListener(), this);
        asyncManager.runTaskTimer(this, 0, 1);
        MapUpdateManager.getInstance().runTaskTimer(this, 0, 1);


        CraftManager.initialize(datapackInitialized);
        // TODO: Can this run asynchronously? Probably not
        Bukkit.getScheduler().runTaskTimer(this, WorldManager.INSTANCE::run, 0,1);
        wreckManager = new WreckManager(WorldManager.INSTANCE);

        getServer().getPluginManager().registerEvents(new InteractListener(), this);
        getServer().getPluginManager().registerEvents(new DirectControlInteractListener(), this);

//        getCommand("crafttype").setExecutor(new CraftTypeCommand());

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            PilotCommand.register(commands);
            RotateCommand.register(commands);
            CruiseCommand.register(commands);
            CraftReportCommand.register(commands);
            ToggleDirectControl.register(commands);
            WindfarerCommand.register(commands);
            ManOverboardCommand.register(commands);
            new ScuttleCommand().register(commands);
            new ReleaseCommand().register(commands);
            new CraftInfoCommand().register(commands);
            ContactsCommand.register(commands);
            IgnoreContactCommand.register(commands);
            new TeleportCraftCommand().register(commands);
        });

        // Naming scheme: If it has parameters, append a double colon except if it is a subcraft
        // Parameters follow on the following lines
        getServer().getPluginManager().registerEvents(new BlockListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new ChunkManager(), this);

        // TODO: CHange all thease names to constants in the relevant classes!
        MovecraftSignRegistry.INSTANCE.register("Ascend:", new AscendSign("Ascend:"));
        MovecraftSignRegistry.INSTANCE.register("Cruise:", new CruiseSign("Cruise:"));
        MovecraftSignRegistry.INSTANCE.register("Descend:", new DescendSign("Descend:"));
        MovecraftSignRegistry.INSTANCE.register("[Helm]", new HelmSign());
        MovecraftSignRegistry.INSTANCE.register(HelmSign.PRETTY_HEADER, new HelmSign());
        MovecraftSignRegistry.INSTANCE.register("Move:", new MoveSign());
        MovecraftSignRegistry.INSTANCE.register("Name:", new NameSign());
        MovecraftSignRegistry.INSTANCE.register("Pilot:", new PilotSign());
        MovecraftSignRegistry.INSTANCE.register("RMove:", new RelativeMoveSign());
        MovecraftSignRegistry.INSTANCE.register("Release", new ReleaseSign());
        MovecraftSignRegistry.INSTANCE.register("Remote Sign", new RemoteSign(), "Remote");
        MovecraftSignRegistry.INSTANCE.register("Speed:", new SpeedSign());
        MovecraftSignRegistry.INSTANCE.register("Status:", new StatusSign());
        MovecraftSignRegistry.INSTANCE.register("Contacts:", new ContactsSign());
        MovecraftSignRegistry.INSTANCE.register("Subcraft Rotate", new SubcraftRotateSign(CraftManager.getInstance()::getCraftTypeByName, Movecraft::getInstance), "SC Rotate");
        MovecraftSignRegistry.INSTANCE.register("Teleport:", new TeleportSign());
        MovecraftSignRegistry.INSTANCE.register("Scuttle", new ScuttleSign());
        MovecraftSignRegistry.INSTANCE.register("Helmsman", new HelmsManSign());
        MovecraftSignRegistry.INSTANCE.register("Subcraft Move", new SubcraftMoveSign(CraftManager.getInstance()::getCraftTypeByName, Movecraft::getInstance), true, "SC Move");

        getServer().getPluginManager().registerEvents(new CraftPilotListener(), this);
        getServer().getPluginManager().registerEvents(new CraftReleaseListener(), this);
        getServer().getPluginManager().registerEvents(new SignListener(), this);
        // Moved to compat section!
        //getServer().getPluginManager().registerEvents(new SignListener(), this);

        MovecraftSignRegistry.INSTANCE.registerCraftPilotSigns(CraftManager.getInstance().getTypesafeCraftTypes(), CraftPilotSign::new);

        var contactsManager = new ContactsManager();
        contactsManager.runTaskTimerAsynchronously(this, 0, 20);
        getServer().getPluginManager().registerEvents(contactsManager, this);
        //getServer().getPluginManager().registerEvents(new ContactsSign(), this);
        getServer().getPluginManager().registerEvents(new CraftTypeListener(), this);
        getServer().getPluginManager().registerEvents(new CraftTranslateListener(), this);
        getServer().getPluginManager().registerEvents(new WorldListener(), this);

        var statusManager = new StatusManager();
        statusManager.runTaskTimerAsynchronously(this, 0, 1);
        getServer().getPluginManager().registerEvents(statusManager, this);
        //getServer().getPluginManager().registerEvents(new StatusSign(), this);

        logger.info("[V " + getDescription().getVersion() + "] has been enabled.");
    }

    private void initializeNMSHandlers() {
        String minecraftVersion = getServer().getMinecraftVersion();
        getLogger().info("Loading support for " + minecraftVersion);
        try {
            for (String packageName : WorldHandler.getPackageNames(minecraftVersion)) {
                getLogger().info("Searching for version support classes for package subname " + packageName + "...");
                try {
                    final Class<?> worldHandlerClazz = Class.forName("net.countercraft.movecraft.compat." + packageName + ".IWorldHandler");
                    // Check if we have a NMSHandler class at that location.
                    if (WorldHandler.class.isAssignableFrom(worldHandlerClazz)) { // Make sure it actually implements NMS
                        worldHandler = (WorldHandler) worldHandlerClazz.getConstructor().newInstance(); // Set our handler

                        // Try to setup the smooth teleport handler
                        try {
                            final Class<?> smoothTeleportClazz = Class.forName("net.countercraft.movecraft.support." + packageName + ".ISmoothTeleport");
                            if (SmoothTeleport.class.isAssignableFrom(smoothTeleportClazz)) {
                                smoothTeleport = (SmoothTeleport) smoothTeleportClazz.getConstructor().newInstance();
                            }
                            else {
                                smoothTeleport = new BukkitTeleport(); // Fall back to bukkit teleportation
                                getLogger().warning("Did not find smooth teleport, falling back to bukkit teleportation provider.");
                            }

                            // General NMS helper
                            final Class<?> nmsHelperClazz = Class.forName("net.countercraft.movecraft.support." + packageName + ".INMSHelper");
                            if (NMSHelper.class.isAssignableFrom(nmsHelperClazz)) {
                                nmsHelper = (NMSHelper) nmsHelperClazz.getConstructor().newInstance();
                            }
                            else {
                                nmsHelper = null;
                                getLogger().warning("Did not find NMSHelper, some features may not work!.");
                            }
                        }
                        catch (final ReflectiveOperationException e) {
                            if (Settings.Debug) {
                                e.printStackTrace();
                            }
                            smoothTeleport = new BukkitTeleport(); // Fall back to bukkit teleportation
                            getLogger().warning("Falling back to bukkit teleportation provider.");
                        }
                    }
                } catch(ClassNotFoundException classNotFoundException) {
                    // Ignored, continue to search
                    // Initializing worldhandler worked but somehow no teleport handler was found! So throw the exception further up
                    if (worldHandler != null) {
                        throw classNotFoundException;
                    }
                }

                if (worldHandler != null && smoothTeleport != null) {
                    getLogger().info("Found version support for " + minecraftVersion + "!");
                    break;
                }
            }
            if (worldHandler == null || smoothTeleport == null) {
                getLogger().severe("Could not find support for this version.");
                if (!Settings.DisableNMSCompatibilityCheck) {
                    // Disable ourselves and exit
                    setEnabled(false);
                    return;
                }
                else {
                    // Server owner claims to know what they are doing, warn them of the possible consequences
                    getLogger().severe("WARNING!\n\t"
                            + "Running Movecraft on an incompatible version can corrupt your world and break EVERYTHING!\n\t"
                            + "We provide no support for any issues.");
                }
            }
        }
        catch (final Exception e) {
            e.printStackTrace();
            getLogger().severe("Could not find support for this version.");
            if (!Settings.DisableNMSCompatibilityCheck) {
                // Disable ourselves and exit
                setEnabled(false);
                return;
            }
            else {
                // Server owner claims to know what they are doing, warn them of the possible consequences
                getLogger().severe("WARNING!\n\t"
                        + "Running Movecraft on an incompatible version can corrupt your world and break EVERYTHING!\n\t"
                        + "We provide no support for any issues.");
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        instance = this;
        logger = getLogger();
        saveDefaultConfig();

        TypeSafeCraftType.init();

        if (!this.isDatapackEnabled()) {
            logger.severe("Failed to automatically load windfarer datapack. Check if it exists.");
            this.setEnabled(false);
        }
    }

    private boolean initializeDatapack() {
        // Logic handled in PluginBootstrap now!
        if (!isDatapackEnabled()) {
            logger.severe("Failed to automatically load movecraft datapack. Check if it exists.");
            setEnabled(false);
            return false;
        }
        return true;
    }

    private boolean isDatapackEnabled() {
        Datapack pack = this.getServer().getDatapackManager().getPack(getPluginMeta().getName() + "/provided");
        if (pack != null) {
            if (pack.isEnabled()) {
                return true;
            } else {
                return false;
            }
        }
        logger.severe("Datapack not found!");
        return false;
    }


    public WorldHandler getWorldHandler(){
        return worldHandler;
    }

    public SmoothTeleport getSmoothTeleport() {
        return smoothTeleport;
    }

    public AsyncManager getAsyncManager() {
        return asyncManager;
    }

    public @NotNull WreckManager getWreckManager(){
        return wreckManager;
    }

    public NMSHelper getNMSHelper() {
        return this.nmsHelper;
    }
}
