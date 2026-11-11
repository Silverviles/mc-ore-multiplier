# Ore Multiplier — Command Reference & Tutorial

This document is the complete guide to controlling ore generation with Ore Multiplier.
It starts with the essentials and then walks through common tasks with copy-paste examples.

> New here? Read [Inspect your ores](#1-inspect-your-ores) and
> [Disable an ore](#2-disable-an-ore-entirely), then jump to the scenario you need.

## Contents

- [Permissions](#permissions)
- [Naming ores and variants](#naming-ores-and-variants)
- [Command reference](#command-reference)
  - [`/oreconfig`](#oreconfig)
  - [`/oreconfig list [<ore>]`](#oreconfig-list-ore)
  - [`/oreconfig show <ore>`](#oreconfig-show-ore)
  - [`/oreconfig set <ore> <variant> <property> <value>`](#oreconfig-set-ore-variant-property-value)
  - [`/oreconfig enable <ore> [<variant>]` / `disable`](#oreconfig-enable-ore-variant--disable)
  - [`/oreconfig multiplier count|size <value>`](#oreconfig-multiplier-countsize-value)
  - [`/oreconfig reset <ore> [<variant>]`](#oreconfig-reset-ore-variant)
  - [`/oreconfig reload`](#oreconfig-reload)
- [Tutorial](#tutorial)
  - [1. Inspect your ores](#1-inspect-your-ores)
  - [2. Disable an ore entirely](#2-disable-an-ore-entirely)
  - [3. Make an ore more common](#3-make-an-ore-more-common)
  - [4. Make veins bigger](#4-make-veins-bigger)
  - [5. Limit an ore to a height band](#5-limit-an-ore-to-a-height-band)
  - [6. Change the height distribution](#6-change-the-height-distribution)
  - [7. Scale every ore at once](#7-scale-every-ore-at-once)
  - [8. Restore defaults](#8-restore-defaults)
  - [9. Edit the JSON by hand](#9-edit-the-json-by-hand)
  - [10. Full example: a richer world](#10-full-example-a-richer-world)
- [How values combine](#how-values-combine)
- [Vein size hard limit](#vein-size-hard-limit)
- [Troubleshooting](#troubleshooting)

## Permissions

`/oreconfig` is an operator command:

- It requires vanilla permission level **2** (`GAMEMASTERS`), i.e. normal OPs.
- The server console and RCON always have permission.

Make yourself OP (`op <player>` or set `op-permission-level=2` or higher).

In chat, commands start with `/`. When typing into the server console or an RCON client, omit the
leading slash (`oreconfig list`).

## Naming ores and variants

### Ore names

The `<ore>` argument accepts several forms:

| Form | Example |
|---|---|
| Short group name | `coal` |
| Full group key | `minecraft:coal` |
| With the `ore_` prefix | `ore_coal` |
| Modded/namespaced group | `mymod:tin` |

Short names are matched against discovered groups. For ancient debris, `debris`,
`ancient_debris`, `minecraft:ancient_debris`, `ore_ancient_debris` and `minecraft:ore_debris` all
work.

If a short name matches more than one group (for example a mod adds both `tin` and `deepslate_tin`),
the command reports an ambiguous name — use the full key, e.g. `mymod:tin`.

### Variant names

A group contains one or more **variants**, one per placed feature. The `<variant>` argument accepts:

| Form | Example for `minecraft:ore_coal_lower` |
|---|---|
| Variant suffix | `lower` |
| Full placed feature ID | `minecraft:ore_coal_lower` |
| Path without namespace | `ore_coal_lower` |
| All variants of the group | `all` |

The variant suffix is the part left after removing `ore_<group>` — for example `ore_iron_small` →
`small`, `ore_diamond` → `main`, `ore_gold_nether` → `nether`.

Tab completion suggests valid ore and variant names. `/oreconfig show <ore>` lists every variant with
its suffix.

## Command reference

### `/oreconfig`

Prints the config file path, global state and the number of discovered ore features.

```text
/oreconfig
```

Example output:

```text
[OreConfig] Ore configuration
File: ./config/ore_multiplier.json
State: enabled | count x1 | size x1 | 40 ore features
Try /oreconfig list, /oreconfig show <ore>, or /oreconfig set <ore> <variant> <property> <value>.
```

### `/oreconfig list [<ore>]`

Lists all ore groups and their variants, or only one group when `<ore>` is given.

```text
/oreconfig list
/oreconfig list diamond
```

Each line shows the variant suffix, count, size, Y range, distribution, on/off state and clickable
edit chips. When a multiplier changes an effective value, it is shown in parentheses, for example
`count 20 (40)` or `size 33 (80)`.

```text
Diamond  (minecraft:diamond)
  main     count 7            size 4    Y -64..16  trapezoid  on  [count] [size] [min-y] [max-y] [off] [reset]
  buried   count 4            size 8    Y -64..16  trapezoid  on  [count] [size] [min-y] [max-y] [off] [reset]
  large    count none         size 12   Y -64..16  trapezoid  on  [count] [size] [min-y] [max-y] [off] [reset]
  medium   count 2            size 8    Y -64..-4  uniform    on  [count] [size] [min-y] [max-y] [off] [reset]
```

`count none` means the placed feature has no count modifier (for example it uses a rarity filter
instead); the mod leaves it untouched unless you set a count.

### `/oreconfig show <ore>`

Same as `list <ore>`, but also prints the dimension each variant belongs to. Use this to check your
changes.

```text
/oreconfig show coal
```

### `/oreconfig set <ore> <variant> <property> <value>`

The main editing command. It changes one property of one variant (or every variant with `all`) and
saves/applies immediately.

```text
/oreconfig set <ore> <variant> <property> <value>
```

Properties:

| Property | Value range | Description |
|---|---|---|
| `count` | `-1` … `100000` | Veins per chunk. `-1` restores "no count modifier"; `0` disables generation. Values outside the range are clamped. |
| `size` | `1` … `80` | Vein size (blocks). Values above **80** are clamped to the hard limit. |
| `min-y` | `-2048` … `2048` | Lowest absolute Y. |
| `max-y` | `-2048` … `2048` | Highest absolute Y. |
| `distribution` | `uniform`, `triangle`, `trapezoid` | Height distribution inside the Y band. |

Examples:

```text
/oreconfig set coal lower count 40
/oreconfig set coal all count 30
/oreconfig set iron upper size 20
/oreconfig set diamond main min-y -64
/oreconfig set diamond main max-y -32
/oreconfig set copper main distribution triangle
```

Notes:

- `count` is an **absolute value**, not a multiplier. Use
  [`/oreconfig multiplier count`](#oreconfig-multiplier-countsize-value) to scale everything.
- **Vein size is hard-capped at 80.** A vanilla ore feature with size 81 can reach outside
  Minecraft's one-chunk feature write radius and trigger `Detected setBlock in a far chunk`. If you
  enter a larger size, the command stores 80 and tells you that the value was clamped.
- Numeric values are clamped to their allowed ranges on every command and when the config is loaded.
  A command that had to clamp a value includes a message with the limit that was applied.
- When a variant originally had **no** count modifier (`count none`), setting a count inserts one.
  Use `count -1` to remove it again and return to the vanilla behavior.
- Changing `distribution` to `triangle` zeroes the trapezoid plateau. To use a real flat-top
  trapezoid, set `plateau` in the JSON (see [Edit the JSON by hand](#9-edit-the-json-by-hand)).
- `min-y`/`max-y` are absolute block coordinates, even for ores whose vanilla definition used
  relative anchors like "above bottom" or "below top"; the mod resolves those into absolute Y values
  during discovery.

### `/oreconfig enable <ore> [<variant>]` / `disable`

Turn a whole ore, or a single variant, on or off without losing its other settings.

```text
/oreconfig disable copper          # all copper variants
/oreconfig disable copper large    # only minecraft:ore_copper_large
/oreconfig enable copper           # re-enable all copper variants
```

Disabling is implemented as a count of `0`: no new veins are generated, but the stored count, size
and Y values remain so you can enable it again later. Disabled variants show `off` in
`/oreconfig list`.

> The master switch for the whole mod is the `"enabled"` field in the JSON file. Use
> `"enabled": false` + `/oreconfig reload` to fall back to fully vanilla generation.

### `/oreconfig multiplier count|size <value>`

Set the global multiplier applied on top of every variant's own value.

```text
/oreconfig multiplier count 2      # twice as many veins for every ore
/oreconfig multiplier size 1.5     # every vein 50% larger
/oreconfig multiplier count 1      # back to normal
/oreconfig multiplier size 1
```

Range: `0.0` … `100.0`. Values above 100 are clamped to 100 and the command reports the clamp.
Values are rounded per variant, and the result is clamped to the valid range. Effective vein sizes
are hard-capped at **80**, so a size multiplier cannot push a vein outside Minecraft's chunk write
radius. A global count multiplier has no effect on variants whose count is `-1` (no count modifier).

### `/oreconfig reset <ore> [<variant>]`

Restore discovered vanilla defaults for one variant, one ore, or everything.

```text
/oreconfig reset coal lower   # one variant
/oreconfig reset coal         # every variant of coal
/oreconfig reset all          # every discovered variant on the server
```

`reset all` ignores any variant argument — it always resets all discovered variants.

Discovery defaults are the values found when the mod scanned the placed feature: original count,
size, Y range and distribution. After a reset, the entry behaves like a freshly generated config.

### `/oreconfig reload`

Re-read `config/ore_multiplier.json` from disk, re-discover ores, merge, validate, save and apply.

```text
/oreconfig reload
```

Use it after:

- editing the JSON by hand,
- adding or removing ore-related datapacks (run it after the datapack `/reload`),
- copying a config from another server.

## Tutorial

Everything below assumes you are an operator. Commands can be run in chat or from the server console
(without the leading `/`).

> **Important:** ore rules only affect chunks that have **not been generated yet**. To see changes,
> explore new terrain or create a new world. Existing chunks keep their blocks forever.

### 1. Inspect your ores

Start with the summary and the full list:

```text
/oreconfig
/oreconfig list
```

Then look at a specific ore:

```text
/oreconfig show diamond
```

You will see every variant with its current settings and clickable `[count]`, `[size]`, `[min-y]`,
`[max-y]`, `[off]` and `[reset]` chips. Clicking a chip puts the matching command into your chat bar
so you only need to change the number.

### 2. Disable an ore entirely

No more copper ore:

```text
/oreconfig disable copper
```

Expected response:

```text
[OreConfig] Disabled 2 variant(s) of minecraft:copper.
```

Verify with `/oreconfig show copper` (both variants now show `off`). To bring it back:

```text
/oreconfig enable copper
```

You can also target one vein type only, for example the large copper veins:

```text
/oreconfig disable copper large
```

### 3. Make an ore more common

`count` is the number of placement attempts per chunk. Vanilla coal uses 20 attempts for the lower
variant and 30 for the upper variant. Double both:

```text
/oreconfig set coal lower count 40
/oreconfig set coal upper count 60
```

Or change every coal variant at once:

```text
/oreconfig set coal all count 40
```

If you want to double every ore in the world instead of hand-editing each one, use
[global multipliers](#7-scale-every-ore-at-once).

### 4. Make veins bigger

Vein size controls how many blocks a single vein can place:

```text
/oreconfig set iron upper size 20
/oreconfig set diamond main size 8
```

Larger sizes grow vein volume faster than linearly, so raise values in small steps and test in fresh
chunks. Vein size is hard-capped at **80**; larger values are clamped and reported. See
[Vein size hard limit](#vein-size-hard-limit).

### 5. Limit an ore to a height band

Y values are absolute block coordinates. Diamond normally spans `-64..16`; to keep it in the deep
slate layer, narrow the band:

```text
/oreconfig set diamond main min-y -64
/oreconfig set diamond main max-y -32
/oreconfig set diamond buried min-y -64
/oreconfig set diamond buried max-y -32
```

To move an ore upwards (for example a coal-rich mountain layer):

```text
/oreconfig set coal lower min-y 96
/oreconfig set coal lower max-y 160
```

If `min-y` is greater than `max-y`, the values are swapped whenever the config is validated (on
load, reload, or after any command that changes a value).

### 6. Change the height distribution

Three distributions are available:

| Value | Shape |
|---|---|
| `uniform` | Equal chance anywhere in the band. |
| `triangle` | Peak in the middle, tapering towards both ends. |
| `trapezoid` | Flat top, then tapering; the plateau width is controlled by `plateau` in the JSON. |

```text
/oreconfig set gold main distribution triangle
/oreconfig set redstone main distribution uniform
```

The command switches the distribution. It keeps the stored plateau, except when you switch to
`triangle`, which sets the plateau to `0`. For a wide flat top, edit the JSON:

```json
"distribution": "trapezoid",
"plateau": 24
```

then run `/oreconfig reload`.

### 7. Scale every ore at once

Global multipliers apply to every variant on top of its own values:

```text
/oreconfig multiplier count 2
/oreconfig multiplier size 1.5
```

This is equivalent to doubling each count and multiplying each size by 1.5, but in two commands. The
summary line shows the active multipliers:

```text
State: enabled | count x2 | size x1.5 | 40 ore features
```

Return to normal:

```text
/oreconfig multiplier count 1
/oreconfig multiplier size 1
```

### 8. Restore defaults

```text
/oreconfig reset coal lower   # one vein type
/oreconfig reset coal         # a whole ore
/oreconfig reset all          # every discovered ore
```

This restores the values that were discovered from the placed feature (the vanilla values) and writes
them to the config.

### 9. Edit the JSON by hand

All commands write to `config/ore_multiplier.json`. You can also edit it directly, for example to
set a value the commands do not expose (`plateau`) or to copy settings between servers.

1. Open `config/ore_multiplier.json`.
2. Find the group and variant, e.g. `ores → minecraft:coal → variants → minecraft:ore_coal_lower`.
3. Change a field:

   ```json
   "minecraft:ore_coal_lower": {
     "enabled": true,
     "count": 60,
     "size": 20,
     "minY": 0,
     "maxY": 192,
     "distribution": "trapezoid",
     "plateau": 24
   }
   ```

4. Apply without restarting:

   ```text
   /oreconfig reload
   ```

5. Confirm with `/oreconfig show coal`.

If the JSON is invalid, the mod logs an error, moves the broken file aside as
`ore_multiplier.json.broken`, and continues with defaults. You can then fix the file and reload.

### 10. Full example: a richer world

A small server-friendly preset: more ore everywhere, bigger veins, and no rock blobs.

```text
# More of everything and larger veins
/oreconfig multiplier count 1.5
/oreconfig multiplier size 1.25

# Extra diamonds deep down
/oreconfig set diamond all count 10
/oreconfig set diamond all min-y -64
/oreconfig set diamond all max-y -16

# Remove the cosmetic rock/dirt blobs
/oreconfig disable andesite
/oreconfig disable diorite
/oreconfig disable granite
/oreconfig disable tuff
/oreconfig disable dirt
/oreconfig disable gravel

# Check the result
/oreconfig
```

Remember to test in newly generated chunks or a fresh world.

## How values combine

For every variant, the mod computes:

```text
effective count = round(variant.count × countMultiplier)   (clamped to 0 … 100000)
effective size  = round(variant.size  × sizeMultiplier)    (clamped to 1 … 80)
```

Exceptions and details:

- `enabled: false` forces the effective count to `0`.
- `count: -1` means "do not touch the count modifier"; the global count multiplier is not applied
  either.
- Height settings (`minY`, `maxY`, `distribution`, `plateau`) are used to build a new
  `HeightRangePlacement` for the variant.
- If a variant has no count modifier and you set a positive count, the mod inserts one. If it already
  has one, the value is replaced.
- Values are validated and clamped when the config is loaded or a command sets them. Commands that
  clamp an input tell you the limit that was applied.

## Vein size hard limit

Minecraft only allows a feature to write to the current chunk and its immediate neighbors during the
`features` generation step. A vanilla ore feature with size **81** can reach 17 blocks from its
origin, which is far enough to touch a chunk outside that radius. Minecraft then logs
`Detected setBlock in a far chunk` and rejects the affected blocks.

For that reason, the mod hard-caps the **final effective vein size at 80**, which is the largest
size that always stays inside the write radius:

- `/oreconfig set <ore> <variant> size 200` stores `80` and tells you it was clamped.
- A JSON value of `"size": 200` is clamped to `80` when the config is loaded or reloaded.
- `size 40` with `/oreconfig multiplier size 10` is capped to `80`, not `400`.
- Counts are not affected by this limit; their maximum remains `100000`.

`/oreconfig list` shows the effective size in parentheses when the multiplier changes it, for
example `size 33 (80)`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Unknown ore 'x'` | The name does not match a discovered group. Run `/oreconfig list` and use a listed key or short name. |
| `Ambiguous ore 'x'` | Several groups match the short name. Use the full key, e.g. `mymod:tin`. |
| `Unknown variant 'x'` | Use `/oreconfig show <ore>` to see the variant suffixes, or use `all`. |
| Changes do not show up | Only new chunks are affected. Explore ungenerated terrain or create a new world. |
| `Detected setBlock in a far chunk` | A vein exceeded Minecraft's one-chunk write radius. The mod caps effective size at 80; run `/oreconfig reload` and check that no external/datapack feature uses an oversized size. |
| A command says the value was clamped | The input exceeded a hard limit. Size is capped at 80, count at 100000, and multipliers at 100. |
| `Ore configuration is not loaded yet` | The server is still starting. Wait a moment and retry. |
| Command not permitted | You need OP level 2 (GAMEMASTERS). |
| A datapack `/reload` removed the overrides | Run `/oreconfig reload` to rebind overrides to the rebuilt placed feature registry. |
| Config edit did nothing | Run `/oreconfig reload`, and check the server log for JSON errors. |
| Config was replaced by defaults | The previous file was invalid JSON and was moved to `ore_multiplier.json.broken`. |

For installation, requirements and how the mod works internally, see the
[README](../README.md).
