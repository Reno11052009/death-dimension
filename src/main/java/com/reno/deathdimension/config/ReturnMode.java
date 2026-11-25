package com.reno.deathdimension.config;

import java.util.Locale;

public enum ReturnMode {
    MANUAL,
    AUTO;

    public static ReturnMode fromOrdinal(int ordinal) {
        ReturnMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return MANUAL;
        }
        return values[ordinal];
    }

    public String translationKey() {
        return "enum.deathdimension.return_mode." + name().toLowerCase(Locale.ROOT);
    }
}
