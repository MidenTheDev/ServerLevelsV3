package us.to.midensthings.serverLevels;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import us.to.midensthings.serverLevels.commands.LSCommands;
import us.to.midensthings.serverLevels.commands.SLCommands;
import us.to.midensthings.serverLevels.compat.MythicMobsCompat;
import us.to.midensthings.serverLevels.compat.PAPIExpansion;
import us.to.midensthings.serverLevels.compat.events.NouveauEnchantingCompatEvents;
import us.to.midensthings.serverLevels.compat.events.ProjectKorraCompatEvents;
import us.to.midensthings.serverLevels.data.BoosterManager;
import us.to.midensthings.serverLevels.data.DatabaseManager;
import us.to.midensthings.serverLevels.data.LevelSystemRegistry;
import us.to.midensthings.serverLevels.data.PlayerLoader;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.data.Registries;
import us.to.midensthings.serverLevels.events.ExpGainBuiltin;
import us.to.midensthings.serverLevels.events.PlayerJoin;
import us.to.midensthings.serverLevels.events.PlayerLeave;
import us.to.midensthings.serverLevels.util.ConfigUpdater;
import us.to.midensthings.serverLevels.util.DurationParser;
import us.to.midensthings.serverLevels.util.UpdateTracker;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class ServerLevels extends JavaPlugin {
    // Used when autosave-interval is missing or invalid
    private static final String DEFAULT_AUTOSAVE_INTERVAL = "5m";
    private BukkitTask autosaveTask;
    private final AtomicBoolean reloading = new AtomicBoolean(false);

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
    private BoosterManager boosterManager;
    private PluginManager pm;
    private UpdateTracker updateTracker;

    public List<String> enabledCompats = new ArrayList<>();

    private NouveauEnchantingCompatEvents nouveauEnchantingCompatEvents;
    private ProjectKorraCompatEvents projectKorraCompatEvents;
    private MythicMobsCompat mythicMobsCompat;
    private PAPIExpansion papiExpansion;

    @Override
    public void onEnable() {
        // Plugin startup logic
        pm = Bukkit.getPluginManager();
        logger.info("ServerLevels is starting up");

        getConfig().options().copyDefaults();
        saveDefaultConfig();
        ConfigUpdater.update(this);
        loadConfigs();

        logger.info("Starting Database Manager");
        databaseManager = new DatabaseManager();
        databaseManager.setupDatabase();

        boosterManager = new BoosterManager();
        boosterManager.start();

        // Level systems must exist before the join listener can load players into them
        registerLevelSystems();
        registerEvents();
        registerCommands();
        loadCompats();
        startAutosave();

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
        // Unregister so a plugin manager reload doesn't leave the old expansion registered
        if (this.papiExpansion != null) {
            this.papiExpansion.unregister();
        }
        // Saves how much time active boosters have left
        if (this.boosterManager != null) {
            this.boosterManager.shutdown();
        }
        // databaseManager is null if startup failed before it was created
        if (this.databaseManager != null) {
            if (levelSystemRegistry != null) {
                levelSystemRegistry.getAllSystems().forEach(levelSystem -> {
                    levelSystem.getPlayerCache().saveAllPlayers();
                });
            }
            // Waits for the saves queued above to finish before closing the connections
            this.databaseManager.shutDownDatabase();
        }
    }


    // The plugin's config files besides config.yml, loaded together so a reload can read them off the main thread then swap them in at once
    private record ConfigFiles(YamlConfiguration levelSystems, YamlConfiguration milestones,
                               YamlConfiguration mobs, YamlConfiguration blocks) {}

    public void loadConfigs() {
        applyConfigFiles(readConfigFiles());
    }

    // Creates any missing config files and reads them all from disk. Safe to call off the main thread
    private ConfigFiles readConfigFiles() {
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
        return new ConfigFiles(
                YamlConfiguration.loadConfiguration(levelsystemsYml),
                YamlConfiguration.loadConfiguration(milestonesYml),
                YamlConfiguration.loadConfiguration(mobsYml),
                YamlConfiguration.loadConfiguration(blocksYml));
    }

    private void applyConfigFiles(ConfigFiles files) {
        levelSystemsConf = files.levelSystems();
        milestonesConf = files.milestones();
        mobsConf = files.mobs();
        blocksConf = files.blocks();
    }

    /***
     * Reloads every config file, the database connection, and the level systems, without blocking the main thread.
     * Files are read and the database is set up in the background, then the new settings are swapped in on the main thread.
     * Must be called on the main thread.
     * @return a future that completes once the reload is done, or fails if a reload is already running
     */
    public CompletableFuture<Void> reloadAsync() {
        if (!reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A reload is already in progress"));
        }

        // config.yml is small and is read on the main thread elsewhere, so reload it here
        reloadConfig();
        ConfigUpdater.update(this);
        FileConfiguration config = getConfig();

        Executor async = task -> Bukkit.getScheduler().runTaskAsynchronously(this, task);
        Executor mainThread = task -> Bukkit.getScheduler().runTask(this, task);

        return CompletableFuture
                .supplyAsync(() -> {
                    ConfigFiles files = readConfigFiles();
                    reloadCompatConfigs();
                    return files;
                }, async)
                // Creates tables for any new systems before they're registered below
                .thenCompose(files -> databaseManager.setupDatabaseAsync(config, files.levelSystems()).thenApply(ignored -> files))
                .thenAcceptAsync(files -> {
                    applyConfigFiles(files);
                    registerLevelSystems();
                    startAutosave();
                }, mainThread)
                .whenComplete((ignored, error) -> reloading.set(false));
    }

    private void registerEvents() {
        pm.registerEvents(new PlayerJoin(), this);
        pm.registerEvents(new ExpGainBuiltin(), this);
        pm.registerEvents(new PlayerLeave(), this);
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

        LevelSystemRegistry oldRegistry = levelSystemRegistry;
        if (oldRegistry != null) {
            // Keep the cached player data of systems that still exist, and save and drop systems that were removed
            oldRegistry.getAllSystems().forEach(oldSystem -> {
                LevelSystem newSystem = newRegistry.getLevelSystem(oldSystem.getSystemName());
                if (newSystem != null) {
                    newSystem.setPlayerCache(oldSystem.getPlayerCache());
                } else {
                    oldSystem.getPlayerCache().unloadAllPlayers();
                }
            });
        }
        levelSystemRegistry = newRegistry;

        // Load online players into any systems they aren't cached in yet. This covers players already online when
        // the plugin enables, systems added by a reload, and systems that now auto add players
        Bukkit.getOnlinePlayers().forEach(PlayerLoader::loadOnlinePlayer);
    }

    /***
     * Starts the autosave timer using autosave-interval from config.yml, replacing the current timer if there is one
     */
    public void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }

        String configuredInterval = getConfig().getString("autosave-interval", DEFAULT_AUTOSAVE_INTERVAL);
        Duration interval = DurationParser.parse(configuredInterval);
        if (interval == null || interval.toSeconds() < 1) {
            logger.warning("Invalid autosave-interval '" + configuredInterval + "' in config.yml, using " + DEFAULT_AUTOSAVE_INTERVAL
                    + ". Use d, h, m and s, e.g. 1d5h4m2s, 30m or 45s");
            configuredInterval = DEFAULT_AUTOSAVE_INTERVAL;
            interval = DurationParser.parse(DEFAULT_AUTOSAVE_INTERVAL);
        }

        // 20 ticks per second
        long intervalTicks = interval.toSeconds() * 20;
        // Runs on the main thread so player data isn't read while it's being changed. The saves themselves happen on the database thread
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, this::autosave, intervalTicks, intervalTicks);
        logger.info("Autosaving player data every " + configuredInterval);
    }

    /***
     * Saves every cached player whose data changed since their last save
     */
    public void autosave() {
        logger.info("Autosaving player data...");
        int saved = 0;
        for (LevelSystem levelSystem : levelSystemRegistry.getAllSystems()) {
            saved += levelSystem.getPlayerCache().saveAllPlayers();
        }
        logger.info("Autosave complete, saved " + saved + " player record" + (saved == 1 ? "" : "s"));
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
            SLCommands cmdCore = new SLCommands();
            commands.registrar().register(cmdCore.rootCommand, "sl");
            LSCommands lsCommands = new LSCommands();
            commands.registrar().register(lsCommands.lsRootCommand,"ls");
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

        if (pm.getPlugin("PlaceholderAPI") != null) {
            logger.info("Enabling PlaceholderAPI Support");
            enabledCompats.add("PlaceholderAPI");
            papiExpansion = new PAPIExpansion();
            papiExpansion.register();
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

    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

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
