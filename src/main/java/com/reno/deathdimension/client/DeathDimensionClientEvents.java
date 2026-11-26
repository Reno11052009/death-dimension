package com.reno.deathdimension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import com.reno.deathdimension.network.DeathTransitionPayload;
import com.reno.deathdimension.registry.ModRegistries;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class DeathDimensionClientEvents {
    private static final long DEATH_COLLAPSE_DURATION_NS = 1_100_000_000L;
    private static final long DEATH_COLLAPSE_SAFETY_TIMEOUT_NS = 2_600_000_000L;
    private static final long AWAIT_EYE_OPEN_TIMEOUT_NS = 3_000_000_000L;
    private static final long EYE_OPEN_DURATION_NS = 2_800_000_000L;
    private static final long REVIVE_ANIM_DURATION_NS = 1_800_000_000L;
    private static final long RESPAWN_ORBIT_DURATION_NS = 3_000_000_000L;
    private static final long RESPAWN_ORBIT_PENDING_TIMEOUT_NS = 2_500_000_000L;
    private static final double RESPAWN_ORBIT_DESIRED_HEIGHT = 3.0;

    private record ActiveRevive(long startNs, ReviveAnimationStyle style) {}

    private static final class RespawnOrbitState {
        boolean started;
        long queuedAtNs;
        long startNs;
        double baseX;
        double baseY;
        double baseZ;
        float baseYaw;
        float basePitch;
        double maxFloatHeight;
        CameraType savedCameraType;
        boolean savedFlying;
        boolean landingBurstSpawned;
    }

    private static volatile boolean serverHasMod = false;
    private static volatile long deathCollapseStartNs = -1L;
    private static volatile boolean deathRespawnPending = false;
    private static volatile float deathFallRollDirection = 1.0f;
    private static volatile long awaitingEyeOpenStartNs = -1L;
    private static volatile long eyeOpenStartNs = -1L;
    private static volatile ActiveRevive localRevive = null;
    private static volatile RespawnOrbitState respawnOrbit = null;

    private static final Map<Integer, ActiveRevive> revivingPlayers = new HashMap<>();
    private static final Set<Integer> pushedPlayersThisFrame = new HashSet<>();
    private static Button titleUpdateButton;

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
            deathCollapseStartNs = -1L;
            deathRespawnPending = false;
            awaitingEyeOpenStartNs = -1L;
            if (ModConfig.enableEyeOpenEffect) {
                eyeOpenStartNs = now;
            }
            return;
        }

        if (payload.transitionKind() == DeathTransitionPayload.KIND_RETURN_TO_LIFE && ModConfig.enableReviveAnimation) {
            ReviveAnimationStyle style = payload.style();
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer localPlayer = mc.player;
            boolean isLocal = localPlayer != null && localPlayer.getId() == payload.entityId();

            if (style != ReviveAnimationStyle.NONE) {
                ActiveRevive active = new ActiveRevive(now, style);
                revivingPlayers.put(payload.entityId(), active);
                if (isLocal) {
                    localRevive = active;
                }
            }

            if (isLocal) {
                deathCollapseStartNs = -1L;
                deathRespawnPending = false;
                awaitingEyeOpenStartNs = -1L;
                eyeOpenStartNs = -1L;
                if (ModConfig.enableRespawnOrbitAnimation) {
                    beginOrQueueRespawnOrbit(mc, localPlayer, now);
                }
            }
        }
    }

    private static void beginOrQueueRespawnOrbit(Minecraft mc, LocalPlayer player, long now) {
        CameraType originalCamera = respawnOrbit != null && respawnOrbit.savedCameraType != null
                ? respawnOrbit.savedCameraType
                : mc.options.getCameraType();

        RespawnOrbitState state = new RespawnOrbitState();
        state.queuedAtNs = now;
        state.savedCameraType = originalCamera;
        state.savedFlying = player.isCreative() || player.isSpectator();
        respawnOrbit = state;

        if (mc.level != null && !(mc.screen instanceof ReceivingLevelScreen)
                && mc.level.hasChunk(player.chunkPosition().x, player.chunkPosition().z)) {
            activateRespawnOrbit(mc, mc.level, player, state, now);
        }
    }

    private static void activateRespawnOrbit(Minecraft mc, ClientLevel level, LocalPlayer player,
                                             RespawnOrbitState state, long now) {
        state.started = true;
        state.startNs = now;
        state.baseX = player.getX();
        state.baseY = player.getY();
        state.baseZ = player.getZ();
        state.baseYaw = player.getYRot();
        state.basePitch = player.getXRot();
        state.maxFloatHeight = player.isPassenger()
                ? 0.0
                : computeSafeFloatHeight(level, player, RESPAWN_ORBIT_DESIRED_HEIGHT);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static double computeSafeFloatHeight(ClientLevel level, LocalPlayer player, double desiredHeight) {
        AABB baseBox = player.getBoundingBox();
        double safeHeight = 0.0;
        double step = 0.1;
        double headroomMargin = 0.08;
        for (double h = step; h <= desiredHeight + 1.0E-6; h += step) {
            double testH = Math.min(h, desiredHeight);
            AABB shifted = baseBox.move(0.0, testH + headroomMargin, 0.0);
            if (!level.noCollision(player, shifted)) {
                break;
            }
            safeHeight = testH;
        }
        return safeHeight;
    }

    private static void finishRespawnOrbit(Minecraft mc, LocalPlayer player) {
        RespawnOrbitState state = respawnOrbit;
        respawnOrbit = null;
        if (state == null) {
            return;
        }
        if (state.savedCameraType != null) {
            mc.options.setCameraType(state.savedCameraType);
        }
        if (player != null) {
            if (state.started && !player.isPassenger() && player.isAlive()) {
                player.setPos(state.baseX, state.baseY, state.baseZ);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                player.setOnGround(true);
            }
            if (!player.isCreative() && !player.isSpectator() && !state.savedFlying) {
                player.getAbilities().flying = false;
            }
        }
    }

    public static double getRespawnOrbitProgress() {
        RespawnOrbitState state = respawnOrbit;
        if (state == null || !state.started) {
            return -1.0;
        }
        long elapsed = System.nanoTime() - state.startNs;
        if (elapsed >= RESPAWN_ORBIT_DURATION_NS) {
            return -1.0;
        }
        return (double) elapsed / (double) RESPAWN_ORBIT_DURATION_NS;
    }

    public static double getDeathCollapseProgress() {
        long start = deathCollapseStartNs;
        if (start < 0L) {
            return -1.0;
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed >= DEATH_COLLAPSE_SAFETY_TIMEOUT_NS) {
            deathCollapseStartNs = -1L;
            deathRespawnPending = false;
            return -1.0;
        }
        if (elapsed >= DEATH_COLLAPSE_DURATION_NS) {
            return 1.0;
        }
        return (double) elapsed / (double) DEATH_COLLAPSE_DURATION_NS;
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
        finishRespawnOrbit(Minecraft.getInstance(), null);
        serverHasMod = false;
        deathCollapseStartNs = -1L;
        deathRespawnPending = false;
        awaitingEyeOpenStartNs = -1L;
        eyeOpenStartNs = -1L;
        localRevive = null;
        revivingPlayers.clear();
        pushedPlayersThisFrame.clear();
        // Restore local config so a previous server's ConfigSyncPayload never leaks into another session.
        ModConfig.reloadFromDisk();
    }

    @SubscribeEvent
    public void onTitleScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            titleUpdateButton = null;
            return;
        }
        ModAutoUpdater.triggerInitialCheckIfNeeded();

        Button btn = Button.builder(Component.translatable("gui.deathdimension.updater.btn_check"), b -> onTitleUpdateButtonClick())
                .bounds(6, 6, 108, 20)
                .build();
        titleUpdateButton = btn;
        syncTitleUpdateButton(btn, ModAutoUpdater.getStatus());
        event.addListener(btn);
    }

    @SubscribeEvent
    public void onTitleScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        ModAutoUpdater.StatusSnapshot snapshot = ModAutoUpdater.getStatus();
        if (titleUpdateButton != null) {
            syncTitleUpdateButton(titleUpdateButton, snapshot);
        }
        if (snapshot.state() == ModAutoUpdater.State.IDLE) {
            return;
        }

        Component statusText = formatUpdaterStatus(snapshot);
        int textColor = switch (snapshot.state()) {
            case UP_TO_DATE -> 0x55FF55;
            case UPDATE_AVAILABLE, DOWNLOADING -> 0xFFAA00;
            case INSTALLED_RESTART_REQUIRED -> 0x55FFFF;
            case FAILED -> 0xFF5555;
            default -> 0xCCCCCC;
        };

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int x = 118;
        int y = 6;
        int textW = font.width(statusText);
        int boxW = Math.min(textW + 10, Math.max(40, event.getScreen().width - x - 6));
        guiGraphics.fill(x, y, x + boxW, y + 20, 0xA0101018);
        guiGraphics.enableScissor(x + 2, y, x + boxW - 2, y + 20);
        guiGraphics.drawString(font, statusText, x + 5, y + 6, textColor, true);
        guiGraphics.disableScissor();
    }

    private static void onTitleUpdateButtonClick() {
        ModAutoUpdater.StatusSnapshot snapshot = ModAutoUpdater.getStatus();
        switch (snapshot.state()) {
            case INSTALLED_RESTART_REQUIRED -> Minecraft.getInstance().stop();
            case UPDATE_AVAILABLE -> {
                if (snapshot.canAutoInstall()) {
                    ModAutoUpdater.startInstallCurrentUpdateAsync();
                } else {
                    Util.getPlatform().openUri(snapshot.releaseUrl());
                }
            }
            case CHECKING, DOWNLOADING -> {}
            default -> ModAutoUpdater.startCheckAsync(ModConfig.autoInstallUpdates);
        }
    }

    private static void syncTitleUpdateButton(Button btn, ModAutoUpdater.StatusSnapshot snapshot) {
        switch (snapshot.state()) {
            case CHECKING -> {
                btn.setMessage(Component.translatable("gui.deathdimension.updater.btn_checking"));
                btn.active = false;
            }
            case DOWNLOADING -> {
                btn.setMessage(Component.translatable("gui.deathdimension.updater.btn_downloading", snapshot.progressPercent()));
                btn.active = false;
            }
            case UPDATE_AVAILABLE -> {
                String target = snapshot.targetVersion() != null ? snapshot.targetVersion() : "";
                btn.setMessage(Component.translatable(
                        snapshot.canAutoInstall() ? "gui.deathdimension.updater.btn_install" : "gui.deathdimension.updater.btn_download",
                        target
                ));
                btn.active = true;
            }
            case INSTALLED_RESTART_REQUIRED -> {
                btn.setMessage(Component.translatable("gui.deathdimension.updater.btn_restart"));
                btn.active = true;
            }
            case FAILED -> {
                btn.setMessage(Component.translatable("gui.deathdimension.updater.btn_retry"));
                btn.active = true;
            }
            default -> {
                btn.setMessage(Component.translatable("gui.deathdimension.updater.btn_check"));
                btn.active = true;
            }
        }
    }

    private static Component formatUpdaterStatus(ModAutoUpdater.StatusSnapshot snapshot) {
        String target = snapshot.targetVersion() != null ? snapshot.targetVersion() : "";
        return switch (snapshot.state()) {
            case CHECKING -> Component.translatable("gui.deathdimension.updater.status_checking");
            case UP_TO_DATE -> Component.translatable("gui.deathdimension.updater.status_up_to_date", snapshot.currentVersion());
            case UPDATE_AVAILABLE -> Component.translatable("gui.deathdimension.updater.status_available", target);
            case DOWNLOADING -> Component.translatable("gui.deathdimension.updater.status_downloading", target, snapshot.progressPercent());
            case INSTALLED_RESTART_REQUIRED -> Component.translatable("gui.deathdimension.updater.status_installed", target);
            case FAILED -> Component.translatable("gui.deathdimension.updater.status_failed");
            case IDLE -> Component.empty();
        };
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen)) {
            return;
        }
        finishRespawnOrbit(Minecraft.getInstance(), null);
        if (!serverHasMod || !ModConfig.enableMod || !ModConfig.enableDeathTeleport || !ModConfig.skipDeathScreen) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.getLevelData().isHardcore()) {
            return;
        }

        event.setCanceled(true);

        if (ModConfig.enableDeathCameraFall || ModConfig.enableDeathEyeClose) {
            if (deathCollapseStartNs < 0L) {
                deathCollapseStartNs = System.nanoTime();
                deathRespawnPending = true;
                deathFallRollDirection = mc.level.random.nextBoolean() ? 1.0f : -1.0f;
                awaitingEyeOpenStartNs = -1L;
                eyeOpenStartNs = -1L;
                localRevive = null;
            }
            return;
        }

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

        long collapseStart = deathCollapseStartNs;
        if (collapseStart >= 0L) {
            long elapsed = System.nanoTime() - collapseStart;
            if (player.isAlive() && !player.isDeadOrDying()) {
                deathCollapseStartNs = -1L;
                deathRespawnPending = false;
            } else if (elapsed >= DEATH_COLLAPSE_DURATION_NS && deathRespawnPending) {
                deathRespawnPending = false;
                deathCollapseStartNs = -1L;
                if (ModConfig.enableEyeOpenEffect || ModConfig.enableDeathEyeClose) {
                    awaitingEyeOpenStartNs = System.nanoTime();
                }
                player.respawn();
            }
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
    public void onMovementInputUpdate(MovementInputUpdateEvent event) {
        RespawnOrbitState state = respawnOrbit;
        if (state == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player) {
            return;
        }
        if (state.started) {
            double progress = getRespawnOrbitProgress();
            if (progress < 0.0 || progress >= 2.82 / 3.0) {
                return;
            }
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public void onLocalPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.level().isClientSide()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || mc.isPaused()) {
            return;
        }
        RespawnOrbitState state = respawnOrbit;
        if (state == null) {
            return;
        }

        ClientLevel level = mc.level;
        if (level == null || !player.isAlive() || player.isDeadOrDying()
                || level.dimension().equals(ModRegistries.DEATH_DIMENSION)) {
            finishRespawnOrbit(mc, player);
            return;
        }

        long now = System.nanoTime();
        if (!state.started) {
            if (now - state.queuedAtNs > RESPAWN_ORBIT_PENDING_TIMEOUT_NS) {
                finishRespawnOrbit(mc, player);
                return;
            }
            if (!(mc.screen instanceof ReceivingLevelScreen)
                    && level.hasChunk(player.chunkPosition().x, player.chunkPosition().z)) {
                activateRespawnOrbit(mc, level, player, state, now);
            } else {
                return;
            }
        }

        long elapsed = now - state.startNs;
        if (elapsed >= RESPAWN_ORBIT_DURATION_NS) {
            finishRespawnOrbit(mc, player);
            return;
        }

        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        double progress = (double) elapsed / (double) RESPAWN_ORBIT_DURATION_NS;
        double heightFactor = TransitionOverlayLayer.respawnOrbitHeightFactor(progress);
        double targetY = state.baseY + state.maxFloatHeight * heightFactor;

        if (!player.isPassenger()) {
            player.setPos(state.baseX, targetY, state.baseZ);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.setOnGround(heightFactor <= 0.001);
        }

        spawnRespawnOrbitParticles(level, player, state, progress);
    }

    @SubscribeEvent
    public void onCalculateDetachedCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        RespawnOrbitState state = respawnOrbit;
        if (state == null || !state.started) {
            return;
        }
        double progress = getRespawnOrbitProgress();
        if (progress < 0.0 || progress >= 1.0) {
            return;
        }
        boolean returnToFirstPerson = state.savedCameraType == CameraType.FIRST_PERSON;
        event.setDistance(TransitionOverlayLayer.respawnOrbitCameraDistance(progress, returnToFirstPerson));
    }

    @SubscribeEvent
    public void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double deathCollapseProgress = getDeathCollapseProgress();
        if (deathCollapseProgress >= 0.0 && ModConfig.enableDeathCameraFall) {
            double fall = TransitionOverlayLayer.deathCameraFallFactor(deathCollapseProgress);
            event.setPitch(clampPitch(event.getPitch() + (float) (42.0 * fall)));
            event.setRoll(event.getRoll() + (float) (deathFallRollDirection * 32.0 * fall));
            event.setYaw(event.getYaw() + (float) (deathFallRollDirection * 12.0 * fall));
        }

        double eyeProgress = getEyeOpenProgress();
        if (eyeProgress >= 0.0 && eyeProgress < 1.0) {
            double closed = 1.0 - TransitionOverlayLayer.eyeOpenFactor(eyeProgress);
            event.setPitch(clampPitch(event.getPitch() + (float) (14.0 * closed)));
        }

        RespawnOrbitState orbit = respawnOrbit;
        if (orbit != null && orbit.started) {
            double orbitProgress = getRespawnOrbitProgress();
            if (orbitProgress >= 0.0 && orbitProgress < 1.0) {
                double orbitAngle = TransitionOverlayLayer.respawnOrbitAngleDegrees(orbitProgress);
                double pitchBlend = TransitionOverlayLayer.respawnOrbitPitchBlend(orbitProgress);
                float cinematicPitch = 16.0f;
                float targetPitch = (float) (orbit.basePitch + (cinematicPitch - orbit.basePitch) * pitchBlend);
                float subtleRoll = (float) (2.5 * Math.sin(Math.PI * 2.0 * orbitProgress) * pitchBlend);
                event.setYaw(orbit.baseYaw + (float) orbitAngle);
                event.setPitch(clampPitch(targetPitch));
                event.setRoll(event.getRoll() + subtleRoll);
                return;
            }
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
        double deathCollapseProgress = getDeathCollapseProgress();
        if (deathCollapseProgress >= 0.0 && ModConfig.enableDeathCameraFall) {
            double fall = TransitionOverlayLayer.deathCameraFallFactor(deathCollapseProgress);
            event.setFOV(Math.max(30.0, event.getFOV() - 12.0 * fall));
        }

        double eyeProgress = getEyeOpenProgress();
        if (eyeProgress >= 0.0 && eyeProgress < 1.0) {
            double closed = 1.0 - TransitionOverlayLayer.eyeOpenFactor(eyeProgress);
            event.setFOV(event.getFOV() - 7.0 * closed);
        }

        double orbitProgress = getRespawnOrbitProgress();
        if (orbitProgress >= 0.0 && orbitProgress < 1.0) {
            event.setFOV(event.getFOV() + 6.5 * Math.sin(Math.PI * orbitProgress));
            return;
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
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && localPlayer.getId() == id && getRespawnOrbitProgress() >= 0.0) {
            double orbitProgress = getRespawnOrbitProgress();
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            pushedPlayersThisFrame.add(id);
            float auraScale = 1.0f + (float) (0.035 * Math.sin(Math.PI * orbitProgress));
            poseStack.scale(auraScale, auraScale, auraScale);
            return;
        }

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

    private static void spawnRespawnOrbitParticles(ClientLevel level, LocalPlayer player,
                                                   RespawnOrbitState state, double progress) {
        RandomSource random = level.random;
        double descentEnd = 2.8 / 3.0;

        if (progress < descentEnd) {
            double angle = progress * Math.PI * 10.0;
            double radius = 0.72 + 0.12 * Math.sin(progress * Math.PI * 4.0);
            double feetY = player.getY() + 0.08;

            for (int arm = 0; arm < 2; arm++) {
                double a = angle + arm * Math.PI;
                double px = player.getX() + Math.cos(a) * radius;
                double pz = player.getZ() + Math.sin(a) * radius;
                level.addParticle(ParticleTypes.END_ROD, px, feetY, pz, 0.0, -0.025, 0.0);
            }

            if (random.nextFloat() < 0.65f) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = 0.35 + random.nextDouble() * 0.65;
                double px = player.getX() + Math.cos(a) * r;
                double pz = player.getZ() + Math.sin(a) * r;
                level.addParticle(
                        random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.ENCHANT,
                        px, feetY + random.nextDouble() * 0.4, pz,
                        0.0, -0.03, 0.0
                );
            }

            if (state.maxFloatHeight > 0.3 && random.nextFloat() < 0.55f) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double r = 0.25 + random.nextDouble() * 0.45;
                double py = state.baseY + random.nextDouble() * Math.max(0.2, player.getY() - state.baseY);
                level.addParticle(
                        ParticleTypes.END_ROD,
                        state.baseX + Math.cos(a) * r,
                        py,
                        state.baseZ + Math.sin(a) * r,
                        0.0, 0.05, 0.0
                );
            }
        } else if (!state.landingBurstSpawned) {
            state.landingBurstSpawned = true;
            int count = 18;
            for (int i = 0; i < count; i++) {
                double a = (Math.PI * 2.0 * i) / count;
                double speed = 0.11;
                double vx = Math.cos(a) * speed;
                double vz = Math.sin(a) * speed;
                level.addParticle(ParticleTypes.END_ROD, state.baseX, state.baseY + 0.1, state.baseZ, vx, 0.03, vz);
                if (i % 2 == 0) {
                    level.addParticle(ParticleTypes.CLOUD, state.baseX, state.baseY + 0.08, state.baseZ, vx * 0.85, 0.015, vz * 0.85);
                }
            }
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
