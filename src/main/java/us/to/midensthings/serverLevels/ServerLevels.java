package us.to.midensthings.serverLevels;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import us.to.midensthings.serverLevels.commands.CommandCore;
import us.to.midensthings.serverLevels.data.DatabaseManager;
import us.to.midensthings.serverLevels.data.LevelSystemRegistry;
import us.to.midensthings.serverLevels.data.Registries;
import us.to.midensthings.serverLevels.events.ExpGainBuiltin;
import us.to.midensthings.serverLevels.events.PlayerJoin;

import java.io.File;
import java.util.logging.Logger;

public final class ServerLevels extends JavaPlugin {
    Logger logger = this.getLogger();
    LevelSystemRegistry levelSystemRegistry;

    File levelsystemsYml = new File(this.getDataFolder()+"/levelsystems.yml");
    private YamlConfiguration levelSystemsConf;

    File milestonesYml = new File(this.getDataFolder()+"/milestones.yml");
    private YamlConfiguration milestonesConf;

    File mobsYml = new File(this.getDataFolder()+"/mobs.yml");
    private YamlConfiguration mobsConf;

    File blocksYml = new File(this.getDataFolder()+"/blocks.yml");
    private YamlConfiguration blocksConf;

    private DatabaseManager databaseManager;
    private PluginManager pm;

    @Override
    public void onEnable() {
        // Plugin startup logic
        pm = Bukkit.getPluginManager();
        logger.info("ServerLevels is starting up");

        getConfig().options().copyDefaults();
        saveDefaultConfig();
        loadConfigs();

        logger.info("Starting Database Manager");
        databaseManager = new DatabaseManager();
        databaseManager.setupDatabase();

        registerEvents();
        registerLevelSystems();
        registerCommands();

        // For API usage
        new Registries();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }


    public void loadConfigs() {

        if (!levelsystemsYml.exists()) {
            logger.info("Creating levelsystems.yml");
            this.saveResource("levelsystems.yml", false);
        }
        if (!milestonesYml.exists()) {
            logger.info("Creating milestones.yml");
            this.saveResource("milestones.yml", false);
        }
        if (!mobsYml.exists()) {
            logger.info("Creating mobs.yml");
            this.saveResource("mobs.yml", false);
        }
        if (!blocksYml.exists()) {
            logger.info("Creating blocks.yml");
            this.saveResource("blocks.yml", false);
        }
        levelSystemsConf = YamlConfiguration.loadConfiguration(levelsystemsYml);
        milestonesConf = YamlConfiguration.loadConfiguration(milestonesYml);
        mobsConf = YamlConfiguration.loadConfiguration(mobsYml);
        blocksConf = YamlConfiguration.loadConfiguration(blocksYml);
    }

    private void registerEvents() {
        pm.registerEvents(new PlayerJoin(), this);
        pm.registerEvents(new ExpGainBuiltin(), this);
    }

    public void registerLevelSystems() {
        levelSystemRegistry = new LevelSystemRegistry();
        levelSystemsConf.getConfigurationSection("").getKeys(false).forEach(system -> {
            if (levelSystemsConf.getBoolean(system+".enabled",true)) {
                levelSystemRegistry.registerLevelSystem(system);
            }

        });
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            CommandCore cmdCore = new CommandCore();
            commands.registrar().register(cmdCore.rootCommand, "sl");
            commands.registrar().register(cmdCore.reloadCommand, "slreload");
        });
    }

    public YamlConfiguration getLevelSystemsConf() {
        return levelSystemsConf;
    }

    public YamlConfiguration getMilestonesConf() {
        return milestonesConf;
    }

    public YamlConfiguration getMobsConf() {return mobsConf;}

    public YamlConfiguration getBlocksConf() {return blocksConf;}

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LevelSystemRegistry getLevelSystemRegistry() {
        return levelSystemRegistry;
    }
}
