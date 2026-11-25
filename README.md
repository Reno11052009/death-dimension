# DeathDimension

A Minecraft mod that introduces a mysterious dimension where players go after death.

**DeathDimension** is a Minecraft mod built for NeoForge 1.21.1. When a player dies, they are transported to a custom Void dimension where all deceased players gather, interact, and wait for their respawn cooldown to expire.

Instead of immediately returning to the world, players enter a shared dimension beyond death.

## Features

- **Custom Void Dimension** - A completely empty dimension without terrain, structures, or naturally spawning mobs.
- **Automatic Death Teleportation** - Players are transported to DeathDimension after dying and respawning.
- **Shared Multiplayer Dimension** - All deceased players gather in the same dimension.
- **Free Movement** - Players can move freely throughout the Void.
- **Respawn Cooldown** - Configurable waiting time before players can return.
- **Cooldown Action Bar** - Displays the remaining cooldown time.
- **Player Persistence** - Preserves player state and cooldown information across logout and server restarts.
- **Return System** - Allows players to return to their original world after the cooldown.
- **Admin Commands** - Provides commands for managing the dimension and player cooldowns.

## How It Works

```text
       Player dies
            |
            v
      Player respawns
            |
            v
   Teleport to DeathDimension
            |
            v
     Respawn Cooldown
            |
            v
  Meet other deceased players
            |
            v
     Cooldown expires
            |
            v
     Return to Overworld
```

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge |
| Java | 21 |
| Build Tool | Gradle |

## Installation

1. Install Minecraft 1.21.1.
2. Install the compatible NeoForge version.
3. Download the DeathDimension `.jar` file from the Releases section.
4. Place the downloaded file inside your Minecraft `mods` folder.
5. Launch Minecraft using the NeoForge profile.

For multiplayer servers, install the mod on the server. Install it on clients too if the mod requires client-side components.

## Configuration

DeathDimension provides configurable settings for cooldown, movement, dimension behavior, and player management.

Example configuration:

```toml
[general]
enableMod = true
enableDeathTeleport = true
enableFreeMovement = true
preventVoidDamage = true

[dimension]
spawnX = 0
spawnY = 100
spawnZ = 0
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
returnToOriginalDimension = true
returnToOriginalPosition = true
allowAdminReturn = true
```

`cooldownDuration` is measured in seconds.

The actual configuration file and available options may change during development.

## Commands

Main command:

```text
/deathdimension
```

| Command | Description |
|---|---|
| `/deathdimension` | Display mod information |
| `/deathdimension return` | Return to the original world |
| `/deathdimension tp` | Teleport to DeathDimension |
| `/deathdimension setspawn` | Set the dimension spawn |
| `/deathdimension reload` | Reload configuration |
| `/deathdimension cooldown` | Check remaining cooldown |
| `/deathdimension cooldown set <seconds>` | Set player cooldown |
| `/deathdimension cooldown reset` | Reset player cooldown |
| `/deathdimension cooldown bypass <player>` | Bypass player cooldown |

Administrative commands require appropriate permissions.

## Development

### Clone Repository

```bash
git clone https://github.com/YOUR_USERNAME/DeathDimension.git
cd DeathDimension
```

### Build

Linux:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

The compiled mod will be available in:

```text
build/libs/
```

### Run Minecraft Client

Linux:

```bash
./gradlew runClient
```

Windows:

```powershell
.\gradlew.bat runClient
```

### Run Minecraft Server

Use the configured NeoForge development server run configuration when testing dedicated server compatibility.

## Project Structure

```text
deathdimension/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/reno/deathdimension/
│       │       ├── registry/
│       │       ├── dimension/
│       │       ├── event/
│       │       ├── player/
│       │       ├── cooldown/
│       │       ├── command/
│       │       ├── config/
│       │       └── util/
│       │
│       └── resources/
│           ├── META-INF/
│           ├── assets/
│           └── data/
│
├── gradle/
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
```

## Roadmap

- [ ] Project initialization with NeoForge 1.21.1.
- [ ] Custom DeathDimension registration.
- [ ] Void world generation.
- [ ] Player death detection.
- [ ] Automatic teleportation after respawn.
- [ ] Free movement system.
- [ ] Void damage prevention.
- [ ] Multiplayer interaction.
- [ ] Respawn cooldown system.
- [ ] Cooldown Action Bar display.
- [ ] Player data persistence.
- [ ] Return system.
- [ ] Administrative commands.
- [ ] Configuration system.
- [ ] Dedicated server testing.
- [ ] Performance optimization.
- [ ] First stable release.

## Compatibility

- Minecraft: 1.21.1
- Mod Loader: NeoForge
- Java: 21

Compatibility with other Minecraft versions and mod loaders is not currently guaranteed.

## Contributing

Contributions, bug reports, and feature suggestions are welcome.

If you find a bug or have an idea for improvement, feel free to open an issue or submit a pull request.

## License

License information will be added before the first public release.

---

**DeathDimension**

*Death is not the end. It's another place to wait.*
