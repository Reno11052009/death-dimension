package com.reno.deathdimension;

import com.reno.deathdimension.client.DeathDimensionClientEvents;
import com.reno.deathdimension.client.ModAutoUpdater;
import com.reno.deathdimension.client.TransitionOverlayLayer;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReturnMode;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import com.reno.deathdimension.cooldown.CooldownManager;
import com.reno.deathdimension.player.PlayerDeathData;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DeathDimensionSelfCheck {
    public static void main(String[] args) {
        checkTimeFormatting();
        checkCooldownProgressionAndRestartPersistence();
        checkAdminBypassAndReset();
        checkTransitionCurves();
        checkConfigSnapshotCodecAndSanitization();
        checkLogoutStatePersistence();
        checkAutoUpdaterManifestAndJarValidation();
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

        // Verify stashed inventory and experience survive NBT round-trips and clearDeathState()
        CompoundTag stashedTag = new CompoundTag();
        stashedTag.putBoolean("InDeathDimension", true);
        stashedTag.putBoolean("HasSavedInventory", true);
        ListTag fakeItems = new ListTag();
        CompoundTag itemTag = new CompoundTag();
        itemTag.putByte("Slot", (byte) 0);
        itemTag.putString("id", "minecraft:diamond_sword");
        itemTag.putInt("count", 1);
        fakeItems.add(itemTag);
        stashedTag.put("SavedInventory", fakeItems);
        stashedTag.putBoolean("HasSavedExperience", true);
        stashedTag.putInt("SavedExpLevel", 27);
        stashedTag.putInt("SavedTotalExp", 1100);
        stashedTag.putFloat("SavedExpProgress", 0.45f);

        PlayerDeathData stashed = new PlayerDeathData();
        stashed.deserializeNBT(null, stashedTag);
        CompoundTag reserialized = stashed.serializeNBT(null);
        PlayerDeathData reloadedStash = new PlayerDeathData();
        reloadedStash.deserializeNBT(null, reserialized);

        assertTrue(reloadedStash.hasSavedInventory(), "stashed inventory flag persisted");
        assertEq(1, reloadedStash.getSavedInventoryCount(), "stashed inventory item count persisted");
        assertTrue(reloadedStash.hasSavedExperience(), "stashed experience flag persisted");
        assertEq(27, reloadedStash.getSavedExpLevel(), "stashed exp level persisted");
        assertEq(1100, reloadedStash.getSavedTotalExp(), "stashed total exp persisted");
        assertEq(0.45f, reloadedStash.getSavedExpProgress(), "stashed exp progress persisted");

        reloadedStash.clearDeathState();
        assertTrue(reloadedStash.hasSavedInventory(), "clearDeathState preserves unrestored inventory");
        assertTrue(reloadedStash.hasSavedExperience(), "clearDeathState preserves unrestored experience");
    }

    private static void checkConfigSnapshotCodecAndSanitization() {
        ModConfig.ConfigSnapshot dirty = new ModConfig.ConfigSnapshot(
                true, true, true, true, true, true,
                Double.NaN, 9999.0, -99_000_000.0, true, false,
                true, true, true, true, true,
                true, -50L, true, true, true,
                true, ReturnMode.AUTO, true, true, true,
                true, true, true, true,
                true, true,
                true, true, true, ReviveAnimationStyle.SPIRAL_ASCENT
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

        assertEq(1.0, TransitionOverlayLayer.eyeCloseFactor(0.0), "death eyes start fully open");
        double midDroop = TransitionOverlayLayer.eyeCloseFactor(0.50);
        double flutter = TransitionOverlayLayer.eyeCloseFactor(0.62);
        assertTrue(midDroop > 0.2 && midDroop < 0.4, "death eyes droop partway");
        assertTrue(flutter > midDroop && flutter < 0.5, "death eyes show faint fading flutter");
        assertEq(0.0, TransitionOverlayLayer.eyeCloseFactor(0.95), "death eyes shut before respawn");
        assertEq(0.0, TransitionOverlayLayer.eyeCloseFactor(1.0), "death eyes fully closed at t=1");

        assertEq(0.0, TransitionOverlayLayer.deathCameraFallFactor(0.0), "death camera fall starts at 0");
        assertTrue(TransitionOverlayLayer.deathCameraFallFactor(0.31) > 0.2
                && TransitionOverlayLayer.deathCameraFallFactor(0.31) < 0.3, "death camera fall accelerates quadratically");
        assertEq(1.0, TransitionOverlayLayer.deathCameraFallFactor(0.62), "death camera fall reaches ground at 0.62");
        assertEq(1.0, TransitionOverlayLayer.deathCameraFallFactor(1.0), "death camera fall rests at 1.0");

        assertTrue(TransitionOverlayLayer.reviveFlashAlpha(0.0) > 0.7, "revive flash starts bright");
        assertTrue(TransitionOverlayLayer.reviveFlashAlpha(0.6) == 0.0, "revive flash clears cleanly");

        // Respawn 3-block float & 360-degree camera orbit curves
        assertEq(0.0, TransitionOverlayLayer.respawnOrbitHeightFactor(0.0), "orbit lift starts on ground");
        double ascentPeak = TransitionOverlayLayer.respawnOrbitHeightFactor(0.8 / 3.0);
        assertTrue(Math.abs(ascentPeak - 1.0) < 1e-6, "orbit lift reaches full height at 0.8s");
        double hoverMid = TransitionOverlayLayer.respawnOrbitHeightFactor(1.5 / 3.0);
        assertTrue(hoverMid >= 0.95 && hoverMid <= 1.0, "orbit hover stays within safe headroom");
        assertEq(0.0, TransitionOverlayLayer.respawnOrbitHeightFactor(2.8 / 3.0), "orbit lands cleanly at 2.8s");
        assertEq(0.0, TransitionOverlayLayer.respawnOrbitHeightFactor(1.0), "orbit stays grounded during settle");

        assertEq(0.0, TransitionOverlayLayer.respawnOrbitAngleDegrees(0.0), "orbit yaw starts at 0 deg");
        double midOrbitAngle = TransitionOverlayLayer.respawnOrbitAngleDegrees(1.4 / 3.0);
        assertTrue(Math.abs(midOrbitAngle - 180.0) < 1e-6, "orbit yaw reaches 180 deg at midpoint");
        assertEq(360.0, TransitionOverlayLayer.respawnOrbitAngleDegrees(2.8 / 3.0), "orbit completes 360 deg at 2.8s");
        assertEq(360.0, TransitionOverlayLayer.respawnOrbitAngleDegrees(1.0), "orbit holds 360 deg through settle");

        assertEq(0.0, TransitionOverlayLayer.respawnOrbitPitchBlend(0.0), "orbit pitch blend starts at 0");
        assertEq(1.0, TransitionOverlayLayer.respawnOrbitPitchBlend(0.5), "orbit pitch blend full during hover");
        assertEq(0.0, TransitionOverlayLayer.respawnOrbitPitchBlend(1.0), "orbit pitch blend returns to 0");

        float midDist = TransitionOverlayLayer.respawnOrbitCameraDistance(0.5, true);
        float endDistFp = TransitionOverlayLayer.respawnOrbitCameraDistance(1.0, true);
        float endDistTp = TransitionOverlayLayer.respawnOrbitCameraDistance(1.0, false);
        assertTrue(midDist >= 4.5f && midDist <= 5.0f, "orbit camera distance frames floating player");
        assertTrue(endDistFp < 1.0f, "orbit zooms smoothly in when returning to first-person");
        assertTrue(Math.abs(endDistTp - 4.0f) < 1e-5f, "orbit returns to vanilla 4.0 distance for third-person");

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

    private static void checkAutoUpdaterManifestAndJarValidation() {
        assertTrue(ModAutoUpdater.compareVersions("1.0.2", "1.0.1") > 0, "1.0.2 > 1.0.1");
        assertTrue(ModAutoUpdater.compareVersions("1.0.10", "1.0.2") > 0, "1.0.10 > 1.0.2");
        assertTrue(ModAutoUpdater.compareVersions("1.0.1", "1.0.1-beta") > 0, "release > prerelease");
        assertEq(0, ModAutoUpdater.compareVersions("1.0.1", "1.0.1"), "equal versions");

        String manifest = """
                {
                  "homepage": "https://github.com/Reno11052009/death-dimension/releases",
                  "downloads": {
                    "1.21.1": {
                      "1.0.2": "https://github.com/Reno11052009/death-dimension/releases/download/v1.0.2/deathdimension-1.0.2.jar"
                    }
                  },
                  "promos": {
                    "1.21.1-recommended": "1.0.1",
                    "1.21.1-latest": "1.0.2"
                  }
                }
                """;
        ModAutoUpdater.UpdateInfo parsed = ModAutoUpdater.parseUpdateManifest(manifest, "1.21.1");
        assertTrue(parsed != null, "manifest parsed");
        assertEq("1.0.2", parsed.targetVersion(), "higher promo selected");
        assertEq("https://github.com/Reno11052009/death-dimension/releases/download/v1.0.2/deathdimension-1.0.2.jar",
                parsed.downloadUrl(), "explicit github download url resolved");

        assertTrue(ModAutoUpdater.isAllowedDownloadUrl("https://github.com/Reno11052009/death-dimension/releases/download/v1.0.2/deathdimension-1.0.2.jar"),
                "github https allowed");
        assertTrue(!ModAutoUpdater.isAllowedDownloadUrl("http://github.com/Reno11052009/death-dimension/releases/download/v1.0.2/deathdimension-1.0.2.jar"),
                "http rejected");
        assertTrue(!ModAutoUpdater.isAllowedDownloadUrl("https://evil.example.com/deathdimension.jar"),
                "non-github host rejected");

        try {
            Path validJar = Files.createTempFile("dd-valid-", ".jar");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(validJar))) {
                zos.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
                zos.write("modId = \"deathdimension\"\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            assertTrue(ModAutoUpdater.isValidModJar(validJar), "valid mod jar accepted");
            Files.deleteIfExists(validJar);

            Path invalidJar = Files.createTempFile("dd-invalid-", ".jar");
            Files.writeString(invalidJar, "<html>404 Not Found</html>");
            assertTrue(!ModAutoUpdater.isValidModJar(invalidJar), "html file rejected as mod jar");
            Files.deleteIfExists(invalidJar);
        } catch (Exception e) {
            throw new AssertionError("JAR validation self-check failed", e);
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
