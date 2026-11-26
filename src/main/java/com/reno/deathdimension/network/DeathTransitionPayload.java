package com.reno.deathdimension.network;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

public record DeathTransitionPayload(int entityId, byte transitionKind, byte animationStyle) implements CustomPacketPayload {
    public static final byte KIND_ENTER_DEATH = 1;
    public static final byte KIND_RETURN_TO_LIFE = 2;

    public static final CustomPacketPayload.Type<DeathTransitionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "death_transition"));

    public static final StreamCodec<ByteBuf, DeathTransitionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DeathTransitionPayload::entityId,
            ByteBufCodecs.BYTE, DeathTransitionPayload::transitionKind,
            ByteBufCodecs.BYTE, DeathTransitionPayload::animationStyle,
            DeathTransitionPayload::new
    );

    private static volatile Consumer<DeathTransitionPayload> clientHandler = payload -> {};

    public ReviveAnimationStyle style() {
        return ReviveAnimationStyle.fromOrdinal(animationStyle);
    }

    public static void setClientHandler(Consumer<DeathTransitionPayload> handler) {
        clientHandler = handler != null ? handler : payload -> {};
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("7").optional();
        registrar.playToClient(TYPE, STREAM_CODEC, DeathTransitionPayload::handleOnClient);
        registrar.playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC, ConfigSyncPayload::handleOnClient);
        registrar.playToServer(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.STREAM_CODEC, ConfigUpdatePayload::handleOnServer);
    }

    private static void handleOnClient(DeathTransitionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }
}
