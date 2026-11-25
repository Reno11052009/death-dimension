package com.reno.deathdimension;

import com.mojang.logging.LogUtils;
import com.reno.deathdimension.command.DeathDimensionCommands;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.event.DeathDimensionEvents;
import com.reno.deathdimension.network.DeathTransitionPayload;
import com.reno.deathdimension.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DeathDimension.MODID)
public class DeathDimension {
    public static final String MODID = "deathdimension";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DeathDimension(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);

        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
        modEventBus.addListener(ModConfig::onConfigEvent);
        modEventBus.addListener(DeathTransitionPayload::registerPayloads);

        NeoForge.EVENT_BUS.register(new DeathDimensionEvents());
        NeoForge.EVENT_BUS.addListener(DeathDimensionCommands::onRegisterCommands);

        LOGGER.info("DeathDimension initialized.");
    }
}
