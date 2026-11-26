package com.reno.deathdimension.client;

import com.reno.deathdimension.DeathDimension;
import com.reno.deathdimension.config.ModConfig;
import com.reno.deathdimension.config.ReviveAnimationStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class TransitionOverlayLayer {
    private static final ResourceLocation OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(DeathDimension.MODID, "transition_overlay");
    private static final int EYELID_CURVE_SLICES = 24;

    private TransitionOverlayLayer() {}

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(OVERLAY_ID, TransitionOverlayLayer::render);
    }

    public static double easeInQuad(double x) {
        double clamped = Math.max(0.0, Math.min(1.0, x));
        return clamped * clamped;
    }

    public static double easeOutCubic(double x) {
        double clamped = Math.max(0.0, Math.min(1.0, x));
        double inv = 1.0 - clamped;
        return 1.0 - inv * inv * inv;
    }

    public static double smootherstep(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return c * c * c * (c * (c * 6.0 - 15.0) + 10.0);
    }

    /**
     * Normalized vertical lift factor in [0.0, 1.0] for the 3.0s respawn orbit animation:
     * 0.0s-0.8s smooth ascent -> 0.8s-2.2s hover with gentle bob -> 2.2s-2.8s descent -> 2.8s-3.0s settle.
     */
    public static double respawnOrbitHeightFactor(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double ascentEnd = 0.8 / 3.0;
        double hoverEnd = 2.2 / 3.0;
        double descentEnd = 2.8 / 3.0;

        if (clamped <= ascentEnd) {
            return easeOutCubic(clamped / ascentEnd);
        }
        if (clamped <= hoverEnd) {
            double local = (clamped - ascentEnd) / (hoverEnd - ascentEnd);
            return 1.0 - 0.045 * 0.5 * (1.0 - Math.cos(local * Math.PI * 2.0));
        }
        if (clamped <= descentEnd) {
            double local = (clamped - hoverEnd) / (descentEnd - hoverEnd);
            return 1.0 - easeInQuad(local);
        }
        return 0.0;
    }

    /**
     * Smooth 360-degree camera orbit angle in degrees over [0.0, 1.0],
     * completing the full rotation at 2.8s with zero angular velocity at start and finish.
     */
    public static double respawnOrbitAngleDegrees(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double orbitEnd = 2.8 / 3.0;
        double u = Math.min(1.0, clamped / orbitEnd);
        return 360.0 * smootherstep(u);
    }

    /**
     * Smooth blend factor in [0.0, 1.0] for tilting the orbiting camera to frame the floating player.
     */
    public static double respawnOrbitPitchBlend(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double ascentEnd = 0.8 / 3.0;
        double hoverEnd = 2.2 / 3.0;
        double descentEnd = 2.8 / 3.0;

        if (clamped <= ascentEnd) {
            return easeOutCubic(clamped / ascentEnd);
        }
        if (clamped <= hoverEnd) {
            return 1.0;
        }
        if (clamped <= descentEnd) {
            double local = (clamped - hoverEnd) / (descentEnd - hoverEnd);
            return 1.0 - smootherstep(local);
        }
        return 0.0;
    }

    /**
     * Third-person camera distance in blocks during the 3.0s respawn orbit animation.
     */
    public static float respawnOrbitCameraDistance(double t, boolean returnToFirstPerson) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double ascentEnd = 0.8 / 3.0;
        double hoverEnd = 2.2 / 3.0;
        double descentEnd = 2.8 / 3.0;

        double startDist = returnToFirstPerson ? 1.4 : 4.0;
        double orbitDist = 4.8;
        double descentDist = returnToFirstPerson ? 1.8 : 4.0;
        double settleDist = returnToFirstPerson ? 0.55 : 4.0;

        double dist;
        if (clamped <= ascentEnd) {
            double f = easeOutCubic(clamped / ascentEnd);
            dist = startDist + (orbitDist - startDist) * f;
        } else if (clamped <= hoverEnd) {
            dist = orbitDist;
        } else if (clamped <= descentEnd) {
            double f = smootherstep((clamped - hoverEnd) / (descentEnd - hoverEnd));
            dist = orbitDist + (descentDist - orbitDist) * f;
        } else {
            double f = easeInQuad((clamped - descentEnd) / (1.0 - descentEnd));
            dist = descentDist + (settleDist - descentDist) * f;
        }
        return (float) dist;
    }

    /**
     * Returns camera slump factor in [0.0, 1.0] for normalized death collapse progress t in [0.0, 1.0]:
     * accelerating gravity drop -> subtle ground-impact settle -> resting slumped.
     */
    public static double deathCameraFallFactor(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        if (clamped <= 0.62) {
            return easeInQuad(clamped / 0.62);
        }
        if (clamped <= 0.80) {
            double local = (clamped - 0.62) / 0.18;
            return 1.0 - 0.06 * Math.sin(local * Math.PI);
        }
        return 1.0;
    }

    /**
     * Returns eyelid openness in [0.0, 1.0] for normalized death progress t in [0.0, 1.0]:
     * open -> heavy droop -> faint weakening flutter -> fully shut before respawn.
     */
    public static double eyeCloseFactor(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        if (clamped <= 0.20) {
            double local = clamped / 0.20;
            return 1.0 - 0.12 * easeInQuad(local);
        }
        if (clamped <= 0.50) {
            double local = (clamped - 0.20) / 0.30;
            return 0.88 - 0.58 * easeOutCubic(local);
        }
        if (clamped <= 0.62) {
            double local = (clamped - 0.50) / 0.12;
            return 0.30 + 0.10 * Math.sin(local * Math.PI * 0.5);
        }
        if (clamped <= 0.92) {
            double local = (clamped - 0.62) / 0.30;
            return 0.40 * (1.0 - easeInQuad(local));
        }
        return 0.0;
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

    private static void renderEyelids(GuiGraphics guiGraphics, int width, int height, double open, boolean deathTint) {
        double clampedOpen = Math.max(0.0, Math.min(1.0, open));
        double closed = 1.0 - clampedOpen;

        if (closed >= 0.999) {
            guiGraphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        if (closed <= 0.001) {
            return;
        }

        int dimAlpha = (int) Math.round((deathTint ? 190.0 : 165.0) * closed);
        if (dimAlpha > 0) {
            int red = deathTint ? (int) Math.round(42.0 * Math.sin(Math.PI * closed)) : 0;
            guiGraphics.fill(0, 0, width, height, (dimAlpha << 24) | (red << 16));
        }

        double baseLid = (height / 2.0) * closed;
        double maxArch = height * 0.11 * clampedOpen * Math.min(1.0, closed * 3.0);
        int feather = (int) Math.round(height * 0.12 * (1.0 - clampedOpen * 0.75));

        for (int i = 0; i < EYELID_CURVE_SLICES; i++) {
            int x0 = (i * width) / EYELID_CURVE_SLICES;
            int x1 = ((i + 1) * width) / EYELID_CURVE_SLICES;
            double centerU = (i + 0.5) / EYELID_CURVE_SLICES;
            double edgeDist = 2.0 * centerU - 1.0; // -1 at left, 0 at center, +1 at right
            int lidHeight = Math.min(height / 2 + 1, (int) Math.ceil(baseLid + maxArch * edgeDist * edgeDist));

            if (lidHeight > 0) {
                guiGraphics.fill(x0, 0, x1, lidHeight, 0xFF000000);
                guiGraphics.fill(x0, height - lidHeight, x1, height, 0xFF000000);
            }
            if (feather > 0) {
                int topFeatherEnd = Math.min(height, lidHeight + feather);
                guiGraphics.fillGradient(x0, lidHeight, x1, topFeatherEnd, 0xFF000000, 0x00000000);

                int bottomFeatherStart = Math.max(0, height - lidHeight - feather);
                guiGraphics.fillGradient(x0, bottomFeatherStart, x1, height - lidHeight, 0x00000000, 0xFF000000);
            }
        }
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        if (DeathDimensionClientEvents.isAwaitingEyeOpen()) {
            guiGraphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }

        double deathCollapseProgress = DeathDimensionClientEvents.getDeathCollapseProgress();
        if (deathCollapseProgress >= 0.0 && ModConfig.enableDeathEyeClose) {
            renderEyelids(guiGraphics, width, height, eyeCloseFactor(deathCollapseProgress), true);
            return;
        }

        double eyeProgress = DeathDimensionClientEvents.getEyeOpenProgress();
        if (eyeProgress >= 0.0 && eyeProgress < 1.0) {
            renderEyelids(guiGraphics, width, height, eyeOpenFactor(eyeProgress), false);
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
