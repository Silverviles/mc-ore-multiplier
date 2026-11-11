package com.silverviles.ore_multiplier;

import com.silverviles.ore_multiplier.command.OreConfigCommand;
import com.silverviles.ore_multiplier.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Ore_multiplier implements ModInitializer {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(ConfigManager.get()::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ConfigManager.get().onServerStopping());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                OreConfigCommand.register(dispatcher));
    }
}
