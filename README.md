# DeathDimension

A Minecraft mod that introduces a mysterious dimension where players go after death.

**DeathDimension** is a Minecraft mod built for NeoForge 1.21.1. When a player dies, they are automatically transported to a custom Void dimension where all deceased players gather, interact, and wait for their respawn cooldown to expire before returning to the world of the living.

## Features

- **Custom Void Dimension** - An empty Void dimension (`deathdimension:death_dimension`) without terrain, structures, or naturally spawning mobs, enhanced with ambient Void particles.
- **Auto-Respawn (Skip Death Screen)** - Automatically respawns players straight into DeathDimension without showing the vanilla Death Screen (disabled in Hardcore).
- **Cinematic Transitions & Center Titles** - Displays center-screen titles on death and revival, an eye-opening eyelid transition upon waking in DeathDimension, and selectable camera/player revival animations (`AWAKENING`, `SPIRAL_ASCENT`, `SHOCKWAVE`, `GENTLE_DRIFT`, `NONE`).
- **Free Movement & Void Protection** - Grants safe flight inside DeathDimension via NeoForge's `CREATIVE_FLIGHT` attribute and prevents fall/void damage.
- **Respawn Cooldown & Auto/Manual Return** - Supports configurable cooldowns with Action Bar timer, persistent wall-clock tracking across logout/restarts, and switchable `MANUAL` (`/deathdimension return`) or `AUTO` return modes.
- **Logout/Rejoin Safety** - Safely preserves DeathDimension position, flight state, inventory/XP snapshots, and cooldown status when disconnecting inside DeathDimension.
- **OP-Only In-Game Config GUI** - Tabbed configuration menu accessible via `/deathdimension config` or the Mods screen (OP level 2+ verified authoritatively on the server).

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge (`21.1.267`) |
| Java | 21 |
| Build Tool | Gradle 8.8 |

## Configuration

Config file: `config/deathdimension-common.toml` (also editable in-game by OPs via `/deathdimension config`).

```toml
[general]
enableMod = true
enableDeathTeleport = true
enableFreeMovement = true
preventVoidDamage = true

[dimension]
spawnX = 0.0
spawnY = 100.0
spawnZ = 0.0
useSharedSpawn = true
preventDimensionEscape = false

[player]
preserveInventory = true
preserveExperience = true
restorePlayerAbilities = true
returnToDeathLocation = true

[respawn_cooldown]
enableCooldown = true
cooldownDuration = 60
showCooldownMessage = true
showCooldownActionbar = true
allowAdminBypass = true

[return]
enableReturnSystem = true
returnMode = "MANUAL" # MANUAL or AUTO
returnToOriginalDimension = true
returnToOriginalPosition = true
allowAdminReturn = true

[effects]
skipDeathScreen = true
enableVoidParticles = true
enableDeathTitle = true
enableReturnTitle = true
enableEyeOpenEffect = true
enableReviveAnimation = true
reviveAnimationStyle = "AWAKENING" # AWAKENING, SPIRAL_ASCENT, SHOCKWAVE, GENTLE_DRIFT, NONE
```

## Commands

| Command | Permission | Description |
|---|---|---|
| `/deathdimension` | All | Display mod status, cooldown, and spawn info |
| `/deathdimension return` | All | Return to the living world after cooldown expires |
| `/deathdimension return <player>` | OP (2+) | Force-return a player immediately |
| `/deathdimension cooldown` | All | Check remaining respawn cooldown |
| `/deathdimension cooldown set <seconds> [player]` | OP (2+) | Set player cooldown |
| `/deathdimension cooldown reset [player]` | OP (2+) | Reset player cooldown |
| `/deathdimension cooldown bypass <player>` | OP (2+) | Bypass player cooldown |
| `/deathdimension returnmode` | All | Show current return mode (`MANUAL` / `AUTO`) |
| `/deathdimension returnmode <auto\|manual>` | OP (2+) | Switch return mode and save config |
| `/deathdimension config` | OP (2+) | Open the OP-only configuration GUI |
| `/deathdimension tp [player]` | OP (2+) | Teleport to DeathDimension |
| `/deathdimension setspawn [x y z]` | OP (2+) | Set DeathDimension spawn coordinates |
| `/deathdimension reload` | OP (2+) | Reload configuration from disk and sync clients |

## Development

### Build & Self-Check

```bash
./gradlew build
```

Compiled `.jar` output: `build/libs/deathdimension-1.0.0.jar`.

### Run Client / Dedicated Server

```bash
./gradlew runClient
./gradlew runServer
```

## Project Structure

```text
src/main/
├── java/com/reno/deathdimension/
│   ├── DeathDimension.java
│   ├── client/      (DeathDimensionClient, DeathDimensionClientEvents, TransitionOverlayLayer, DeathDimensionConfigScreen)
│   ├── command/     (DeathDimensionCommands)
│   ├── config/      (ModConfig, ReturnMode, ReviveAnimationStyle)
│   ├── cooldown/    (CooldownManager)
│   ├── dimension/   (VoidChunkGenerator, DeathDimensionSavedData)
│   ├── event/       (DeathDimensionEvents)
│   ├── network/     (DeathTransitionPayload, ConfigSyncPayload, ConfigUpdatePayload)
│   ├── player/      (PlayerDeathData, PlayerTeleportHandler)
│   └── registry/    (ModRegistries)
└── resources/
    ├── assets/deathdimension/lang/   (en_us.json, id_id.json)
    └── data/deathdimension/          (dimension, dimension_type, worldgen/biome)
```

---

**DeathDimension** — *Death is not the end. It's another place to wait.*
