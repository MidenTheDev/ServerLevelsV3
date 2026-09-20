# Server Levels
![GitHub Release](https://img.shields.io/github/v/release/MidenTheDev/ServerLevelsV3)![GitHub Pre-Release](https://img.shields.io/github/v/release/MidenTheDev/ServerLevelsV3?include_prereleases)
![Static Badge](https://img.shields.io/badge/Requires%20Version%2026.1.2%2B-00BF3F?style=flat) ![Static Badge](https://img.shields.io/badge/Requires%20Paper-BF5600?style=flat&link=https%3A%2F%2Fpapermc.io%2Fdownloads%2Fpaper)

Adds a new system for server owners to add their own leveling and progression systems completely separate from Minecraft's existing leveling system.

## 💹 Custom Leveling Systems:
Using this plugin, you can define your own custom leveling systems. This is done in the plugin's levelsystems.yml file, where you can configure the name, how players gain exp, the formula that calculates the amount of exp required for each level, etc etc. There is no limit on the number of systems you can create, and using the plugin's API you can define your own custom methods for gaining exp!

The plugin also natively supports the following plugins to add additional support:
## 💻 Commands:
/sl **set** [exp|level] <player> <amount> <system> - Sets the exp or level of a specified player to the specified amount in the specified level system

/sl **add** [exp|level] <player> <amount> <system> - Gives the specified amount of exp or levels to the given player in the specified level system.

/sl **remove** [exp|level] <player> <amount> <system> - Removes the specified amount of exp or levels from the given player in the specified level system.

/sl **reload** - Reloads the plugin, loading changes made in all configs and refreshing the database connection.
## ❓FAQ:
