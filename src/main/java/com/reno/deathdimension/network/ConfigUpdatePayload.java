package com.reno.deathdimension.network;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigUpdatePayload(ModConfig.ConfigSnapshot snapshot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConfigUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "config_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> payload.snapshot.write(buf),
            buf -> new ConfigUpdatePayload(ModConfig.ConfigSnapshot.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handleOnServer(ConfigUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(
                        Component.translatable("message.deathdimension.config_op_only").withStyle(ChatFormatting.RED)
                );
                return;
            }

            ModConfig.applySnapshot(payload.snapshot(), true);
            PacketDistributor.sendToAllPlayers(new ConfigSyncPayload(ModConfig.captureSnapshot(), false));
            player.sendSystemMessage(
                    Component.translatable("message.deathdimension.config_saved").withStyle(ChatFormatting.GREEN)
            );
        });
    }
}
