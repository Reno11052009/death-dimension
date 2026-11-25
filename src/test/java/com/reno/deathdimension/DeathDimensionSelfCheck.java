package com.reno.deathdimension;

import com.reno.deathdimension.client.DeathDimensionClientEvents;
import com.reno.deathdimension.client.TransitionOverlayLayer;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReturnMode;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import com.reno.deathdimension.cooldown.CooldownManager;
import com.reno.deathdimension.player.PlayerDeathData;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicLong;

public final class DeathDimensionSelfCheck {
    public static void main(String[] args) {
        checkTimeFormatting();
        checkCooldownProgressionAndRestartPersistence();
        checkAdminBypassAndReset();
        checkTransitionCurves();
        checkConfigSnapshotCodecAndSanitization();
        checkLogoutStatePersistence();
        System.out.println("DeathDimensionSelfCheck: ALL CHECKS PASSED");
    }

    private static void checkLogoutStatePersistence() {
        PlayerDeathData data = new PlayerDeathData();
        data.setInDeathDimension(true);
        data.setAutoReturnEligible(true);
        data.recordDeathDimPosition(12.5, 108.0, -34.25, 90.0f, -15.0f);
        data.setLastLogoutEpoch(1_700_000_500L);

        CompoundTag tag = data.serializeNBT(null);
        PlayerDeathData loaded = new PlayerDeathData();
        loaded.deserializeNBT(null, tag);

        assertTrue(loaded.isInDeathDimension(), "inDeathDimension persisted across logout");
        assertTrue(loaded.isAutoReturnEligible(), "autoReturnEligible persisted across logout");
        assertTrue(loaded.hasDeathDimPosition(), "deathDim position flag persisted");
        assertEq(12.5, loaded.getDeathDimX(), "deathDimX persisted");
        assertEq(108.0, loaded.getDeathDimY(), "deathDimY persisted");
        assertEq(-34.25, loaded.getDeathDimZ(), "deathDimZ persisted");
        assertEq(1_700_000_500L, loaded.getLastLogoutEpoch(), "lastLogoutEpoch persisted");

        loaded.clearDeathState();
        assertTrue(!loaded.hasDeathDimPosition(), "clearDeathState resets deathDim position");
        assertTrue(!loaded.isAutoReturnEligible(), "clearDeathState resets autoReturnEligible");
        assertEq(0L, loaded.getLastLogoutEpoch(), "clearDeathState resets lastLogoutEpoch");
    }

    private static void checkConfigSnapshotCodecAndSanitization() {
        ModConfig.ConfigSnapshot dirty = new ModConfig.ConfigSnapshot(
                true, true, true, true,
                Double.NaN, 9999.0, -99_000_000.0, true, false,
                true, true, true, true,
                true, -50L, true, true, true,
                true, ReturnMode.AUTO, true, true, true,
                true, true, true, true,
                true, true, ReviveAnimationStyle.SPIRAL_ASCENT
        );
        ModConfig.ConfigSnapshot clean = dirty.sanitized();
        assertEq(0.0, clean.spawnX(), "NaN spawnX clamped to fallback");
        assertEq(320.0, clean.spawnY(), "spawnY clamped to max");
        assertEq(-30_000_000.0, clean.spawnZ(), "spawnZ clamped to min");
        assertEq(0L, clean.cooldownDuration(), "negative cooldown clamped to 0");
        assertEq(ReturnMode.AUTO, clean.returnMode(), "returnMode preserved");
        assertEq(ReviveAnimationStyle.SPIRAL_ASCENT, clean.reviveAnimationStyle(), "revive style preserved");

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        clean.write(buf);
        ModConfig.ConfigSnapshot decoded = ModConfig.ConfigSnapshot.read(buf);
        assertEq(clean, decoded, "ConfigSnapshot codec round-trip");

        for (ReviveAnimationStyle style : ReviveAnimationStyle.values()) {
            double startAlpha = TransitionOverlayLayer.reviveFlashAlpha(0.0, style);
            double endAlpha = TransitionOverlayLayer.reviveFlashAlpha(1.0, style);
            assertEq(0.0, endAlpha, "flash ends at 0 for " + style);
            if (style == ReviveAnimationStyle.NONE) {
                assertEq(0.0, startAlpha, "NONE flash stays 0");
            } else {
                assertTrue(startAlpha > 0.3, "active style has initial flash: " + style);
            }
        }
    }

    private static void checkTransitionCurves() {
        assertTrue(TransitionOverlayLayer.eyeOpenFactor(0.0) == 0.0, "eyes start closed");
        assertTrue(TransitionOverlayLayer.eyeOpenFactor(0.10) == 0.0, "eyes stay closed initially");
        double peek = TransitionOverlayLayer.eyeOpenFactor(0.36);
        double blink = TransitionOverlayLayer.eyeOpenFactor(0.48);
        double open = TransitionOverlayLayer.eyeOpenFactor(1.0);
        assertTrue(peek > 0.3 && peek < 0.45, "first peek opens partway");
        assertTrue(blink < peek && blink > 0.0, "drowsy blink closes slightly");
        assertTrue(Math.abs(open - 1.0) < 1e-6, "eyes fully open at t=1");

        assertTrue(TransitionOverlayLayer.reviveFlashAlpha(0.0) > 0.7, "revive flash starts bright");
        assertTrue(TransitionOverlayLayer.reviveFlashAlpha(0.6) == 0.0, "revive flash clears cleanly");

        assertEq(89.0f, DeathDimensionClientEvents.clampPitch(120.0f), "camera pitch clamped to +89");
        assertEq(-89.0f, DeathDimensionClientEvents.clampPitch(-110.0f), "camera pitch clamped to -89");
        assertEq(12.5f, DeathDimensionClientEvents.clampPitch(12.5f), "camera pitch in range preserved");
    }

    private static void checkTimeFormatting() {
        assertEq("00:00", CooldownManager.formatTime(0), "zero seconds");
        assertEq("00:45", CooldownManager.formatTime(45), "45 seconds");
        assertEq("59:59", CooldownManager.formatTime(3599), "59m59s");
        assertEq("01:00:00", CooldownManager.formatTime(3600), "1 hour");
        assertEq("01:01:01", CooldownManager.formatTime(3661), "1h1m1s");
        assertEq("00:00", CooldownManager.formatTime(-10), "negative clamp");
    }

    private static void checkCooldownProgressionAndRestartPersistence() {
        AtomicLong now = new AtomicLong(1_700_000_000L);
        CooldownManager.setClockForTesting(now::get);
        ModConfig.enableCooldown = true;
        ModConfig.allowAdminBypass = true;

        try {
            PlayerDeathData data = new PlayerDeathData();
            data.recordOrigin(Level.NETHER, 120.5, 70.0, -250.25, 90.0f, 15.0f);
            data.saveAbilities(false, false, false);
            data.setInDeathDimension(true);

            CooldownManager.startCooldown(data, 60L);
            assertEq(60L, CooldownManager.getRemainingSeconds(data), "initial remaining");
            assertTrue(!CooldownManager.isCooldownComplete(data, false), "player blocked during cooldown");

            // Simulate logout + server restart via full NBT round-trip while 15s pass offline
            CompoundTag saved = data.serializeNBT(null);
            now.addAndGet(15L);

            PlayerDeathData reloaded = new PlayerDeathData();
            reloaded.deserializeNBT(null, saved);

            assertTrue(reloaded.isInDeathDimension(), "inDeathDimension persisted");
            assertEq(Level.NETHER.location().toString(), reloaded.getOriginDimension(), "origin dim persisted");
            assertEq(120.5, reloaded.getOriginX(), "origin X persisted");
            assertEq(45L, CooldownManager.getRemainingSeconds(reloaded), "remaining ticked while offline");
            assertTrue(!CooldownManager.isCooldownComplete(reloaded, false), "still blocked at 45s");

            // Advance past cooldown end
            now.addAndGet(45L);
            assertEq(0L, CooldownManager.getRemainingSeconds(reloaded), "expired remaining");
            assertTrue(CooldownManager.isCooldownComplete(reloaded, false), "unlocked after expiration");
            assertTrue(reloaded.isCooldownCompleted(), "completed flag latched");
        } finally {
            CooldownManager.setClockForTesting(null);
        }
    }

    private static void checkAdminBypassAndReset() {
        AtomicLong now = new AtomicLong(2_000_000_000L);
        CooldownManager.setClockForTesting(now::get);
        ModConfig.enableCooldown = true;

        try {
            PlayerDeathData data = new PlayerDeathData();
            CooldownManager.setCooldown(data, 120L);

            ModConfig.allowAdminBypass = true;
            assertTrue(CooldownManager.isCooldownComplete(data, true), "admin bypass allowed");
            assertTrue(!CooldownManager.isCooldownComplete(data, false), "non-admin still blocked");

            ModConfig.allowAdminBypass = false;
            assertTrue(!CooldownManager.isCooldownComplete(data, true), "admin bypass respects config=false");

            CooldownManager.resetCooldown(data);
            assertEq(0L, CooldownManager.getRemainingSeconds(data), "reset clears remaining");
            assertTrue(CooldownManager.isCooldownComplete(data, false), "reset unlocks player");
        } finally {
            ModConfig.allowAdminBypass = true;
            CooldownManager.setClockForTesting(null);
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + msg);
        }
    }

    private static void assertEq(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Assertion failed (" + msg + "): expected=" + expected + " actual=" + actual);
        }
    }
}
