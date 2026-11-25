package com.reno.deathdimension.network;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

public record ConfigSyncPayload(ModConfig.ConfigSnapshot snapshot, boolean openGui) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                payload.snapshot.write(buf);
                buf.writeBoolean(payload.openGui);
            },
            buf -> new ConfigSyncPayload(ModConfig.ConfigSnapshot.read(buf), buf.readBoolean())
    );

    private static volatile Consumer<ConfigSyncPayload> clientHandler = payload -> {};

    public static void setClientHandler(Consumer<ConfigSyncPayload> handler) {
        clientHandler = handler != null ? handler : payload -> {};
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handleOnClient(ConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ModConfig.applySnapshot(payload.snapshot(), false);
            clientHandler.accept(payload);
        });
    }
}
