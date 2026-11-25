package com.reno.deathdimension.config;

import java.util.Locale;

public enum ReviveAnimationStyle {
    AWAKENING,
    SPIRAL_ASCENT,
    SHOCKWAVE,
    GENTLE_DRIFT,
    NONE;

    public static ReviveAnimationStyle fromOrdinal(int ordinal) {
        ReviveAnimationStyle[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return AWAKENING;
        }
        return values[ordinal];
    }

    public String translationKey() {
        return "enum.deathdimension.revive_style." + name().toLowerCase(Locale.ROOT);
    }
}
