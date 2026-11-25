package com.reno.deathdimension.client;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class TransitionOverlayLayer {
    private static final ResourceLocation OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "transition_overlay");

    private TransitionOverlayLayer() {}

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(OVERLAY_ID, TransitionOverlayLayer::render);
    }

    public static double easeOutCubic(double x) {
        double clamped = Math.max(0.0, Math.min(1.0, x));
        double inv = 1.0 - clamped;
        return 1.0 - inv * inv * inv;
    }

    /**
     * Returns eyelid openness in [0.0, 1.0] for normalized progress t in [0.0, 1.0]:
     * closed -> slow first peek -> brief drowsy blink -> smooth full opening.
     */
    public static double eyeOpenFactor(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        if (clamped <= 0.12) {
            return 0.0;
        }
        if (clamped <= 0.36) {
            double local = (clamped - 0.12) / 0.24;
            return 0.38 * easeOutCubic(local);
        }
        if (clamped <= 0.48) {
            double local = (clamped - 0.36) / 0.12;
            return 0.38 - (0.24 * Math.sin(local * Math.PI * 0.5));
        }
        double local = (clamped - 0.48) / 0.52;
        return 0.14 + 0.86 * easeOutCubic(local);
    }

    public static double reviveFlashAlpha(double t) {
        return reviveFlashAlpha(t, ReviveAnimationStyle.AWAKENING);
    }

    public static double reviveFlashAlpha(double t, ReviveAnimationStyle style) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return switch (style) {
            case AWAKENING -> clamped >= 0.55 ? 0.0 : 0.72 * (1.0 - easeOutCubic(clamped / 0.55));
            case SPIRAL_ASCENT -> clamped >= 0.65 ? 0.0 : 0.60 * (1.0 - easeOutCubic(clamped / 0.65));
            case SHOCKWAVE -> clamped >= 0.40 ? 0.0 : 0.88 * (1.0 - easeOutCubic(clamped / 0.40));
            case GENTLE_DRIFT -> clamped >= 0.75 ? 0.0 : 0.42 * (1.0 - easeOutCubic(clamped / 0.75));
            case NONE -> 0.0;
        };
    }

    private static int reviveFlashRgb(ReviveAnimationStyle style) {
        return switch (style) {
            case AWAKENING -> 0xFFF3D0;
            case SPIRAL_ASCENT -> 0xC8F4FF;
            case SHOCKWAVE -> 0xFFFFFF;
            case GENTLE_DRIFT -> 0xDCEEFF;
            case NONE -> 0x000000;
        };
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        if (DeathDimensionClientEvents.isAwaitingEyeOpen()) {
            guiGraphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }

        double eyeProgress = DeathDimensionClientEvents.getEyeOpenProgress();
        if (eyeProgress >= 0.0 && eyeProgress < 1.0) {
            double open = eyeOpenFactor(eyeProgress);
            double closed = 1.0 - open;
            int lidHeight = (int) Math.ceil((height / 2.0) * closed);
            int feather = (int) Math.round(height * 0.12 * (1.0 - open * 0.75));

            int dimAlpha = (int) Math.round(165.0 * closed);
            if (dimAlpha > 0) {
                guiGraphics.fill(0, 0, width, height, (dimAlpha << 24));
            }

            if (lidHeight > 0) {
                guiGraphics.fill(0, 0, width, lidHeight, 0xFF000000);
                guiGraphics.fill(0, height - lidHeight, width, height, 0xFF000000);
            }
            if (feather > 0) {
                int topFeatherEnd = Math.min(height, lidHeight + feather);
                guiGraphics.fillGradient(0, lidHeight, width, topFeatherEnd, 0xFF000000, 0x00000000);

                int bottomFeatherStart = Math.max(0, height - lidHeight - feather);
                guiGraphics.fillGradient(0, bottomFeatherStart, width, height - lidHeight, 0x00000000, 0xFF000000);
            }
        }

        double reviveProgress = DeathDimensionClientEvents.getLocalReviveProgress();
        if (reviveProgress >= 0.0 && reviveProgress < 1.0) {
            ReviveAnimationStyle style = DeathDimensionClientEvents.getLocalReviveStyle();
            double alpha = reviveFlashAlpha(reviveProgress, style);
            int alphaByte = (int) Math.round(alpha * 255.0);
            if (alphaByte > 0) {
                guiGraphics.fill(0, 0, width, height, (alphaByte << 24) | reviveFlashRgb(style));
            }
        }
    }
}
