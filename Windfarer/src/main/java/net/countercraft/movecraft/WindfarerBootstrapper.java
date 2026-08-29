package net.countercraft.movecraft;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.countercraft.movecraft.commands.*;
import net.countercraft.movecraft.features.contacts.ContactsCommand;
import net.countercraft.movecraft.features.contacts.IgnoreContactCommand;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class WindfarerBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext bootstrapContext) {
        // Initialize datapack
        bootstrapContext.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(
                event -> {
                    try {
                        // Retrieve the URI of the datapack folder.
                        URI uri = this.getClass().getResource("/windfarer_data").toURI();
                        // Discover the pack. The ID is set to "provided", which indicates to
                        // a server owner that your plugin includes this data pack.
                        event.registrar().discoverPack(uri, "provided");
                    } catch (URISyntaxException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        ));

        // Commands
        bootstrapContext.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
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
        new DeleteCraftCommand().register(commands);
    }
}
