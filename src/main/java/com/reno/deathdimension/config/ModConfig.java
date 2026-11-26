package com.reno.deathdimension.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.reno.deathdimension.DeathDimension;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    private static final double MIN_COORD = -30_000_000.0;
    private static final double MAX_COORD = 30_000_000.0;
    private static final double MIN_Y = -64.0;
    private static final double MAX_Y = 320.0;
    private static final long MAX_COOLDOWN = 86400L * 30L;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // [general]
    private static final ModConfigSpec.BooleanValue ENABLE_MOD;
    private static final ModConfigSpec.BooleanValue ENABLE_DEATH_TELEPORT;
    private static final ModConfigSpec.BooleanValue ENABLE_FREE_MOVEMENT;
    private static final ModConfigSpec.BooleanValue PREVENT_VOID_DAMAGE;
    private static final ModConfigSpec.BooleanValue CHECK_UPDATES;
    private static final ModConfigSpec.BooleanValue AUTO_INSTALL_UPDATES;

    // [dimension]
    private static final ModConfigSpec.DoubleValue SPAWN_X;
    private static final ModConfigSpec.DoubleValue SPAWN_Y;
    private static final ModConfigSpec.DoubleValue SPAWN_Z;
    private static final ModConfigSpec.BooleanValue USE_SHARED_SPAWN;
    private static final ModConfigSpec.BooleanValue PREVENT_DIMENSION_ESCAPE;

    // [player]
    private static final ModConfigSpec.BooleanValue PRESERVE_INVENTORY;
    private static final ModConfigSpec.BooleanValue PRESERVE_EXPERIENCE;
    private static final ModConfigSpec.BooleanValue RESTORE_PLAYER_ABILITIES;
    private static final ModConfigSpec.BooleanValue RETURN_TO_DEATH_LOCATION;

    // [respawn_cooldown]
    private static final ModConfigSpec.BooleanValue ENABLE_COOLDOWN;
    private static final ModConfigSpec.LongValue COOLDOWN_DURATION;
    private static final ModConfigSpec.BooleanValue SHOW_COOLDOWN_MESSAGE;
    private static final ModConfigSpec.BooleanValue SHOW_COOLDOWN_ACTIONBAR;
    private static final ModConfigSpec.BooleanValue ALLOW_ADMIN_BYPASS;

    // [return]
    private static final ModConfigSpec.BooleanValue ENABLE_RETURN_SYSTEM;
    private static final ModConfigSpec.EnumValue<ReturnMode> RETURN_MODE;
    private static final ModConfigSpec.BooleanValue RETURN_TO_ORIGINAL_DIMENSION;
    private static final ModConfigSpec.BooleanValue RETURN_TO_ORIGINAL_POSITION;
    private static final ModConfigSpec.BooleanValue ALLOW_ADMIN_RETURN;

    // [effects]
    private static final ModConfigSpec.BooleanValue SKIP_DEATH_SCREEN;
    private static final ModConfigSpec.BooleanValue ENABLE_VOID_PARTICLES;
    private static final ModConfigSpec.BooleanValue ENABLE_DEATH_TITLE;
    private static final ModConfigSpec.BooleanValue ENABLE_RETURN_TITLE;
    private static final ModConfigSpec.BooleanValue ENABLE_EYE_OPEN_EFFECT;
    private static final ModConfigSpec.BooleanValue ENABLE_REVIVE_ANIMATION;
    private static final ModConfigSpec.EnumValue<ReviveAnimationStyle> REVIVE_ANIMATION_STYLE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("General settings").push("general");
        ENABLE_MOD = BUILDER.comment("Enable or disable the DeathDimension mod mechanics.")
                .define("enableMod", true);
        ENABLE_DEATH_TELEPORT = BUILDER.comment("Automatically teleport players to DeathDimension after dying and respawning.")
                .define("enableDeathTeleport", true);
        ENABLE_FREE_MOVEMENT = BUILDER.comment("Allow players to fly freely inside DeathDimension.")
                .define("enableFreeMovement", true);
        PREVENT_VOID_DAMAGE = BUILDER.comment("Prevent void damage inside DeathDimension.")
                .define("preventVoidDamage", true);
        CHECK_UPDATES = BUILDER.comment("Check GitHub for newer DeathDimension releases on the Main Menu and notify operators in chat.")
                .define("checkUpdates", true);
        AUTO_INSTALL_UPDATES = BUILDER.comment("Automatically download and install DeathDimension updates from GitHub when detected on the Main Menu.")
                .define("autoInstallUpdates", true);
        BUILDER.pop();

        BUILDER.comment("DeathDimension spawn and boundary settings").push("dimension");
        SPAWN_X = BUILDER.comment("Default spawn X coordinate inside DeathDimension.")
                .defineInRange("spawnX", 0.0, MIN_COORD, MAX_COORD);
        SPAWN_Y = BUILDER.comment("Default spawn Y coordinate inside DeathDimension.")
                .defineInRange("spawnY", 100.0, MIN_Y, MAX_Y);
        SPAWN_Z = BUILDER.comment("Default spawn Z coordinate inside DeathDimension.")
                .defineInRange("spawnZ", 0.0, MIN_COORD, MAX_COORD);
        USE_SHARED_SPAWN = BUILDER.comment("Use one shared spawn point for all players entering DeathDimension.")
                .define("useSharedSpawn", true);
        PREVENT_DIMENSION_ESCAPE = BUILDER.comment("Prevent non-admin players from leaving DeathDimension except through the return system.")
                .define("preventDimensionEscape", false);
        BUILDER.pop();

        BUILDER.comment("Player preservation and ability settings").push("player");
        PRESERVE_INVENTORY = BUILDER.comment("Preserve player inventory across death when keepInventory is disabled.")
                .define("preserveInventory", true);
        PRESERVE_EXPERIENCE = BUILDER.comment("Preserve player experience across death when keepInventory is disabled.")
                .define("preserveExperience", true);
        RESTORE_PLAYER_ABILITIES = BUILDER.comment("Restore the player's pre-death movement abilities when leaving DeathDimension.")
                .define("restorePlayerAbilities", true);
        RETURN_TO_DEATH_LOCATION = BUILDER.comment("Return players to their death coordinates if returnToOriginalPosition is enabled.")
                .define("returnToDeathLocation", true);
        BUILDER.pop();

        BUILDER.comment("Respawn cooldown settings").push("respawn_cooldown");
        ENABLE_COOLDOWN = BUILDER.comment("Enable respawn cooldown before returning from DeathDimension.")
                .define("enableCooldown", true);
        COOLDOWN_DURATION = BUILDER.comment("Default respawn cooldown duration in seconds.")
                .defineInRange("cooldownDuration", 60L, 0L, MAX_COOLDOWN);
        SHOW_COOLDOWN_MESSAGE = BUILDER.comment("Send chat notifications when cooldown starts and finishes.")
                .define("showCooldownMessage", true);
        SHOW_COOLDOWN_ACTIONBAR = BUILDER.comment("Display remaining cooldown timer on the player's Action Bar.")
                .define("showCooldownActionbar", true);
        ALLOW_ADMIN_BYPASS = BUILDER.comment("Allow administrators (op level 2+) to bypass respawn cooldown.")
                .define("allowAdminBypass", true);
        BUILDER.pop();

        BUILDER.comment("Return system settings").push("return");
        ENABLE_RETURN_SYSTEM = BUILDER.comment("Enable returning from DeathDimension.")
                .define("enableReturnSystem", true);
        RETURN_MODE = BUILDER.comment("Return mode after cooldown ends: MANUAL (use /deathdimension return) or AUTO (respawn automatically).")
                .defineEnum("returnMode", ReturnMode.MANUAL);
        RETURN_TO_ORIGINAL_DIMENSION = BUILDER.comment("Return players to the dimension they died in (falls back to Overworld).")
                .define("returnToOriginalDimension", true);
        RETURN_TO_ORIGINAL_POSITION = BUILDER.comment("Return players to their saved death position instead of world/bed spawn.")
                .define("returnToOriginalPosition", true);
        ALLOW_ADMIN_RETURN = BUILDER.comment("Allow administrators to return other players immediately.")
                .define("allowAdminReturn", true);
        BUILDER.pop();

        BUILDER.comment("Visual, particle, title, and transition effects").push("effects");
        SKIP_DEATH_SCREEN = BUILDER.comment("Automatically respawn players into DeathDimension without showing the Death Screen (disabled in Hardcore).")
                .define("skipDeathScreen", true);
        ENABLE_VOID_PARTICLES = BUILDER.comment("Spawn ambient Void particles around players inside DeathDimension.")
                .define("enableVoidParticles", true);
        ENABLE_DEATH_TITLE = BUILDER.comment("Display a center-screen title when dying and entering DeathDimension.")
                .define("enableDeathTitle", true);
        ENABLE_RETURN_TITLE = BUILDER.comment("Display a center-screen title when returning to the living world.")
                .define("enableReturnTitle", true);
        ENABLE_EYE_OPEN_EFFECT = BUILDER.comment("Play an eye-opening transition when awakening in DeathDimension.")
                .define("enableEyeOpenEffect", true);
        ENABLE_REVIVE_ANIMATION = BUILDER.comment("Play camera and player animations when returning to life.")
                .define("enableReviveAnimation", true);
        REVIVE_ANIMATION_STYLE = BUILDER.comment("Animation style when returning to life: AWAKENING, SPIRAL_ASCENT, SHOCKWAVE, GENTLE_DRIFT, or NONE.")
                .defineEnum("reviveAnimationStyle", ReviveAnimationStyle.AWAKENING);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // Cached runtime values
    public static volatile boolean enableMod = true;
    public static volatile boolean enableDeathTeleport = true;
    public static volatile boolean enableFreeMovement = true;
    public static volatile boolean preventVoidDamage = true;
    public static volatile boolean checkUpdates = true;
    public static volatile boolean autoInstallUpdates = true;

    public static volatile double spawnX = 0.0;
    public static volatile double spawnY = 100.0;
    public static volatile double spawnZ = 0.0;
    public static volatile boolean useSharedSpawn = true;
    public static volatile boolean preventDimensionEscape = false;

    public static volatile boolean preserveInventory = true;
    public static volatile boolean preserveExperience = true;
    public static volatile boolean restorePlayerAbilities = true;
    public static volatile boolean returnToDeathLocation = true;

    public static volatile boolean enableCooldown = true;
    public static volatile long cooldownDuration = 60L;
    public static volatile boolean showCooldownMessage = true;
    public static volatile boolean showCooldownActionbar = true;
    public static volatile boolean allowAdminBypass = true;

    public static volatile boolean enableReturnSystem = true;
    public static volatile ReturnMode returnMode = ReturnMode.MANUAL;
    public static volatile boolean returnToOriginalDimension = true;
    public static volatile boolean returnToOriginalPosition = true;
    public static volatile boolean allowAdminReturn = true;

    public static volatile boolean skipDeathScreen = true;
    public static volatile boolean enableVoidParticles = true;
    public static volatile boolean enableDeathTitle = true;
    public static volatile boolean enableReturnTitle = true;
    public static volatile boolean enableEyeOpenEffect = true;
    public static volatile boolean enableReviveAnimation = true;
    public static volatile ReviveAnimationStyle reviveAnimationStyle = ReviveAnimationStyle.AWAKENING;

    private static volatile net.neoforged.fml.config.ModConfig loadedConfig;

    private ModConfig() {}

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        loadedConfig = event.getConfig();
        bake();
    }

    public static boolean reloadFromDisk() {
        net.neoforged.fml.config.ModConfig cfg = loadedConfig;
        if (cfg != null && cfg.getLoadedConfig() != null && cfg.getLoadedConfig().config() instanceof CommentedFileConfig fileConfig) {
            try {
                fileConfig.load();
                bake();
                return true;
            } catch (Exception e) {
                DeathDimension.LOGGER.error("Failed to reload DeathDimension config file", e);
                return false;
            }
        }
        if (SPEC.isLoaded()) {
            bake();
            return true;
        }
        return false;
    }

    public static void setReturnMode(ReturnMode mode) {
        ReturnMode safe = mode != null ? mode : ReturnMode.MANUAL;
        returnMode = safe;
        if (SPEC.isLoaded()) {
            RETURN_MODE.set(safe);
            SPEC.save();
        }
    }

    public static void setSpawnPosition(double x, double y, double z) {
        double safeX = clampFinite(x, MIN_COORD, MAX_COORD, 0.0);
        double safeY = clampFinite(y, MIN_Y, MAX_Y, 100.0);
        double safeZ = clampFinite(z, MIN_COORD, MAX_COORD, 0.0);
        spawnX = safeX;
        spawnY = safeY;
        spawnZ = safeZ;
        if (SPEC.isLoaded()) {
            SPAWN_X.set(safeX);
            SPAWN_Y.set(safeY);
            SPAWN_Z.set(safeZ);
            SPEC.save();
        }
    }

    public static ConfigSnapshot captureSnapshot() {
        return new ConfigSnapshot(
                enableMod, enableDeathTeleport, enableFreeMovement, preventVoidDamage, checkUpdates, autoInstallUpdates,
                spawnX, spawnY, spawnZ, useSharedSpawn, preventDimensionEscape,
                preserveInventory, preserveExperience, restorePlayerAbilities, returnToDeathLocation,
                enableCooldown, cooldownDuration, showCooldownMessage, showCooldownActionbar, allowAdminBypass,
                enableReturnSystem, returnMode, returnToOriginalDimension, returnToOriginalPosition, allowAdminReturn,
                skipDeathScreen, enableVoidParticles, enableDeathTitle, enableReturnTitle,
                enableEyeOpenEffect, enableReviveAnimation, reviveAnimationStyle
        );
    }

    public static void applySnapshot(ConfigSnapshot raw, boolean persistToDisk) {
        ConfigSnapshot s = raw.sanitized();

        enableMod = s.enableMod();
        enableDeathTeleport = s.enableDeathTeleport();
        enableFreeMovement = s.enableFreeMovement();
        preventVoidDamage = s.preventVoidDamage();
        checkUpdates = s.checkUpdates();
        autoInstallUpdates = s.autoInstallUpdates();

        spawnX = s.spawnX();
        spawnY = s.spawnY();
        spawnZ = s.spawnZ();
        useSharedSpawn = s.useSharedSpawn();
        preventDimensionEscape = s.preventDimensionEscape();

        preserveInventory = s.preserveInventory();
        preserveExperience = s.preserveExperience();
        restorePlayerAbilities = s.restorePlayerAbilities();
        returnToDeathLocation = s.returnToDeathLocation();

        enableCooldown = s.enableCooldown();
        cooldownDuration = s.cooldownDuration();
        showCooldownMessage = s.showCooldownMessage();
        showCooldownActionbar = s.showCooldownActionbar();
        allowAdminBypass = s.allowAdminBypass();

        enableReturnSystem = s.enableReturnSystem();
        returnMode = s.returnMode();
        returnToOriginalDimension = s.returnToOriginalDimension();
        returnToOriginalPosition = s.returnToOriginalPosition();
        allowAdminReturn = s.allowAdminReturn();

        skipDeathScreen = s.skipDeathScreen();
        enableVoidParticles = s.enableVoidParticles();
        enableDeathTitle = s.enableDeathTitle();
        enableReturnTitle = s.enableReturnTitle();
        enableEyeOpenEffect = s.enableEyeOpenEffect();
        enableReviveAnimation = s.enableReviveAnimation();
        reviveAnimationStyle = s.reviveAnimationStyle();

        if (persistToDisk && SPEC.isLoaded()) {
            ENABLE_MOD.set(s.enableMod());
            ENABLE_DEATH_TELEPORT.set(s.enableDeathTeleport());
            ENABLE_FREE_MOVEMENT.set(s.enableFreeMovement());
            PREVENT_VOID_DAMAGE.set(s.preventVoidDamage());
            CHECK_UPDATES.set(s.checkUpdates());
            AUTO_INSTALL_UPDATES.set(s.autoInstallUpdates());

            SPAWN_X.set(s.spawnX());
            SPAWN_Y.set(s.spawnY());
            SPAWN_Z.set(s.spawnZ());
            USE_SHARED_SPAWN.set(s.useSharedSpawn());
            PREVENT_DIMENSION_ESCAPE.set(s.preventDimensionEscape());

            PRESERVE_INVENTORY.set(s.preserveInventory());
            PRESERVE_EXPERIENCE.set(s.preserveExperience());
            RESTORE_PLAYER_ABILITIES.set(s.restorePlayerAbilities());
            RETURN_TO_DEATH_LOCATION.set(s.returnToDeathLocation());

            ENABLE_COOLDOWN.set(s.enableCooldown());
            COOLDOWN_DURATION.set(s.cooldownDuration());
            SHOW_COOLDOWN_MESSAGE.set(s.showCooldownMessage());
            SHOW_COOLDOWN_ACTIONBAR.set(s.showCooldownActionbar());
            ALLOW_ADMIN_BYPASS.set(s.allowAdminBypass());

            ENABLE_RETURN_SYSTEM.set(s.enableReturnSystem());
            RETURN_MODE.set(s.returnMode());
            RETURN_TO_ORIGINAL_DIMENSION.set(s.returnToOriginalDimension());
            RETURN_TO_ORIGINAL_POSITION.set(s.returnToOriginalPosition());
            ALLOW_ADMIN_RETURN.set(s.allowAdminReturn());

            SKIP_DEATH_SCREEN.set(s.skipDeathScreen());
            ENABLE_VOID_PARTICLES.set(s.enableVoidParticles());
            ENABLE_DEATH_TITLE.set(s.enableDeathTitle());
            ENABLE_RETURN_TITLE.set(s.enableReturnTitle());
            ENABLE_EYE_OPEN_EFFECT.set(s.enableEyeOpenEffect());
            ENABLE_REVIVE_ANIMATION.set(s.enableReviveAnimation());
            REVIVE_ANIMATION_STYLE.set(s.reviveAnimationStyle());

            SPEC.save();
        }
    }

    private static void bake() {
        enableMod = ENABLE_MOD.get();
        enableDeathTeleport = ENABLE_DEATH_TELEPORT.get();
        enableFreeMovement = ENABLE_FREE_MOVEMENT.get();
        preventVoidDamage = PREVENT_VOID_DAMAGE.get();
        checkUpdates = CHECK_UPDATES.get();
        autoInstallUpdates = AUTO_INSTALL_UPDATES.get();

        spawnX = SPAWN_X.get();
        spawnY = SPAWN_Y.get();
        spawnZ = SPAWN_Z.get();
        useSharedSpawn = USE_SHARED_SPAWN.get();
        preventDimensionEscape = PREVENT_DIMENSION_ESCAPE.get();

        preserveInventory = PRESERVE_INVENTORY.get();
        preserveExperience = PRESERVE_EXPERIENCE.get();
        restorePlayerAbilities = RESTORE_PLAYER_ABILITIES.get();
        returnToDeathLocation = RETURN_TO_DEATH_LOCATION.get();

        enableCooldown = ENABLE_COOLDOWN.get();
        cooldownDuration = COOLDOWN_DURATION.get();
        showCooldownMessage = SHOW_COOLDOWN_MESSAGE.get();
        showCooldownActionbar = SHOW_COOLDOWN_ACTIONBAR.get();
        allowAdminBypass = ALLOW_ADMIN_BYPASS.get();

        enableReturnSystem = ENABLE_RETURN_SYSTEM.get();
        returnMode = RETURN_MODE.get();
        returnToOriginalDimension = RETURN_TO_ORIGINAL_DIMENSION.get();
        returnToOriginalPosition = RETURN_TO_ORIGINAL_POSITION.get();
        allowAdminReturn = ALLOW_ADMIN_RETURN.get();

        skipDeathScreen = SKIP_DEATH_SCREEN.get();
        enableVoidParticles = ENABLE_VOID_PARTICLES.get();
        enableDeathTitle = ENABLE_DEATH_TITLE.get();
        enableReturnTitle = ENABLE_RETURN_TITLE.get();
        enableEyeOpenEffect = ENABLE_EYE_OPEN_EFFECT.get();
        enableReviveAnimation = ENABLE_REVIVE_ANIMATION.get();
        reviveAnimationStyle = REVIVE_ANIMATION_STYLE.get();
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    public record ConfigSnapshot(
            boolean enableMod,
            boolean enableDeathTeleport,
            boolean enableFreeMovement,
            boolean preventVoidDamage,
            boolean checkUpdates,
            boolean autoInstallUpdates,
            double spawnX,
            double spawnY,
            double spawnZ,
            boolean useSharedSpawn,
            boolean preventDimensionEscape,
            boolean preserveInventory,
            boolean preserveExperience,
            boolean restorePlayerAbilities,
            boolean returnToDeathLocation,
            boolean enableCooldown,
            long cooldownDuration,
            boolean showCooldownMessage,
            boolean showCooldownActionbar,
            boolean allowAdminBypass,
            boolean enableReturnSystem,
            ReturnMode returnMode,
            boolean returnToOriginalDimension,
            boolean returnToOriginalPosition,
            boolean allowAdminReturn,
            boolean skipDeathScreen,
            boolean enableVoidParticles,
            boolean enableDeathTitle,
            boolean enableReturnTitle,
            boolean enableEyeOpenEffect,
            boolean enableReviveAnimation,
            ReviveAnimationStyle reviveAnimationStyle
    ) {
        public ConfigSnapshot sanitized() {
            return new ConfigSnapshot(
                    enableMod, enableDeathTeleport, enableFreeMovement, preventVoidDamage, checkUpdates, autoInstallUpdates,
                    clampFinite(spawnX, MIN_COORD, MAX_COORD, 0.0),
                    clampFinite(spawnY, MIN_Y, MAX_Y, 100.0),
                    clampFinite(spawnZ, MIN_COORD, MAX_COORD, 0.0),
                    useSharedSpawn, preventDimensionEscape,
                    preserveInventory, preserveExperience, restorePlayerAbilities, returnToDeathLocation,
                    enableCooldown, Math.max(0L, Math.min(MAX_COOLDOWN, cooldownDuration)),
                    showCooldownMessage, showCooldownActionbar, allowAdminBypass,
                    enableReturnSystem, returnMode != null ? returnMode : ReturnMode.MANUAL,
                    returnToOriginalDimension, returnToOriginalPosition, allowAdminReturn,
                    skipDeathScreen, enableVoidParticles, enableDeathTitle, enableReturnTitle,
                    enableEyeOpenEffect, enableReviveAnimation,
                    reviveAnimationStyle != null ? reviveAnimationStyle : ReviveAnimationStyle.AWAKENING
            );
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enableMod);
            buf.writeBoolean(enableDeathTeleport);
            buf.writeBoolean(enableFreeMovement);
            buf.writeBoolean(preventVoidDamage);
            buf.writeBoolean(checkUpdates);
            buf.writeBoolean(autoInstallUpdates);
            buf.writeDouble(spawnX);
            buf.writeDouble(spawnY);
            buf.writeDouble(spawnZ);
            buf.writeBoolean(useSharedSpawn);
            buf.writeBoolean(preventDimensionEscape);
            buf.writeBoolean(preserveInventory);
            buf.writeBoolean(preserveExperience);
            buf.writeBoolean(restorePlayerAbilities);
            buf.writeBoolean(returnToDeathLocation);
            buf.writeBoolean(enableCooldown);
            buf.writeVarLong(cooldownDuration);
            buf.writeBoolean(showCooldownMessage);
            buf.writeBoolean(showCooldownActionbar);
            buf.writeBoolean(allowAdminBypass);
            buf.writeBoolean(enableReturnSystem);
            buf.writeVarInt(returnMode != null ? returnMode.ordinal() : 0);
            buf.writeBoolean(returnToOriginalDimension);
            buf.writeBoolean(returnToOriginalPosition);
            buf.writeBoolean(allowAdminReturn);
            buf.writeBoolean(skipDeathScreen);
            buf.writeBoolean(enableVoidParticles);
            buf.writeBoolean(enableDeathTitle);
            buf.writeBoolean(enableReturnTitle);
            buf.writeBoolean(enableEyeOpenEffect);
            buf.writeBoolean(enableReviveAnimation);
            buf.writeVarInt(reviveAnimationStyle != null ? reviveAnimationStyle.ordinal() : 0);
        }

        public static ConfigSnapshot read(FriendlyByteBuf buf) {
            return new ConfigSnapshot(
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarLong(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    ReturnMode.fromOrdinal(buf.readVarInt()),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    ReviveAnimationStyle.fromOrdinal(buf.readVarInt())
            ).sanitized();
        }
    }
}
