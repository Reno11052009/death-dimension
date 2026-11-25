package com.reno.deathdimension.cooldown;

import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.player.PlayerDeathData;

import java.time.Instant;
import java.util.function.LongSupplier;

public final class CooldownManager {
    private static volatile LongSupplier clock = () -> Instant.now().getEpochSecond();

    private CooldownManager() {}

    public static void setClockForTesting(LongSupplier customClock) {
        clock = customClock != null ? customClock : () -> Instant.now().getEpochSecond();
    }

    public static long nowEpochSeconds() {
        return clock.getAsLong();
    }

    public static void startCooldown(PlayerDeathData data, long durationSeconds) {
        long clamped = Math.max(0L, durationSeconds);
        data.setCooldownStart(nowEpochSeconds());
        data.setCooldownDuration(clamped);
        boolean done = clamped == 0L;
        data.setCooldownCompleted(done);
        data.setCooldownNotified(done);
    }

    public static void setCooldown(PlayerDeathData data, long seconds) {
        startCooldown(data, seconds);
    }

    public static void resetCooldown(PlayerDeathData data) {
        data.setCooldownStart(0L);
        data.setCooldownDuration(0L);
        data.setCooldownCompleted(true);
        data.setCooldownNotified(true);
    }

    public static long getRemainingSeconds(PlayerDeathData data) {
        if (!ModConfig.enableCooldown || data.isCooldownCompleted()) {
            return 0L;
        }
        long end = data.getCooldownStart() + data.getCooldownDuration();
        long remaining = Math.max(0L, end - nowEpochSeconds());
        if (remaining == 0L) {
            data.setCooldownCompleted(true);
        }
        return remaining;
    }

    public static boolean isCooldownComplete(PlayerDeathData data, boolean isAdmin) {
        if (!ModConfig.enableCooldown) {
            return true;
        }
        if (isAdmin && ModConfig.allowAdminBypass) {
            return true;
        }
        return getRemainingSeconds(data) == 0L;
    }

    public static String formatTime(long totalSeconds) {
        long safe = Math.max(0L, totalSeconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long seconds = safe % 60L;
        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
