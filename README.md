# SchemPaste

**English** | [简体中文](README_CN.md)

A Minecraft Fabric mod that helps you paste Litematica schematics on servers.

## Dependencies

[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.14.8+-brightgreen?style=flat-square&logo=fabricmc&logoColor=white)](https://fabricmc.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-Required-orange?style=flat-square&logo=fabricmc&logoColor=white)](https://github.com/FabricMC/fabric)
[![Syncmatica](https://img.shields.io/badge/Syncmatica-Required-blue?style=flat-square&logo=minecraft&logoColor=white)](https://github.com/End-Tech/syncmatica)

## Optimization for large schematics

- After testing in RMS Server with Intel i7-14600k CPU, pasting a schematic with 10 million blocks and a volume of 200 million blocks took 9 minutes and 46 seconds (averaging 20,000 blocks per second), with TPS remaining stable and no drops.

> [!Note] 
> This performance data only represents the mod's performance on the RMS Server and is for reference purposes only. It does not indicate the actual performance on your server.

## Command Details

### Main Commands

#### `/sp paste` - Paste Command

Basic syntax:

- `/sp paste stop` - Stop all active paste tasks
- `/sp paste <index>` - Paste schematic by index
- `/sp paste <index> addition <parameters>` - Paste schematic with additional parameters

**Index Parameter:**

- `<index>` - Integer, building index starting from 1, corresponding to buildings in `placements.json`

**Additional Parameters:**

**Replace Behavior Parameters:**

- `replace none` - Don't replace any existing blocks
- `replace all` - Replace all blocks (including air)
- `replace with_non_air` - Only place blocks at non-air positions (default behavior)

**Layer Control Parameters:**

- `axis <x|y|z>` - Set layer axis (default y-axis)
- `mode <mode>` - Set layer mode:
    - `all` - Paste all layers (default)
    - `single` or `single_layer` - Paste only a single layer
    - `all_above` - Paste all layers above specified coordinate
    - `all_below` - Paste all layers below specified coordinate
    - `range` or `layer_range` - Paste layers within specified range

**Layer Coordinate Parameters:**

- `single <coordinate>` - Coordinate value for single layer mode
- `above <coordinate>` - Starting coordinate for "above" mode
- `below <coordinate>` - Ending coordinate for "below" mode
- `rangemin <coordinate>` - Minimum coordinate for range mode
- `rangemax <coordinate>` - Maximum coordinate for range mode

**Usage Examples:**

```
/sp paste 1                                           # Paste building 1
/sp paste 2 addition replace none                     # Paste building 2, don't replace existing blocks
/sp paste 3 addition rate 100                        # Paste building 3, process 100 blocks per tick
/sp paste 4 addition replace all rate 50             # Paste building 4, replace all blocks, 50 blocks per tick
/sp paste 5 addition axis y mode single single 64    # Paste building 5, only paste Y=64 layer
/sp paste 6 addition mode all_above above 100        # Paste building 6, only paste Ye100 parts
/sp paste 7 addition mode range rangemin 60 rangemax 80  # Paste building 7, only paste Y=60-80 range
/sp paste 8 addition axis x mode single single 150 replace none  # Paste building 8, X=150 slice, no replace
/sp paste stop                                        # Stop all paste tasks
```

#### `/sp list` - List Command

- Display list of all available projections

### Permission Requirements

All commands require OP permissions

## Configuration Files

### Main Configuration File (`config/schempaste.json`)

**Performance Related Configuration:**

```json
{
    "backgroundThreads": 2,
    // Number of background processing threads
    "mainThreadBudgetMs": 40,
    // Main thread budget time per tick (milliseconds)
    "enableProgressMessages": true,
    // Enable progress messages
    "progressUpdateIntervalMs": 2000
    // Progress update interval (milliseconds)
}
```

**Default Behavior Configuration:**

```json
{
    "defaultReplace": "with_non_air",
    // Default replace behavior
    "suppressNeighborUpdates": true,
    // Suppress neighbor block updates
    "fixChestMirror": true,
    // Fix chest mirror issues
    "clearInventories": false
    // Clear container items
}
```

**Layer Control Configuration:**

```json
{
    "defaultLayerAxis": "y",
    // Layer axis (x/y/z)
    "defaultLayerMode": "all",
    // Layer mode
    "defaultLayerSingle": 0,
    // Single layer coordinate
    "defaultLayerRangeMin": 0,
    // Range mode minimum coordinate
    "defaultLayerRangeMax": 0
    // Range mode maximum coordinate
}
```

**Chunk Management Configuration:**

```json
{
    "enableDynamicChunkLoading": true,
    // Enable dynamic chunk loading
    "maxLoadedChunks": 32
    // Maximum loaded chunks
}
```

## Supported Versions

- 1.17.1
- 1.20.1

## Installation

1. Install Fabric Loader
2. Download the mod jar file
3. Place the file in the `mods` folder

## TODO

- Remove dependency on Syncmatica, allow direct loading of .litematica files from client

## License

CC0-1.0
