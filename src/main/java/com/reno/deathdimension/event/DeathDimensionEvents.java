package com.reno.deathdimension.event;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReturnMode;
import com.reno.deathdimension.cooldown.CooldownManager;
import com.reno.deathdimension.dimension.DeathDimensionSavedData;
import com.reno.deathdimension.network.ConfigSyncPayload;
import com.reno.deathdimension.player.PlayerDeathData;
import com.reno.deathdimension.player.PlayerTeleportHandler;
import com.reno.deathdimension.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DeathDimensionEvents {

    private static boolean isHardcore(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isHardcore();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !ModConfig.enableMod || !ModConfig.enableDeathTeleport) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || isHardcore(player)) {
            return;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());

        // Only capture origin & pre-death abilities when dying outside DeathDimension,
        // so dying again inside DeathDimension never overwrites origin or resets cooldown.
        if (!data.isInDeathDimension() && !PlayerTeleportHandler.isInDeathDimension(player)) {
            data.recordOrigin(
                    player.level().dimension(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot()
            );
            if (!data.hasSavedAbilities()) {
                data.saveAbilities(player);
            }
        }

        data.setPendingDeathTeleport(true);

        boolean keepInventory = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        if (!keepInventory) {
            if (ModConfig.preserveInventory) {
                data.snapshotInventory(player);
            }
            if (ModConfig.preserveExperience) {
                data.snapshotExperience(player);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!ModConfig.enableMod || !ModConfig.preserveInventory) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
            if (data.hasSavedInventory()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (!ModConfig.enableMod || !ModConfig.preserveExperience) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
            if (data.hasSavedExperience()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        // Runs at LOWEST so NeoForge's AttachmentInternals (NORMAL) has already copied PLAYER_DEATH_DATA.
        PlayerDeathData data = newPlayer.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        data.restoreInventoryAndClear(newPlayer);
        data.restoreExperienceAndClear(newPlayer);
    }

    @SubscribeEvent
    public void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight() || !ModConfig.enableMod || !ModConfig.enableDeathTeleport) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || isHardcore(player)) {
            return;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        if (!data.isPendingDeathTeleport()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerLevel deathLevel = DeathDimensionSavedData.getDeathLevel(server);
        if (deathLevel == null) {
            return;
        }

        Vec3 spawn = PlayerTeleportHandler.resolveDeathSpawn(server, data);
        event.setCopyOriginalSpawnPosition(true);
        event.setDimensionTransition(new DimensionTransition(
                deathLevel,
                spawn,
                Vec3.ZERO,
                player.getYRot(),
                0.0f,
                DimensionTransition.DO_NOTHING
        ));
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !ModConfig.enableMod || !ModConfig.enableDeathTeleport) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || isHardcore(player)) {
            return;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        if (!data.isPendingDeathTeleport()) {
            return;
        }

        data.setPendingDeathTeleport(false);
        PlayerTeleportHandler.enterDeathDimension(player, true);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new ConfigSyncPayload(ModConfig.captureSnapshot(), false));

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        boolean inDeathDim = PlayerTeleportHandler.isInDeathDimension(player);

        // Edge case: disconnected after dying before respawn completed, then rejoined alive.
        if (!inDeathDim && data.isPendingDeathTeleport() && player.isAlive()
                && ModConfig.enableMod && ModConfig.enableDeathTeleport) {
            data.restoreInventoryAndClear(player);
            data.restoreExperienceAndClear(player);
            data.setPendingDeathTeleport(false);
            PlayerTeleportHandler.enterDeathDimension(player, true);
            return;
        }

        if (inDeathDim) {
            data.setInDeathDimension(true);
            player.fallDistance = 0.0f;
            player.setDeltaMovement(Vec3.ZERO);

            if (ModConfig.enableMod) {
                if (ModConfig.preventVoidDamage && player.getY() < player.level().getMinBuildHeight() - 32.0) {
                    if (data.hasDeathDimPosition() && data.getDeathDimY() >= player.level().getMinBuildHeight() - 16.0) {
                        player.teleportTo(data.getDeathDimX(), data.getDeathDimY(), data.getDeathDimZ());
                    } else {
                        rescueFromDeepVoid(player);
                    }
                }
                PlayerTeleportHandler.applyDeathAbilities(player);
            }

            // Refresh cooldown completion based on current server time after offline period.
            long remaining = CooldownManager.getRemainingSeconds(data);
            if (remaining == 0L
                    && ModConfig.enableMod
                    && ModConfig.enableReturnSystem
                    && ModConfig.returnMode == ReturnMode.AUTO
                    && data.isAutoReturnEligible()) {
                PlayerTeleportHandler.returnFromDeathDimension(player, false);
                return;
            }

            if (ModConfig.showCooldownMessage) {
                if (remaining > 0L) {
                    player.sendSystemMessage(
                            Component.translatable("message.deathdimension.rejoin_cooldown", CooldownManager.formatTime(remaining))
                                    .withStyle(ChatFormatting.GRAY)
                    );
                } else {
                    data.setCooldownNotified(true);
                    player.sendSystemMessage(
                            Component.translatable("message.deathdimension.rejoin_ready")
                                    .withStyle(ChatFormatting.GREEN)
                    );
                }
            }
            if (ModConfig.showCooldownActionbar && ModConfig.enableCooldown) {
                Component bar = remaining > 0L
                        ? Component.translatable("actionbar.deathdimension.cooldown", CooldownManager.formatTime(remaining))
                                .withStyle(ChatFormatting.GOLD)
                        : Component.translatable("actionbar.deathdimension.ready")
                                .withStyle(ChatFormatting.GREEN);
                player.displayClientMessage(bar, true);
            }
        } else if (data.isInDeathDimension()) {
            PlayerTeleportHandler.restoreAbilities(player, data);
            data.clearDeathState();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        boolean inDeathDim = PlayerTeleportHandler.isInDeathDimension(player);
        if (!inDeathDim && !data.isInDeathDimension() && !data.isPendingDeathTeleport()) {
            return;
        }

        // Cooldown keeps tracking via wall-clock timestamp; save logout state before PlayerList.save(player).
        data.setLastLogoutEpoch(CooldownManager.nowEpochSeconds());

        if (inDeathDim) {
            data.setInDeathDimension(true);
            if (!data.hasSavedAbilities()) {
                data.saveAbilities(player);
            }
            data.recordDeathDimPosition(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot()
            );

            player.fallDistance = 0.0f;
            player.setDeltaMovement(Vec3.ZERO);
            if (ModConfig.enableMod && ModConfig.enableFreeMovement) {
                PlayerTeleportHandler.ensureDeathFlightAttribute(player);
                player.getAbilities().flying = true;
            }

            long remaining = CooldownManager.getRemainingSeconds(data);
            DeathDimension.LOGGER.info(
                    "Player {} logged out in DeathDimension (remaining cooldown: {}s).",
                    player.getScoreboardName(), remaining
            );
        } else if (data.isPendingDeathTeleport()) {
            DeathDimension.LOGGER.info(
                    "Player {} logged out with pending DeathDimension teleport.",
                    player.getScoreboardName()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        if (data.isInternalTeleport()) {
            return;
        }

        if (event.getFrom().equals(ModRegistries.DEATH_DIMENSION)) {
            PlayerTeleportHandler.restoreAbilities(player, data);
            data.clearDeathState();
        } else if (event.getTo().equals(ModRegistries.DEATH_DIMENSION)) {
            if (!data.hasSavedAbilities()) {
                data.saveAbilities(player);
            }
            data.setInDeathDimension(true);
            if (ModConfig.enableMod) {
                PlayerTeleportHandler.applyDeathAbilities(player);
            }
        }
    }

    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!ModConfig.enableMod || !ModConfig.preventDimensionEscape) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!PlayerTeleportHandler.isInDeathDimension(player) || event.getDimension().equals(ModRegistries.DEATH_DIMENSION)) {
            return;
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        if (data.isInternalTeleport()) {
            return;
        }
        if (ModConfig.allowAdminBypass && player.hasPermissions(2)) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.translatable("message.deathdimension.escape_prevented").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ModConfig.enableMod) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !PlayerTeleportHandler.isInDeathDimension(player)) {
            return;
        }

        if (ModConfig.preventVoidDamage && event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            rescueFromDeepVoid(player);
            return;
        }

        if (ModConfig.enableFreeMovement && event.getSource().is(DamageTypes.FALL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModConfig.enableMod) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !PlayerTeleportHandler.isInDeathDimension(player)) {
            return;
        }

        if (player.tickCount % 20 != 0) {
            return;
        }

        if (ModConfig.preventVoidDamage && player.getY() < player.level().getMinBuildHeight() - 32.0) {
            rescueFromDeepVoid(player);
        }

        if (ModConfig.enableFreeMovement && !player.mayFly()) {
            PlayerTeleportHandler.applyDeathAbilities(player);
        }

        PlayerDeathData data = player.getData(ModRegistries.PLAYER_DEATH_DATA.get());
        long remaining = CooldownManager.getRemainingSeconds(data);

        if (remaining > 0L) {
            if (ModConfig.showCooldownActionbar) {
                player.displayClientMessage(
                        Component.translatable("actionbar.deathdimension.cooldown", CooldownManager.formatTime(remaining))
                                .withStyle(ChatFormatting.GOLD),
                        true
                );
            }
        } else {
            if (ModConfig.enableReturnSystem && ModConfig.returnMode == ReturnMode.AUTO && data.isAutoReturnEligible()) {
                PlayerTeleportHandler.returnFromDeathDimension(player, false);
                return;
            }
            if (!data.isCooldownNotified()) {
                data.setCooldownNotified(true);
                if (ModConfig.showCooldownMessage) {
                    player.sendSystemMessage(
                            Component.translatable("message.deathdimension.cooldown_ready")
                                    .withStyle(ChatFormatting.GREEN)
                    );
                }
            }
            if (ModConfig.showCooldownActionbar && ModConfig.enableCooldown) {
                player.displayClientMessage(
                        Component.translatable("actionbar.deathdimension.ready")
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
            }
        }
    }

    private static void rescueFromDeepVoid(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Vec3 spawn = DeathDimensionSavedData.getSpawnPosition(server);
        player.fallDistance = 0.0f;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(player.getX(), spawn.y, player.getZ());
        if (ModConfig.enableFreeMovement) {
            PlayerTeleportHandler.applyDeathAbilities(player);
        }
    }
}
