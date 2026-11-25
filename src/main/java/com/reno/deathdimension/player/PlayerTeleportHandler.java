package com.reno.deathdimension.player;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import com.reno.deathdimension.cooldown.CooldownManager;
import com.reno.deathdimension.dimension.DeathDimensionSavedData;
import com.reno.deathdimension.network.DeathTransitionPayload;
import com.reno.deathdimension.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlayerTeleportHandler {
    private static final ResourceLocation DEATH_FLIGHT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "death_flight");

    private PlayerTeleportHandler() {}

    public static boolean isInDeathDimension(ServerPlayer player) {
        return player.level().dimension().equals(ModRegistries.DEATH_DIMENSION);
    }

    public static Vec3 resolveDeathSpawn(MinecraftServer server, PlayerDeathData data) {
        Vec3 spawn = DeathDimensionSavedData.getSpawnPosition(server);
        double targetX = ModConfig.useSharedSpawn ? spawn.x : (data.hasOriginPosition() ? data.getDeathX() : spawn.x);
        double targetY = spawn.y;
        double targetZ = ModConfig.useSharedSpawn ? spawn.z : (data.hasOriginPosition() ? data.getDeathZ() : spawn.z);
        return new Vec3(targetX, targetY, targetZ);
    }

    public static boolean enterDeathDimension(ServerPlayer player, boolean fromDeath) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel deathLevel = DeathDimensionSavedData.getDeathLevel(server);
        if (deathLevel == null) {
            DeathDimension.LOGGER.error("DeathDimension level '{}' is not loaded.", ModRegistries.DEATH_DIMENSION_ID);
            player.sendSystemMessage(Component.translatable("message.deathdimension.dimension_missing").withStyle(ChatFormatting.RED));
            return false;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());

        if (!data.hasSavedAbilities()) {
            data.saveAbilities(player);
        }

        boolean wasAlreadyInDeathDim = data.isInDeathDimension();
        if (!wasAlreadyInDeathDim) {
            if (!fromDeath) {
                data.recordOrigin(
                        player.level().dimension(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        player.getYRot(),
                        player.getXRot()
                );
                CooldownManager.resetCooldown(data);
            } else if (ModConfig.enableCooldown && ModConfig.cooldownDuration > 0L) {
                CooldownManager.startCooldown(data, ModConfig.cooldownDuration);
            } else {
                CooldownManager.resetCooldown(data);
            }
        }

        data.setInDeathDimension(true);
        data.setPendingDeathTeleport(false);
        if (fromDeath) {
            data.setAutoReturnEligible(true);
        } else if (!wasAlreadyInDeathDim) {
            data.setAutoReturnEligible(false);
        }

        Vec3 target = resolveDeathSpawn(server, data);
        if (!isInDeathDimension(player)) {
            safeTeleport(player, data, deathLevel, target.x, target.y, target.z, player.getYRot(), 0.0f);
        }
        applyDeathAbilities(player);

        if (fromDeath) {
            if (ModConfig.enableEyeOpenEffect) {
                PacketDistributor.sendToPlayer(
                        player,
                        new DeathTransitionPayload(player.getId(), DeathTransitionPayload.KIND_ENTER_DEATH, (byte) 0)
                );
            }
            if (ModConfig.enableDeathTitle) {
                sendTitle(
                        player,
                        Component.translatable("title.deathdimension.death").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.translatable("subtitle.deathdimension.death").withStyle(ChatFormatting.GRAY),
                        20, 55, 20
                );
            }
            deathLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 0.85f);
        }

        if (fromDeath && !wasAlreadyInDeathDim && ModConfig.showCooldownMessage) {
            long remaining = CooldownManager.getRemainingSeconds(data);
            if (remaining > 0L) {
                player.sendSystemMessage(
                        Component.translatable("message.deathdimension.entered_cooldown", CooldownManager.formatTime(remaining))
                                .withStyle(ChatFormatting.GRAY)
                );
            } else {
                player.sendSystemMessage(
                        Component.translatable("message.deathdimension.entered_ready")
                                .withStyle(ChatFormatting.GREEN)
                );
            }
        }
        return true;
    }

    public static boolean returnFromDeathDimension(ServerPlayer player, boolean adminOverride) {
        if (!adminOverride && !ModConfig.enableReturnSystem) {
            player.sendSystemMessage(Component.translatable("message.deathdimension.return_disabled").withStyle(ChatFormatting.RED));
            return false;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        if (!isInDeathDimension(player) && !data.isInDeathDimension()) {
            player.sendSystemMessage(Component.translatable("message.deathdimension.not_in_dimension").withStyle(ChatFormatting.RED));
            return false;
        }

        boolean isAdmin = player.hasPermissions(2);
        if (!adminOverride && !CooldownManager.isCooldownComplete(data, isAdmin)) {
            long remaining = CooldownManager.getRemainingSeconds(data);
            player.sendSystemMessage(
                    Component.translatable("message.deathdimension.cooldown_active", CooldownManager.formatTime(remaining))
                            .withStyle(ChatFormatting.YELLOW)
            );
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel targetLevel = resolveReturnLevel(server, data);
        ReturnTarget targetPos = resolveReturnPosition(player, targetLevel, data);

        restoreAbilities(player, data);
        data.clearDeathState();

        safeTeleport(player, data, targetLevel, targetPos.x, targetPos.y, targetPos.z, targetPos.yRot, targetPos.xRot);
        // Brief revive grace period (3s) to prevent instant death-loops at hazardous death sites.
        player.invulnerableTime = Math.max(player.invulnerableTime, 60);
        player.clearFire();
        player.sendSystemMessage(Component.translatable("message.deathdimension.returned").withStyle(ChatFormatting.GREEN));

        if (ModConfig.enableReturnTitle) {
            sendTitle(
                    player,
                    Component.translatable("title.deathdimension.revive").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable("subtitle.deathdimension.revive").withStyle(ChatFormatting.WHITE),
                    10, 45, 20
            );
        }

        ReviveAnimationStyle style = ModConfig.enableReviveAnimation ? ModConfig.reviveAnimationStyle : ReviveAnimationStyle.NONE;
        if (style != ReviveAnimationStyle.NONE) {
            spawnReviveEffects(player, targetLevel, style);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    player,
                    new DeathTransitionPayload(player.getId(), DeathTransitionPayload.KIND_RETURN_TO_LIFE, (byte) style.ordinal())
            );
        }

        return true;
    }

    public static void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private static void spawnReviveEffects(ServerPlayer player, ServerLevel level, ReviveAnimationStyle style) {
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        switch (style) {
            case AWAKENING -> {
                level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 36, 0.65, 0.9, 0.65, 0.12);
                level.sendParticles(ParticleTypes.SOUL, x, y - 0.3, z, 18, 0.55, 0.6, 0.55, 0.05);
                level.sendParticles(ParticleTypes.CLOUD, x, player.getY() + 0.15, z, 14, 0.45, 0.1, 0.45, 0.04);
                level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.15f);
                level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 1.25f);
                player.swing(InteractionHand.MAIN_HAND, true);
            }
            case SPIRAL_ASCENT -> {
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 48, 0.8, 1.2, 0.8, 0.14);
                level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 40, 0.7, 1.0, 0.7, 0.3);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 24, 0.5, 0.9, 0.5, 0.08);
                level.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 1.25f);
                player.swing(InteractionHand.MAIN_HAND, true);
            }
            case SHOCKWAVE -> {
                level.sendParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.FLASH, x, y, z, 2, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.CLOUD, x, player.getY() + 0.3, z, 32, 0.9, 0.25, 0.9, 0.16);
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 32, 0.9, 0.7, 0.9, 0.2);
                level.playSound(null, x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.55f, 1.1f);
                player.swing(InteractionHand.MAIN_HAND, true);
            }
            case GENTLE_DRIFT -> {
                level.sendParticles(ParticleTypes.SOUL, x, y, z, 20, 0.6, 0.8, 0.6, 0.03);
                level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 14, 0.6, 0.8, 0.6, 0.03);
                level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.75f, 1.1f);
            }
            case NONE -> {}
        }
    }

    public static void ensureDeathFlightAttribute(ServerPlayer player) {
        AttributeInstance flightAttr = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flightAttr != null && !flightAttr.hasModifier(DEATH_FLIGHT_MODIFIER_ID)) {
            flightAttr.addPermanentModifier(new AttributeModifier(
                    DEATH_FLIGHT_MODIFIER_ID,
                    1.0,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    public static void applyDeathAbilities(ServerPlayer player) {
        if (!ModConfig.enableFreeMovement) {
            return;
        }
        ensureDeathFlightAttribute(player);
        player.getAbilities().flying = true;
        player.fallDistance = 0.0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.onUpdateAbilities();
    }

    public static void restoreAbilities(ServerPlayer player, PlayerDeathData data) {
        AttributeInstance flightAttr = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flightAttr != null && flightAttr.hasModifier(DEATH_FLIGHT_MODIFIER_ID)) {
            flightAttr.removeModifier(DEATH_FLIGHT_MODIFIER_ID);
        }

        if (!ModConfig.restorePlayerAbilities) {
            data.clearSavedAbilities();
            return;
        }

        Abilities abilities = player.getAbilities();
        if (player.isCreative() || player.isSpectator()) {
            if (player.isSpectator()) {
                abilities.flying = true;
            }
        } else if (data.hasSavedAbilities()) {
            abilities.flying = player.mayFly() && data.isSavedFlying();
            abilities.invulnerable = data.isSavedInvulnerable();
        } else {
            abilities.flying = player.mayFly() && abilities.flying;
            abilities.invulnerable = false;
        }

        player.fallDistance = 0.0f;
        player.onUpdateAbilities();
        data.clearSavedAbilities();
    }

    public static void safeTeleport(ServerPlayer player, PlayerDeathData data, ServerLevel level,
                                    double x, double y, double z, float yRot, float xRot) {
        data.setInternalTeleport(true);
        try {
            player.fallDistance = 0.0f;
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(level, x, y, z, yRot, xRot);
        } finally {
            data.setInternalTeleport(false);
        }
    }

    private static ServerLevel resolveReturnLevel(MinecraftServer server, PlayerDeathData data) {
        if (ModConfig.returnToOriginalDimension && data.hasOriginPosition()) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(data.getOriginDimension());
            if (dimLoc != null && !dimLoc.equals(ModRegistries.DEATH_DIMENSION_ID)) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimLoc);
                ServerLevel originLevel = server.getLevel(key);
                if (originLevel != null) {
                    return originLevel;
                }
            }
        }
        return server.overworld();
    }

    private static ReturnTarget resolveReturnPosition(ServerPlayer player, ServerLevel targetLevel, PlayerDeathData data) {
        boolean wantsDeathPos = ModConfig.returnToOriginalPosition && ModConfig.returnToDeathLocation && data.hasOriginPosition();
        boolean matchesOriginDim = targetLevel.dimension().location().toString().equals(data.getOriginDimension());

        if (wantsDeathPos && matchesOriginDim) {
            double y = data.getOriginY();
            // Prevent returning directly into the void if the player died below min build height.
            if (y >= targetLevel.getMinBuildHeight()) {
                return new ReturnTarget(data.getOriginX(), y, data.getOriginZ(), data.getOriginYRot(), data.getOriginXRot());
            }
        }

        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnPos != null && player.getRespawnDimension().equals(targetLevel.dimension())) {
            return new ReturnTarget(
                    respawnPos.getX() + 0.5,
                    respawnPos.getY() + 0.1,
                    respawnPos.getZ() + 0.5,
                    player.getRespawnAngle(),
                    0.0f
            );
        }

        BlockPos sharedSpawn = targetLevel.getSharedSpawnPos();
        return new ReturnTarget(
                sharedSpawn.getX() + 0.5,
                sharedSpawn.getY() + 0.1,
                sharedSpawn.getZ() + 0.5,
                targetLevel.getSharedSpawnAngle(),
                0.0f
        );
    }

    private record ReturnTarget(double x, double y, double z, float yRot, float xRot) {}
}
