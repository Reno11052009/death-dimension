package com.reno.deathdimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import com.reno.deathdimension.network.DeathTransitionPayload;
import com.reno.deathdimension.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class DeathDimensionClientEvents {
    private static final long AWAIT_EYE_OPEN_TIMEOUT_NS = 3_000_000_000L;
    private static final long EYE_OPEN_DURATION_NS = 2_800_000_000L;
    private static final long REVIVE_ANIM_DURATION_NS = 1_800_000_000L;

    private record ActiveRevive(long startNs, ReviveAnimationStyle style) {}

    private static volatile boolean serverHasMod = false;
    private static volatile long awaitingEyeOpenStartNs = -1L;
    private static volatile long eyeOpenStartNs = -1L;
    private static volatile ActiveRevive localRevive = null;

    private static final Map<Integer, ActiveRevive> revivingPlayers = new HashMap<>();
    private static final Set<Integer> pushedPlayersThisFrame = new HashSet<>();

    public static void markServerHasMod() {
        serverHasMod = true;
    }

    public static boolean hasServerMod() {
        return serverHasMod;
    }

    public static float clampPitch(float pitch) {
        return Math.max(-89.0f, Math.min(89.0f, pitch));
    }

    public static void handleTransitionPayload(DeathTransitionPayload payload) {
        long now = System.nanoTime();
        if (payload.transitionKind() == DeathTransitionPayload.KIND_ENTER_DEATH) {
            awaitingEyeOpenStartNs = -1L;
            if (ModConfig.enableEyeOpenEffect) {
                eyeOpenStartNs = now;
            }
            return;
        }

        if (payload.transitionKind() == DeathTransitionPayload.KIND_RETURN_TO_LIFE && ModConfig.enableReviveAnimation) {
            ReviveAnimationStyle style = payload.style();
            if (style == ReviveAnimationStyle.NONE) {
                return;
            }
            ActiveRevive active = new ActiveRevive(now, style);
            revivingPlayers.put(payload.entityId(), active);
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null && localPlayer.getId() == payload.entityId()) {
                awaitingEyeOpenStartNs = -1L;
                eyeOpenStartNs = -1L;
                localRevive = active;
            }
        }
    }

    public static boolean isAwaitingEyeOpen() {
        long start = awaitingEyeOpenStartNs;
        if (start < 0L) {
            return false;
        }
        if (System.nanoTime() - start > AWAIT_EYE_OPEN_TIMEOUT_NS) {
            awaitingEyeOpenStartNs = -1L;
            return false;
        }
        return true;
    }

    public static double getEyeOpenProgress() {
        long start = eyeOpenStartNs;
        if (start < 0L) {
            return -1.0;
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed >= EYE_OPEN_DURATION_NS) {
            eyeOpenStartNs = -1L;
            return -1.0;
        }
        return (double) elapsed / (double) EYE_OPEN_DURATION_NS;
    }

    public static double getLocalReviveProgress() {
        ActiveRevive current = localRevive;
        if (current == null) {
            return -1.0;
        }
        long elapsed = System.nanoTime() - current.startNs();
        if (elapsed >= REVIVE_ANIM_DURATION_NS) {
            localRevive = null;
            return -1.0;
        }
        return (double) elapsed / (double) REVIVE_ANIM_DURATION_NS;
    }

    public static ReviveAnimationStyle getLocalReviveStyle() {
        ActiveRevive current = localRevive;
        return current != null ? current.style() : ReviveAnimationStyle.NONE;
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        serverHasMod = false;
        awaitingEyeOpenStartNs = -1L;
        eyeOpenStartNs = -1L;
        localRevive = null;
        revivingPlayers.clear();
        pushedPlayersThisFrame.clear();
        // Restore local config so a previous server's ConfigSyncPayload never leaks into another session.
        ModConfig.reloadFromDisk();
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen)) {
            return;
        }
        if (!serverHasMod || !ModConfig.enableMod || !ModConfig.enableDeathTeleport || !ModConfig.skipDeathScreen) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.getLevelData().isHardcore()) {
            return;
        }

        event.setCanceled(true);
        if (ModConfig.enableEyeOpenEffect) {
            awaitingEyeOpenStartNs = System.nanoTime();
        }
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.respawn();
            }
        });
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) {
            return;
        }

        if (ModConfig.enableMod && ModConfig.enableVoidParticles
                && level.dimension().equals(ModRegistries.DEATH_DIMENSION)) {
            spawnVoidAmbientParticles(level, player);
        }

        if (!revivingPlayers.isEmpty()) {
            long now = System.nanoTime();
            RandomSource random = level.random;
            Iterator<Map.Entry<Integer, ActiveRevive>> it = revivingPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, ActiveRevive> entry = it.next();
                ActiveRevive active = entry.getValue();
                long elapsed = now - active.startNs();
                if (elapsed >= REVIVE_ANIM_DURATION_NS) {
                    it.remove();
                    continue;
                }
                Entity entity = level.getEntity(entry.getKey());
                if (entity != null) {
                    double progress = (double) elapsed / (double) REVIVE_ANIM_DURATION_NS;
                    spawnReviveTrailParticles(level, entity, active.style(), progress, random);
                }
            }
        }
    }

    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double eyeProgress = getEyeOpenProgress();
        if (eyeProgress >= 0.0 && eyeProgress < 1.0) {
            double closed = 1.0 - TransitionOverlayLayer.eyeOpenFactor(eyeProgress);
            event.setPitch(clampPitch(event.getPitch() + (float) (14.0 * closed)));
        }

        double reviveProgress = getLocalReviveProgress();
        if (reviveProgress >= 0.0 && reviveProgress < 1.0) {
            ReviveAnimationStyle style = getLocalReviveStyle();
            double inv = 1.0 - TransitionOverlayLayer.easeOutCubic(reviveProgress);
            switch (style) {
                case AWAKENING -> {
                    event.setPitch(clampPitch(event.getPitch() + (float) (26.0 * inv)));
                    event.setRoll(event.getRoll() + (float) (11.0 * Math.sin(Math.PI * reviveProgress) * (1.0 - reviveProgress)));
                }
                case SPIRAL_ASCENT -> {
                    event.setPitch(clampPitch(event.getPitch() - (float) (18.0 * inv)));
                    event.setRoll(event.getRoll() + (float) (16.0 * Math.sin(2.0 * Math.PI * reviveProgress) * (1.0 - reviveProgress)));
                }
                case SHOCKWAVE -> {
                    double damp = (1.0 - reviveProgress) * (1.0 - reviveProgress);
                    float shakePitch = (float) (6.0 * Math.sin(28.0 * reviveProgress) * damp);
                    float shakeRoll = (float) (7.5 * Math.cos(24.0 * reviveProgress) * damp);
                    event.setPitch(clampPitch(event.getPitch() + shakePitch));
                    event.setRoll(event.getRoll() + shakeRoll);
                }
                case GENTLE_DRIFT -> event.setPitch(clampPitch(event.getPitch() + (float) (8.0 * inv)));
                case NONE -> {}
            }
        }
    }

    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        double eyeProgress = getEyeOpenProgress();
        if (eyeProgress >= 0.0 && eyeProgress < 1.0) {
            double closed = 1.0 - TransitionOverlayLayer.eyeOpenFactor(eyeProgress);
            event.setFOV(event.getFOV() - 7.0 * closed);
        }

        double reviveProgress = getLocalReviveProgress();
        if (reviveProgress >= 0.0 && reviveProgress < 1.0) {
            ReviveAnimationStyle style = getLocalReviveStyle();
            switch (style) {
                case AWAKENING -> {
                    double pulse = 13.0 * Math.sin(Math.PI * reviveProgress) * (1.0 - 0.35 * reviveProgress);
                    event.setFOV(event.getFOV() + pulse);
                }
                case SPIRAL_ASCENT -> {
                    double inv = 1.0 - TransitionOverlayLayer.easeOutCubic(reviveProgress);
                    event.setFOV(event.getFOV() - 10.0 * inv);
                }
                case SHOCKWAVE -> {
                    double punch = 22.0 * Math.sin(Math.PI * Math.min(1.0, reviveProgress * 1.6)) * (1.0 - reviveProgress);
                    event.setFOV(event.getFOV() + punch);
                }
                case GENTLE_DRIFT -> event.setFOV(event.getFOV() + 5.0 * Math.sin(Math.PI * reviveProgress));
                case NONE -> {}
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ModConfig.enableReviveAnimation) {
            return;
        }
        int id = event.getEntity().getId();
        ActiveRevive active = revivingPlayers.get(id);
        if (active == null || active.style() == ReviveAnimationStyle.NONE) {
            return;
        }
        long elapsed = System.nanoTime() - active.startNs();
        if (elapsed >= REVIVE_ANIM_DURATION_NS) {
            revivingPlayers.remove(id);
            return;
        }

        double progress = (double) elapsed / (double) REVIVE_ANIM_DURATION_NS;
        double inv = 1.0 - TransitionOverlayLayer.easeOutCubic(progress);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        pushedPlayersThisFrame.add(id);

        switch (active.style()) {
            case AWAKENING -> {
                float sway = (float) (8.0 * Math.sin(Math.PI * progress) * inv);
                float scalePulse = 1.0f + (float) (0.05 * Math.sin(Math.PI * progress));
                poseStack.translate(0.0, -0.28 * inv, 0.0);
                poseStack.mulPose(Axis.XP.rotationDegrees((float) (-52.0 * inv)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(sway));
                poseStack.scale(scalePulse, scalePulse, scalePulse);
            }
            case SPIRAL_ASCENT -> {
                float spin = (float) (360.0 * inv * inv);
                float tilt = (float) (14.0 * Math.sin(Math.PI * progress));
                poseStack.translate(0.0, 0.65 * inv, 0.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(spin));
                poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
            }
            case SHOCKWAVE -> {
                double pop = Math.sin(Math.PI * Math.min(1.0, progress * 1.4)) * (1.0 - progress * 0.5);
                float scaleBurst = 1.0f + (float) (0.12 * pop);
                poseStack.translate(0.0, 0.35 * pop, 0.0);
                poseStack.mulPose(Axis.XP.rotationDegrees((float) (22.0 * inv)));
                poseStack.scale(scaleBurst, scaleBurst, scaleBurst);
            }
            case GENTLE_DRIFT -> {
                poseStack.translate(0.0, 0.25 * inv, 0.0);
                float calmScale = 1.0f + (float) (0.03 * Math.sin(Math.PI * progress));
                poseStack.scale(calmScale, calmScale, calmScale);
            }
            case NONE -> {}
        }
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        int id = event.getEntity().getId();
        if (pushedPlayersThisFrame.remove(id)) {
            event.getPoseStack().popPose();
        }
    }

    private static void spawnReviveTrailParticles(ClientLevel level, Entity entity, ReviveAnimationStyle style,
                                                  double progress, RandomSource random) {
        switch (style) {
            case AWAKENING -> {
                if (random.nextFloat() < 0.75f) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double r = 0.45 + random.nextDouble() * 0.45;
                    double px = entity.getX() + Math.cos(angle) * r;
                    double py = entity.getY() + 0.15 + random.nextDouble() * 1.6;
                    double pz = entity.getZ() + Math.sin(angle) * r;
                    level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0.0, 0.04, 0.0);
                    if (random.nextFloat() < 0.35f) {
                        level.addParticle(ParticleTypes.SOUL, px, py, pz, 0.0, 0.03, 0.0);
                    }
                }
            }
            case SPIRAL_ASCENT -> {
                double angle = progress * Math.PI * 10.0;
                double r = 0.75 * (1.0 - progress * 0.4);
                double py = entity.getY() + progress * 2.0;
                level.addParticle(ParticleTypes.END_ROD,
                        entity.getX() + Math.cos(angle) * r, py, entity.getZ() + Math.sin(angle) * r,
                        0.0, 0.02, 0.0);
                level.addParticle(ParticleTypes.ENCHANT,
                        entity.getX() - Math.cos(angle) * r, py, entity.getZ() - Math.sin(angle) * r,
                        0.0, 0.05, 0.0);
            }
            case SHOCKWAVE -> {
                if (progress < 0.45) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double speed = 0.12 + random.nextDouble() * 0.08;
                    level.addParticle(ParticleTypes.CLOUD,
                            entity.getX(), entity.getY() + 0.35, entity.getZ(),
                            Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed);
                }
            }
            case GENTLE_DRIFT -> {
                if (random.nextFloat() < 0.5f) {
                    double px = entity.getX() + (random.nextDouble() - 0.5) * 1.2;
                    double py = entity.getY() + 0.2 + random.nextDouble() * 1.6;
                    double pz = entity.getZ() + (random.nextDouble() - 0.5) * 1.2;
                    level.addParticle(random.nextBoolean() ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL,
                            px, py, pz, 0.0, 0.025, 0.0);
                }
            }
            case NONE -> {}
        }
    }

    private static void spawnVoidAmbientParticles(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.random;
        double baseX = player.getX();
        double baseY = player.getY();
        double baseZ = player.getZ();

        for (int i = 0; i < 3; i++) {
            double px = baseX + (random.nextDouble() - 0.5) * 28.0;
            double py = baseY + (random.nextDouble() - 0.4) * 16.0;
            double pz = baseZ + (random.nextDouble() - 0.5) * 28.0;
            level.addParticle(
                    random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    px, py, pz,
                    (random.nextDouble() - 0.5) * 0.015,
                    0.01 + random.nextDouble() * 0.02,
                    (random.nextDouble() - 0.5) * 0.015
            );
        }

        if (random.nextFloat() < 0.35f) {
            double px = baseX + (random.nextDouble() - 0.5) * 24.0;
            double py = baseY + (random.nextDouble() - 0.5) * 14.0;
            double pz = baseZ + (random.nextDouble() - 0.5) * 24.0;
            level.addParticle(
                    random.nextFloat() < 0.5f ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL,
                    px, py, pz,
                    0.0, 0.025 + random.nextDouble() * 0.02, 0.0
            );
        }

        if (random.nextFloat() < 0.25f) {
            double px = baseX + (random.nextDouble() - 0.5) * 32.0;
            double py = baseY + (random.nextDouble() - 0.5) * 18.0;
            double pz = baseZ + (random.nextDouble() - 0.5) * 32.0;
            level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0.0, 0.01, 0.0);
        }
    }
}
