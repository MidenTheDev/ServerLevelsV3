package us.to.midensthings.serverLevels;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import us.to.midensthings.serverLevels.commands.CommandCore;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.compat.events.NouveauEnchantingCompatEvents;
import us.to.midensthings.serverLevels.compat.events.ProjectKorraCompatEvents;
import us.to.midensthings.serverLevels.data.DatabaseManager;
import us.to.midensthings.serverLevels.data.LevelSystemRegistry;
import us.to.midensthings.serverLevels.data.Registries;
import us.to.midensthings.serverLevels.events.ExpGainBuiltin;
import us.to.midensthings.serverLevels.events.PlayerJoin;
import us.to.midensthings.serverLevels.util.UpdateTracker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ServerLevels extends JavaPlugin {
    Logger logger = this.getLogger();
    // volatile since it's replaced on reload and read from other threads
    private volatile LevelSystemRegistry levelSystemRegistry;

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
    private UpdateTracker updateTracker;

    public List<String> enabledCompats = new ArrayList<>();

    private NouveauEnchantingCompatEvents nouveauEnchantingCompatEvents;
    private ProjectKorraCompatEvents projectKorraCompatEvents;
    private MythicMobsCompat mythicMobsCompat;

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
        loadCompats();

        // For API usage
        new Registries();

        updateTracker = new UpdateTracker();
        if (getConfig().getBoolean("check-for-updates", true)) {
            updateTracker.checkForUpdates();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        // databaseManager is null if startup failed before it was created
        if (this.databaseManager != null) {
            this.databaseManager.closeHikariConnections();
        }
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
        // Build the new registry fully before swapping it in, so nothing ever sees a half filled registry
        LevelSystemRegistry newRegistry = new LevelSystemRegistry();
        levelSystemsConf.getConfigurationSection("").getKeys(false).forEach(system -> {
            if (!LevelSystemRegistry.isValidSystemName(system)) {
                logger.warning("Level system '" + system + "' has an invalid name and will not be loaded. Names can only contain letters, numbers, and underscores");
                return;
            }
            if (levelSystemsConf.getBoolean(system+".enabled",true)) {
                newRegistry.registerLevelSystem(system);
            }

        });
        levelSystemRegistry = newRegistry;
    }

    /***
     * Reloads the config files of all enabled compatibilities
     */
    public void reloadCompatConfigs() {
        if (nouveauEnchantingCompatEvents != null) {
            nouveauEnchantingCompatEvents.reloadConfig();
        }
        if (projectKorraCompatEvents != null) {
            projectKorraCompatEvents.reloadConfig();
        }
        if (mythicMobsCompat != null) {
            mythicMobsCompat.reloadConfig();
        }
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            CommandCore cmdCore = new CommandCore();
            commands.registrar().register(cmdCore.rootCommand, "sl");
        });
    }

    private void loadCompats() {
        enabledCompats.clear();
        // Nouveau Enchanting
        if (pm.getPlugin("NouveauEnchanting") != null) {
            logger.info("Enabling NouveauEnchanting Support");
            enabledCompats.add("NouveauEnchanting");
            File neYml = new File(this.getDataFolder()+"/Compat/nouveauenchanting.yml");
            if (!neYml.exists()) {
                this.saveResource("Compat/nouveauenchanting.yml",false);
            }
            nouveauEnchantingCompatEvents = new NouveauEnchantingCompatEvents();
            pm.registerEvents(nouveauEnchantingCompatEvents,this);
        }

        // Project Korra
        if (pm.getPlugin("ProjectKorra") != null) {
            logger.info("Enabling ProjectKorra Support");
            enabledCompats.add("ProjectKorra");
            File pkYml = new File(this.getDataFolder()+"/Compat/projectkorra.yml");
            if (!pkYml.exists()) {
                this.saveResource("Compat/projectkorra.yml",false);
            }
            projectKorraCompatEvents = new ProjectKorraCompatEvents();
            pm.registerEvents(projectKorraCompatEvents,this);
        }

        // Mythic Mobs
        if (pm.getPlugin("MythicMobs") != null) {
            logger.info("Enabling MythicMobs Support");
            enabledCompats.add("MythicMobs");
            File pkYml = new File(this.getDataFolder()+"/Compat/mythicmobs.yml");
            if (!pkYml.exists()) {
                this.saveResource("Compat/mythicmobs.yml",false);
            }
            mythicMobsCompat = new MythicMobsCompat();
        }

        // Luckperms
        if (pm.getPlugin("LuckPerms") != null) {
            logger.info("Enabling LuckPerms support");
            enabledCompats.add("LuckPerms");
        }
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

    /***
     * @return the MythicMobs compatibility, or null if MythicMobs isn't installed
     */
    public MythicMobsCompat getMythicMobsCompat() {
        return mythicMobsCompat;
    }

    public UpdateTracker getUpdateTracker() {
        return updateTracker;
    }
}
