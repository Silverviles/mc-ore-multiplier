# Ore Multiplier

A server-side [Fabric](https://fabricmc.net/) mod for **Minecraft 26.1.1** that gives server operators
full control over ore generation through commands and a single global JSON config file.

Every discovered ore placed feature (vein type) can be individually enabled, disabled, resized,
re-counted and moved to a different height band, on top of global count/size multipliers. Vanilla,
modded and datapack ores are discovered automatically — no hardcoded ore list.

> **Tutorial:** step-by-step examples for every command live in
> **[docs/commands.md](docs/commands.md)**.

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [How it works](#how-it-works)
- [Configuration file](#configuration-file)
- [Commands](#commands)
- [Behavior notes](#behavior-notes)
- [Building from source](#building-from-source)
- [License](#license)

## Features

- **Global, server-wide config** at `config/ore_multiplier.json`, shared by every world on the server.
- **Auto-discovery** of all placed features that use Minecraft's ore feature types, including
  vanilla, modded and datapack ores.
- **Per-variant control** grouped by ore type: each vein variant (lower, upper, buried, large,
  nether, ...) has its own settings.
- Per variant:
  - on/off toggle
  - veins per chunk (`count`)
  - vein size (`size`)
  - minimum and maximum Y
  - height distribution (`uniform`, `triangle`, `trapezoid`)
- **Global multipliers** for count and size applied on top of every per-ore value.
- **Safe vein sizes** — effective size is hard-capped at 80 so oversized veins cannot write outside
  Minecraft's chunk write radius.
- **Commands only** — no GUI and no client-side screens required.
- **Hot reload** from disk with `/oreconfig reload`; command changes are saved and applied instantly.
- Changes affect **newly generated chunks only** — existing terrain is never modified or retro-generated.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | **26.1.1** (Java Edition) |
| Mod loader | [Fabric Loader](https://fabricmc.net/use/installer/) **0.20.1** or newer |
| Fabric API | Required — built against **0.145.4+26.1.1**, use any Fabric API build for 26.1.1 |
| Java | **Java 25** (required by Minecraft 26.1.1) |
| Side | Server-side logic; works on dedicated servers, singleplayer (integrated server) and LAN |

The mod declares `minecraft: 26.1.1` exactly and requires `fabric-api` and `fabricloader >= 0.20.1`
in its metadata. It cannot be installed on 1.21.x or other Minecraft versions.

No client mod is needed on a multiplayer server. For singleplayer, install it in the client's
`mods` folder because the integrated server runs the generation logic.

## Installation

1. Install **Fabric Loader 0.20.1+** for Minecraft **26.1.1**.
2. Put **Fabric API** for 26.1.1 in the `mods/` folder.
3. Put `ore_multiplier-1.0-SNAPSHOT.jar` in the same `mods/` folder.
4. Start the server (or the game for singleplayer).
5. The mod creates `config/ore_multiplier.json` on first start. That file is global and independent
   of the world save.
6. Run `/oreconfig` in-game or from the server console to confirm it loaded.

The first start log line looks like:

```text
[ore_multiplier] Loaded 40 ore variants from ./config/ore_multiplier.json
```

The exact count depends on the vanilla/modded/datapack placed ore features present.

## Quick start

```text
/oreconfig list
/oreconfig show coal
/oreconfig set coal lower count 40
/oreconfig disable copper
/oreconfig multiplier count 2
```

Those commands are enough to inspect the discovered ores, double coal's lower veins, disable copper
entirely and double the amount of every ore. The full walkthrough with expected output, naming rules
and more scenarios is in **[docs/commands.md](docs/commands.md)**.

## How it works

Minecraft generates ores through *placed features*: a feature (the actual vein) plus a list of
placement modifiers (count, height range, biome filter, ...). Biome feature lists hold the original
registry instances, so replacing them globally is not practical.

Instead, the mod intercepts generation at the last moment:

1. On server start (before spawn chunks generate), the config is loaded and every placed feature
   whose feature is an ore feature is discovered and converted into editable defaults.
2. For each configured variant, the mod builds an effective `PlacedFeature`:
   - a rebuilt `ConfiguredFeature` with a new `OreConfiguration` size when the vein size changes,
   - a replaced or inserted `CountPlacement`,
   - a replaced `HeightRangePlacement` built from the configured Y bounds and distribution.
3. A mixin on `PlacedFeature.place`/`placeWithBiomeCheck` swaps the original feature for the effective
   one immediately before placement modifiers run, only for the chunks currently being generated.
4. A second mixin on `BiomeFilter` maps the replacement back to its original registry feature so the
   vanilla "is this feature part of this biome?" check still succeeds.

All replacements are held in an immutable snapshot that is swapped atomically, so chunk generation
on worker threads is safe while the config is reloaded.

Because the swap happens during generation, **existing chunks are never touched**. Only chunks that
have not been generated yet use the new rules.

## Configuration file

Location: `config/ore_multiplier.json` (relative to the server/client run directory).

Example with the relevant fields:

```json
{
  "configVersion": 1,
  "enabled": true,
  "countMultiplier": 1.0,
  "sizeMultiplier": 1.0,
  "ores": {
    "minecraft:coal": {
      "variants": {
        "minecraft:ore_coal_lower": {
          "enabled": true,
          "count": 20,
          "size": 17,
          "minY": 0,
          "maxY": 192,
          "distribution": "trapezoid",
          "plateau": 0
        },
        "minecraft:ore_coal_upper": {
          "enabled": true,
          "count": 30,
          "size": 17,
          "minY": 136,
          "maxY": 319,
          "distribution": "uniform",
          "plateau": 0
        }
      }
    }
  }
}
```

### Top-level fields

| Field | Type | Meaning |
|---|---|---|
| `configVersion` | int | Internal schema version. Leave at `1`. |
| `enabled` | boolean | Master switch. `false` disables all ore overrides and restores vanilla generation. |
| `countMultiplier` | double | Global multiplier applied to every variant `count` (`0.0`–`100.0`; larger values are clamped). |
| `sizeMultiplier` | double | Global multiplier applied to every variant `size` (`0.0`–`100.0`; larger values are clamped). Effective sizes are capped at 80. |
| `ores` | object | Map of ore group key → group. |

### Ore groups and variants

- A **group** is one logical ore type, e.g. `minecraft:coal`, `minecraft:iron`,
  `minecraft:ancient_debris`, `minecraft:andesite`.
- Each group contains one or more **variants** keyed by the full placed feature ID, e.g.
  `minecraft:ore_coal_lower` or `minecraft:ore_iron_small`.

Group names and variant suffixes are derived automatically from the placed feature path
(`ore_coal_lower` → group `coal`, variant `lower`). `ore_debris_*` and `ore_ancient_debris_*` are
merged into the `ancient_debris` group.

The config is merged with discovery on every start/reload: newly added mods or datapacks get new
entries with vanilla-derived defaults, while values you already changed are preserved.

### Variant fields

| Field | Type | Range / values | Meaning |
|---|---|---|---|
| `enabled` | boolean | `true`/`false` | `false` suppresses the variant completely (implemented as count `0`). |
| `count` | int | `-1` … `100000` | Veins per chunk (placement attempts). `-1` means "do not touch the vanilla count modifier". |
| `size` | int | `1` … `80` | Vein size passed to the ore feature. Values above **80** are clamped; see [Vein size limit](#vein-size-limit). |
| `minY` | int | `-2048` … `2048` | Lowest Y for placement (absolute block coordinate). |
| `maxY` | int | `-2048` … `2048` | Highest Y for placement (absolute block coordinate). |
| `distribution` | string | `uniform`, `triangle`, `trapezoid` | Height distribution inside `minY..maxY`. |
| `plateau` | int | `0` … `maxY - minY` | Flat-top width used only by `trapezoid`. |

Distributions:

- `uniform` — equal probability anywhere in the band.
- `triangle` — most veins near the middle of the band, tapering towards both ends.
- `trapezoid` — like triangle but with a flat top; `plateau` controls how wide the flat section is.
  `plateau` is a JSON-only field (the commands can switch the distribution but keep the stored
  plateau).

Values are validated on load: numbers are clamped to their ranges, `minY`/`maxY` are swapped if
reversed, and an unknown distribution falls back to `uniform`.

### Vein size limit

Minecraft only allows a feature to write to the current chunk and its immediate neighbors during the
`features` generation step. A vanilla ore feature's placement box grows with `size`; at size **81**
it can reach 17 blocks from its origin, which is outside that write radius. Minecraft then logs
`Detected setBlock in a far chunk` and rejects the affected blocks.

To prevent that error entirely, **80 is the hard upper bound for every effective vein size**. Larger
values are clamped to 80 whether they come from the JSON file, `/oreconfig set ... size`, or
`sizeMultiplier`. Commands report the clamp to the player. The limit applies to the final effective
size, so `size 40` with `sizeMultiplier 10` still becomes 80, not 400.

Counts are not limited by this rule; the maximum count is `100000`.

### Editing by hand

You can edit the JSON while the server is running and apply it without restarting:

```text
/oreconfig reload
```

The file is rewritten by the mod whenever a command changes something, so command edits and manual
edits end up in the same file.

## Commands

All commands available to operators:

| Command | Purpose |
|---|---|
| `/oreconfig` | Show config file, global state and ore count. |
| `/oreconfig list [<ore>]` | List ore groups and all their variants. |
| `/oreconfig show <ore>` | Show one ore group with Y ranges, dimensions and clickable edit buttons. |
| `/oreconfig set <ore> <variant> <property> <value>` | Change `count`, `size`, `min-y`, `max-y` or `distribution`. |
| `/oreconfig enable <ore> [<variant>]` | Enable one variant or all variants of an ore. |
| `/oreconfig disable <ore> [<variant>]` | Disable one variant or all variants of an ore. |
| `/oreconfig multiplier count <value>` | Set the global count multiplier. |
| `/oreconfig multiplier size <value>` | Set the global size multiplier. |
| `/oreconfig reset <ore> [<variant>]` | Restore one variant, one ore or `all` discovered variants to defaults. |
| `/oreconfig reload` | Re-read and re-apply `config/ore_multiplier.json`. |

Command output includes clickable `[count]`, `[size]`, `[min-y]`, `[max-y]`, `[on]`/`[off]` and
`[reset]` chips that pre-fill the matching command.

See **[docs/commands.md](docs/commands.md)** for the full command reference, argument rules,
permissions, and a tutorial with worked examples.

## Behavior notes

- **New chunks only.** Changes never modify existing terrain and there is no retro-generation.
- **Global config.** `config/ore_multiplier.json` is shared by all worlds on the server. There is no
  per-world config.
- **Auto-discovery.** Any placed feature using an ore feature type is picked up, including ores from
  mods and datapacks. A feature with no count/height modifier still gets editable defaults.
- **Global off switch.** Set `"enabled": false` in the JSON and run `/oreconfig reload` to disable
  all overrides (vanilla behavior) without deleting your settings.
- **Multiplier math.** Effective count is `round(count × countMultiplier)` and effective size is
  `round(size × sizeMultiplier)`, both clamped to valid ranges. Effective size is hard-capped at
  **80** to stay inside Minecraft's one-chunk feature write radius. When `count` is `-1`, the count
  multiplier does not apply because the vanilla count modifier is left untouched.
- **Datapack reloads.** If you run `/reload` (or add ore datapacks) while the server is running, the
  placed feature registry is rebuilt. Run `/oreconfig reload` afterwards to rebind the overrides to
  the new registry instances, or simply restart the server.
- **Modded features.** Third-party ore feature subclasses that cannot be rebuilt keep their original
  size, but count and height overrides still apply.

## Building from source

Requirements: **JDK 25** and **Gradle 9.9+** (the project uses Gradle 9.9 and has no wrapper scripts).

```bash
gradle build
```

The remapped mod jar is written to `build/libs/ore_multiplier-1.0-SNAPSHOT.jar`.

Useful development tasks:

```bash
gradle runServer   # run a Fabric dev server in ./run
gradle runClient   # run a Fabric dev client
```

## License

Copyright (c) 2026 Silverviles. All rights reserved. See [LICENSE.txt](LICENSE.txt).
