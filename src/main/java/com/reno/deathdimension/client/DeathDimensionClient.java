package com.reno.deathdimension.client;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.network.ConfigSyncPayload;
import com.reno.deathdimension.network.DeathTransitionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = DeathDimension.MODID, dist = Dist.CLIENT)
public final class DeathDimensionClient {
    public DeathDimensionClient(IEventBus modEventBus, ModContainer modContainer) {
        DeathTransitionPayload.setClientHandler(DeathDimensionClientEvents::handleTransitionPayload);
        ConfigSyncPayload.setClientHandler(DeathDimensionClient::handleConfigSync);

        modEventBus.addListener(TransitionOverlayLayer::registerGuiLayers);
        NeoForge.EVENT_BUS.register(new DeathDimensionClientEvents());

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> DeathDimensionConfigScreen.createFromModList(parent)
        );
    }

    private static void handleConfigSync(ConfigSyncPayload payload) {
        if (!payload.openGui()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new DeathDimensionConfigScreen(mc.screen, payload.snapshot()));
    }
}
