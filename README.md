# ForbiddenBlocks

## Overview

ForbiddenBlocks is a client-side mod for Minecraft that allows players to set specific blocks as forbidden, preventing their placement in the game. This mod is particularly useful for players who want to avoid losing items with specific names or lore.

## Key Features

- **Block Placement Prevention**: Users can define a list of forbidden blocks that cannot be placed in the game.
- **User Feedback**: Configurable messages provide feedback when players attempt to place forbidden blocks.
- **ModMenu Integration**: Easy access to configure settings through the settings interface.
- **World-Specific Configurations**: Each Minecraft world or server can have its own configuration file, allowing for tailored settings.

## Installation

1. **Download the mod** from the [official repository](https://github.com/Evol-Luci/Minecraft_Client_Mods/No_PlaceBlock_Client).
2. **Place the mod file** in the `mods` folder of your Minecraft installation.
3. **Launch Minecraft** with the Fabric loader.

## Configuration

### Global Settings

The global settings are managed through the [ForbiddenBlocksConfig]me/lucievol/forbiddenblocks/config/ForbiddenBlocksConfig.java class, which allows you to toggle the visibility of feedback messages.

### World-Specific Settings

Each world or server has its own configuration file stored in JSON format:
- **Single Player Worlds**: `config/forbiddenblocks/worlds/singleplayer_[worldname].json`
- **Multiplayer Servers**: `config/forbiddenblocks/worlds/multiplayer_[serveraddress].json`

You can modify the forbidden blocks list directly in these files or through the in-game configuration UI.

## Usage

- **Toggle Forbidden Blocks**: Use the designated key (default: `O`) to toggle the restriction on block placement.
- **Toggle Feedback Messages**: Use the designated key (default: `M`) to toggle visibility of feedback messages.

## Dependencies

- **Fabric Loader**: Version >= 0.16.10
- **Minecraft**: Version ~1.21.4
- **Java**: Version >= 21
- **Fabric API**: Required for mod functionality.
- **Cloth Config**: For configuration management.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Authors

- **LuciEvol** - [Website](https://www.evoldigitalproductions.com)
