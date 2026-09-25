# Server Levels
![GitHub Release](https://img.shields.io/github/v/release/MidenTheDev/ServerLevelsV3)![GitHub Pre-Release](https://img.shields.io/github/v/release/MidenTheDev/ServerLevelsV3?include_prereleases)
![Static Badge](https://img.shields.io/badge/Requires%20Version%2026.1.2%2B-00BF3F?style=flat) ![Static Badge](https://img.shields.io/badge/Requires%20Paper-BF5600?style=flat&link=https%3A%2F%2Fpapermc.io%2Fdownloads%2Fpaper)

Adds a new system for server owners to add their own leveling and progression systems completely separate from Minecraft's existing leveling system.

## 💹 Custom Leveling Systems:
Using this plugin, you can define your own custom leveling systems. This is done in the plugin's levelsystems.yml file, where you can configure the name, how players gain exp, the formula that calculates the amount of exp required for each level, etc etc. There is no limit on the number of systems you can create, and using the plugin's API you can define your own custom methods for gaining exp!

The plugin also supports the following plugins to add new ways to gain exp:
|Plugin|Has Support|
|------|-----------|
|Project Korra|❌ (W.I.P)|
|Nouveau Enchanting|❌ (W.I.P)|
|Mythic Mobs|❌ (W.I.P)|
|ItemsAdder|❌ (W.I.P)|

## 📁 Setup:
The bulk of the setup is done the `levelsystems.yml` file generated after your server boots for the first time. By default, the plugin comes with a generalized default level system that demonstrates how custom level systems are structured. See the Wiki Page for more details on setting up your custom system exactly how you want it.

If you want to give your players rewards for leveling up, this is done in the `milestones.yml` file, where you can setup custom rewards for specific levels. Again, the plugin ships with a default configuration file that should give sufficient examples on how to set up your own rewards, but there is a dedicated Wiki Page that goes into much greater detail if neeeded.

In some cases, you may want players to gain experience for killing mobs, but you might want to make some mobs give more or less exp to the player than others. Configuration for this is done in the `mobs.yml` file. See the Wiki Page for more detail on customizing this file to your liking.

## 💻 Commands:
|Command|Permission|Description|
|-------|----------|-----------|
|/sl **set** [exp/level] [player] [amount] [system] | serverlevels.commands.set | Sets the exp or level of a specified player to the specified amount in the specified level system|
|/sl **add** [exp/level] [player] [amount] [system]| serverlevels.commands.add |Gives the specified amount of exp or levels to the given player in the specified level system.|
|/sl **remove** [exp/level] [player] [amount] [system]| serverlevels.commands.remove | Removes the specified amount of exp or levels from the given player in the specified level system.|
|/slreload | serverlevels.commands.reload |Reloads the plugin, loading changes made in all configs and refreshing the database.|

## 📒 Planned Features:
- In-game GUI for setting up and customizing Level Systems
- Support for more plugins (See the table above for current and W.I.P plugins).
- Customizable chat prefix to display players' levels.
- EXP bar hijacking to replace the vanilla exp system with your custom one.
- Indicators to players when they gain exp.

## ❓ FAQ:
#### Q: Where does the V3 come from?
A: This plugin is the 3rd iteration of the Server Levels plugin that I've made. The first version is still available on Spigot on my old account and the GitHub repo is still public on my profile but is archived. V2 Never saw an official release because I was very dissatisfied with the plugin's performance and the quality of the code. V3 is, hopefully, the last time I will feel the need to remake this plugin, as I'm finally satisfied with the state of the plugin and the overall quality and performance.

#### Q: Can you add support for X plugin?
A: Maybe! Check the table above to see if I'm already working on it or planning to work on it, if its not already in the table then feel free to either go to the Issues tab and open a new issue requesting support for the plugin OR join the Discord server and request it there.

#### Q: Can you bring back the yml file data storage?
A: I'd really rather not. Using yml files to store the data for each player is incredibly slow compared to databases. If enough people really want yml storage back, then I will bring it back, but the demand will have to be rather large. 

#### Q: I need help! X Isn't working!
A: Check the relevant Wiki page for the feature that isn't working and make sure your configs are set up properly. If you cannot find the issue yourself, join the Discord server and request support there.
