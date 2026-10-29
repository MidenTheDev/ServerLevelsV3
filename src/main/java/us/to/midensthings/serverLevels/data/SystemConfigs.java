package us.to.midensthings.serverLevels.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/***
 * Each level system's settings live in their own file, systems/[name].yml. Besides the system's own settings, a file holds
 * the sections that used to be spread over mobs.yml, blocks.yml, milestones.yml and the files in Compat/.
 * <p>
 * The rest of the plugin reads these through combined views keyed by system name, the same layout as the old files
 * (e.g. getMobs() has a "mining" section with the mining system's mobs), so it doesn't need to know where they're stored.
 */
public final class SystemConfigs {
    public static final String SYSTEMS_FOLDER = "systems";
    public static final String MOBS = "mobs";
    public static final String BLOCKS = "blocks";
    public static final String MILESTONES = "milestones";
    public static final String PROJECTKORRA = "projectkorra";
    public static final String MYTHICMOBS = "mythicmobs";
    public static final String NOUVEAUENCHANTING = "nouveauenchanting";

    // Sections of a system file that aren't the system's own settings, and the old file each one came from
    private static final Map<String, String> SECTION_OLD_FILES = new LinkedHashMap<>();
    static {
        SECTION_OLD_FILES.put(MOBS, "mobs.yml");
        SECTION_OLD_FILES.put(BLOCKS, "blocks.yml");
        SECTION_OLD_FILES.put(MILESTONES, "milestones.yml");
        SECTION_OLD_FILES.put(PROJECTKORRA, "Compat/projectkorra.yml");
        SECTION_OLD_FILES.put(MYTHICMOBS, "Compat/mythicmobs.yml");
        SECTION_OLD_FILES.put(NOUVEAUENCHANTING, "Compat/nouveauenchanting.yml");
    }
    private static final String OLD_LEVELSYSTEMS_FILE = "levelsystems.yml";
    // Level systems created with a fresh install
    private static final List<String> DEFAULT_SYSTEMS = List.of("default", "mining");

    /***
     * Every system file, and combined views of them keyed by system name
     * @param files each system's file by system name
     * @param levelSystems each system's own settings, without the other sections
     * @param sections the other sections (mobs, blocks, ...) of every system, by section name
     */
    public record Loaded(Map<String, YamlConfiguration> files, YamlConfiguration levelSystems, Map<String, YamlConfiguration> sections) {
        public YamlConfiguration section(String name) {
            return sections.getOrDefault(name, new YamlConfiguration());
        }
    }

    private SystemConfigs() {}

    /***
     * Loads every system file. On the first start after updating, the old config files are split into system files first.
     * On a fresh install, the default system files are created. Safe to call off the main thread.
     */
    public static Loaded load(ServerLevels plugin) {
        File folder = new File(plugin.getDataFolder(), SYSTEMS_FOLDER);
        if (!folder.isDirectory()) {
            if (new File(plugin.getDataFolder(), OLD_LEVELSYSTEMS_FILE).exists()) {
                migrateOldFiles(plugin, folder);
            } else {
                for (String system : DEFAULT_SYSTEMS) {
                    plugin.saveResource(SYSTEMS_FOLDER + "/" + system + ".yml", false);
                }
            }
        }

        Map<String, YamlConfiguration> files = new TreeMap<>();
        File[] systemFiles = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (systemFiles != null) {
            for (File file : systemFiles) {
                String system = file.getName().substring(0, file.getName().length() - ".yml".length());
                if (!LevelSystemRegistry.isValidSystemName(system)) {
                    plugin.getLogger().warning("Skipping " + SYSTEMS_FOLDER + "/" + file.getName()
                            + ", level system names can only contain letters, numbers and underscores");
                    continue;
                }
                YamlConfiguration systemFile = YamlConfiguration.loadConfiguration(file);
                if (upgrade(plugin, system, systemFile)) {
                    try {
                        systemFile.save(file);
                    } catch (IOException e) {
                        plugin.getLogger().warning("Could not save the updated " + SYSTEMS_FOLDER + "/" + file.getName() + ": " + e.getMessage());
                    }
                }
                files.put(system, systemFile);
            }
        }

        // The booster message upgrade only runs once
        upgradeBoosterMessages = false;

        // Build the combined views
        YamlConfiguration levelSystems = new YamlConfiguration();
        Map<String, YamlConfiguration> sections = new HashMap<>();
        SECTION_OLD_FILES.keySet().forEach(section -> sections.put(section, new YamlConfiguration()));
        files.forEach((system, file) -> {
            ConfigurationSection settings = levelSystems.createSection(system);
            for (String key : file.getKeys(false)) {
                if (SECTION_OLD_FILES.containsKey(key)) {
                    if (file.isConfigurationSection(key)) {
                        copy(file.getConfigurationSection(key), sections.get(key).createSection(system));
                    }
                } else {
                    copyValue(file, key, settings);
                }
            }
        });
        return new Loaded(files, levelSystems, sections);
    }

    // Splits levelsystems.yml, mobs.yml, blocks.yml, milestones.yml and the Compat files into one file per system
    private static void migrateOldFiles(ServerLevels plugin, File folder) {
        plugin.getLogger().info("Moving level system settings into one file per system in " + SYSTEMS_FOLDER + "/ ...");
        File dataFolder = plugin.getDataFolder();
        YamlConfiguration levelSystems = YamlConfiguration.loadConfiguration(new File(dataFolder, OLD_LEVELSYSTEMS_FILE));
        Map<String, YamlConfiguration> oldFiles = new LinkedHashMap<>();
        SECTION_OLD_FILES.forEach((section, fileName) -> {
            File file = new File(dataFolder, fileName);
            if (file.exists()) {
                oldFiles.put(section, YamlConfiguration.loadConfiguration(file));
            }
        });

        if (!folder.mkdirs()) {
            plugin.getLogger().severe("Could not create the " + SYSTEMS_FOLDER + " folder, level systems will not load");
            return;
        }
        for (String system : levelSystems.getKeys(false)) {
            YamlConfiguration systemFile = new YamlConfiguration();
            systemFile.options().setHeader(List.of("Settings for the " + system + " level system, moved here from levelsystems.yml"
                    + " and the other old config files"));
            copy(levelSystems.getConfigurationSection(system), systemFile);
            oldFiles.forEach((section, oldFile) -> {
                if (oldFile.isConfigurationSection(system)) {
                    copy(oldFile.getConfigurationSection(system), systemFile.createSection(section));
                }
            });
            try {
                systemFile.save(new File(folder, system + ".yml"));
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + SYSTEMS_FOLDER + "/" + system + ".yml: " + e.getMessage());
            }
        }

        // Sections for systems that don't exist in levelsystems.yml can't be moved anywhere
        oldFiles.forEach((section, oldFile) -> oldFile.getKeys(false).stream()
                .filter(system -> !levelSystems.contains(system))
                .forEach(system -> plugin.getLogger().warning("Not moving the " + system + " section of " + SECTION_OLD_FILES.get(section)
                        + ", there's no " + system + " level system in levelsystems.yml. It's kept in old-configs/")));

        // Keep the old files in case anything needs to be checked, but out of the way so they aren't edited by mistake
        File backupFolder = new File(dataFolder, "old-configs");
        List<String> moved = new ArrayList<>(List.of(OLD_LEVELSYSTEMS_FILE));
        moved.addAll(SECTION_OLD_FILES.values());
        for (String fileName : moved) {
            File file = new File(dataFolder, fileName);
            if (!file.exists()) {
                continue;
            }
            File backup = new File(backupFolder, fileName);
            try {
                Files.createDirectories(backup.getParentFile().toPath());
                Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not move " + fileName + " to old-configs/: " + e.getMessage());
            }
        }
        File compatFolder = new File(dataFolder, "Compat");
        String[] leftInCompat = compatFolder.list();
        if (compatFolder.isDirectory() && leftInCompat != null && leftInCompat.length == 0) {
            compatFolder.delete();
        }
        plugin.getLogger().info("Moved " + levelSystems.getKeys(false).size() + " level systems to " + SYSTEMS_FOLDER
                + "/. The old files are in old-configs/");
    }

    /***
     * Brings a system file written for an older version up to the current layout. Add a step here whenever the layout changes.
     * @return whether anything changed, so the file needs saving
     */
    private static boolean upgrade(ServerLevels plugin, String system, YamlConfiguration file) {
        boolean changed = false;
        changed |= upgradeProjectKorra(plugin, system, file);
        changed |= upgradeBlocks(plugin, system, file);
        changed |= upgradeMessageToggles(plugin, system, file);
        changed |= upgradeExpMultiplier(plugin, system, file);
        if (upgradeBoosterMessages) {
            changed |= upgradeBoosterMessages(plugin, system, file);
        }
        return changed;
    }

    // exp-gain-multiplier was added to the player's own multiplier, so 0 meant normal exp. exp-multiplier multiplies instead,
    // so 1 is normal exp. Adding 1 gives the same exp for players with the default multiplier of 1
    private static boolean upgradeExpMultiplier(ServerLevels plugin, String system, YamlConfiguration file) {
        if (!file.contains("exp-gain-multiplier")) {
            return false;
        }
        if (!file.contains("exp-multiplier")) {
            file.set("exp-multiplier", file.getDouble("exp-gain-multiplier") + 1);
            file.setComments("exp-multiplier", List.of("Multiplies all exp gained in this system, 1 for normal exp"));
        }
        file.set("exp-gain-multiplier", null);
        plugin.getLogger().info("Replaced exp-gain-multiplier with exp-multiplier in " + SYSTEMS_FOLDER + "/" + system
                + ".yml (" + file.getDouble("exp-multiplier") + ", 1 is normal exp)");
        return true;
    }

    // Set by the config.yml 2.5 update, see upgradeBoosterMessages
    private static volatile boolean upgradeBoosterMessages = false;

    /***
     * Converts each system's booster messages the next time the system files load. Called by the config.yml 2.5 update, which
     * always runs before the system files load
     */
    public static void requestBoosterMessageUpgrade() {
        upgradeBoosterMessages = true;
    }

    // Booster messages used to be off unless a system had broadcast-boosters: true. They now come from config.yml unless the
    // system sets its own, so each system keeps what it did: off becomes '', the old default text is dropped to use config.yml's
    private static boolean upgradeBoosterMessages(ServerLevels plugin, String system, YamlConfiguration file) {
        boolean broadcast = file.getBoolean("broadcast-boosters", false);
        file.set("broadcast-boosters", null);
        if (!broadcast) {
            file.set("booster-start-message", "");
            file.set("booster-end-message", "");
            file.setComments("booster-start-message", List.of("This system doesn't broadcast boosters. Remove these to use the messages in config.yml"));
        } else {
            if (LevelSystem.DEFAULT_BOOSTER_START_MSG.equals(file.getString("booster-start-message"))) {
                file.set("booster-start-message", null);
            }
            if (LevelSystem.DEFAULT_BOOSTER_END_MSG.equals(file.getString("booster-end-message"))) {
                file.set("booster-end-message", null);
            }
        }
        plugin.getLogger().info("Updated the booster messages of " + SYSTEMS_FOLDER + "/" + system + ".yml, they now default to the ones in config.yml");
        return true;
    }

    // send-message-on-levelup and show-exp-gain turned the level up and exp gain messages on and off. Now an empty message
    // turns it off, so each toggle is folded into its message
    private static boolean upgradeMessageToggles(ServerLevels plugin, String system, YamlConfiguration file) {
        if (!file.contains("send-message-on-levelup") && !file.contains("show-exp-gain")) {
            return false;
        }
        if (file.contains("send-message-on-levelup")) {
            if (!file.getBoolean("send-message-on-levelup") || !file.isString("levelup-message")) {
                file.set("levelup-message", "");
            }
            file.set("send-message-on-levelup", null);
        }
        if (file.contains("show-exp-gain")) {
            if (!file.getBoolean("show-exp-gain")) {
                file.set("exp-gain-message", "");
            } else if (!file.isString("exp-gain-message")) {
                // It used to fall back to this built-in message
                file.set("exp-gain-message", "<green>Gained </green><dark_green>%exp%</dark_green><green> exp</green>");
            }
            file.set("show-exp-gain", null);
        }
        file.setComments("levelup-message", List.of("Messages use MiniMessage format for color and text effects. Set a message to '' to turn it off"));
        plugin.getLogger().info("Updated the level up and exp gain messages of " + SYSTEMS_FOLDER + "/" + system + ".yml, '' now turns a message off");
        return true;
    }

    // Block breaking used to have two full lists, break.no-silk and break.silk. It's now one break list, plus break-silk-touch
    // for only what's different with silk touch
    private static boolean upgradeBlocks(ServerLevels plugin, String system, YamlConfiguration file) {
        ConfigurationSection oldBreak = file.getConfigurationSection(BLOCKS + ".break");
        if (oldBreak == null || (!oldBreak.isConfigurationSection("no-silk") && !oldBreak.isConfigurationSection("silk"))) {
            return false;
        }
        ConfigurationSection noSilk = oldBreak.getConfigurationSection("no-silk");
        ConfigurationSection silk = oldBreak.getConfigurationSection("silk");

        Map<String, Object> breakExp = new LinkedHashMap<>();
        Map<String, Object> silkExp = new LinkedHashMap<>();
        if (noSilk != null) {
            noSilk.getKeys(false).forEach(block -> breakExp.put(block, noSilk.get(block)));
        }
        if (silk != null) {
            silk.getKeys(false).forEach(block -> silkExp.put(block, silk.get(block)));
        }
        // Before, a block only in no-silk gave the default exp when mined with silk touch. Now it would give its break exp,
        // so keep the old behavior by listing it with the default
        double defaultBreakExp = file.getDouble("exp-gain.on-blockbreak", 0);
        breakExp.keySet().stream()
                .filter(block -> silkExp.keySet().stream().noneMatch(block::equalsIgnoreCase))
                .forEach(block -> silkExp.put(block, defaultBreakExp));

        file.set(BLOCKS + ".break", null);
        file.createSection(BLOCKS + ".break", breakExp);
        file.setComments(BLOCKS + ".break", List.of("Exp for breaking blocks"));
        if (!silkExp.isEmpty()) {
            file.createSection(BLOCKS + ".break-silk-touch", silkExp);
            file.setComments(BLOCKS + ".break-silk-touch", List.of("Exp for breaking blocks with silk touch, for blocks where it's different from break"));
        }
        plugin.getLogger().info("Updated the blocks section of " + SYSTEMS_FOLDER + "/" + system + ".yml to the new layout");
        return true;
    }

    // The projectkorra section used to have one list per kind of exp (abilities-use, abilities-kill-mob, ...).
    // It's now grouped by ability, with defaults, per element settings, and mobs.kill / mobs.damage
    private static boolean upgradeProjectKorra(ServerLevels plugin, String system, YamlConfiguration file) {
        ConfigurationSection pk = file.getConfigurationSection(PROJECTKORRA);
        if (pk == null || (!pk.contains("exp-gain") && !pk.contains("abilities-use") && !pk.contains("kill-mobs")
                && !pk.contains("abilities-kill-player") && !pk.contains("abilities-kill-mob")
                && !pk.contains("abilities-damage-player") && !pk.contains("abilities-damage-mob") && !pk.contains("damage-mobs"))) {
            return false;
        }

        // Old exp-gain key -> new defaults key, and old per ability list -> new per ability key
        // Ordered, so the converted file lists them the same way every time
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("bending-use-ability", "use");
        defaults.put("bending-kill-player", "kill-player");
        defaults.put("bending-kill-mob", "kill-mob");
        defaults.put("bending-damage-player", "damage-player");
        defaults.put("bending-damage-mob", "damage-mob");
        Map<String, String> perAbility = new LinkedHashMap<>();
        perAbility.put("abilities-use", "use");
        perAbility.put("abilities-kill-player", "kill-player");
        perAbility.put("abilities-kill-mob", "kill-mob");
        perAbility.put("abilities-damage-player", "damage-player");
        perAbility.put("abilities-damage-mob", "damage-mob");

        YamlConfiguration upgraded = new YamlConfiguration();
        ConfigurationSection oldDefaults = pk.getConfigurationSection("exp-gain");
        if (oldDefaults != null) {
            defaults.forEach((oldKey, newKey) -> {
                if (oldDefaults.contains(oldKey)) {
                    upgraded.set("defaults." + newKey, oldDefaults.getDouble(oldKey));
                }
            });
        }
        perAbility.forEach((oldSection, newKey) -> {
            ConfigurationSection abilities = pk.getConfigurationSection(oldSection);
            if (abilities != null) {
                for (String ability : abilities.getKeys(false)) {
                    upgraded.set("abilities." + ability + "." + newKey, abilities.getDouble(ability));
                }
            }
        });
        for (String[] mobSection : new String[][]{{"kill-mobs", "kill"}, {"damage-mobs", "damage"}}) {
            ConfigurationSection mobs = pk.getConfigurationSection(mobSection[0]);
            if (mobs != null) {
                for (String mob : mobs.getKeys(false)) {
                    upgraded.set("mobs." + mobSection[1] + "." + mob, mobs.getDouble(mob));
                }
            }
        }
        // Keep anything that isn't part of the old layout
        Set<String> oldKeys = new HashSet<>(perAbility.keySet());
        oldKeys.addAll(List.of("exp-gain", "kill-mobs", "damage-mobs"));
        for (String key : pk.getKeys(false)) {
            if (!oldKeys.contains(key) && !upgraded.contains(key)) {
                copyValue(pk, key, upgraded);
            }
        }

        ConfigurationSection section = file.createSection(PROJECTKORRA);
        copy(upgraded, section);
        file.setComments(PROJECTKORRA, List.of("Only used when ProjectKorra is installed. Exp for each ability comes from abilities, then its",
                "element (or sub-element) in elements, then defaults. mobs gives extra exp for bending specific mobs"));
        plugin.getLogger().info("Updated the projectkorra section of " + SYSTEMS_FOLDER + "/" + system + ".yml to the new layout");
        return true;
    }

    // Copies every key of a section into another, with comments
    private static void copy(ConfigurationSection from, ConfigurationSection to) {
        if (from == null) {
            return;
        }
        for (String key : from.getKeys(false)) {
            copyValue(from, key, to);
        }
    }

    private static void copyValue(ConfigurationSection from, String key, ConfigurationSection to) {
        if (from.isConfigurationSection(key)) {
            copy(from.getConfigurationSection(key), to.createSection(key));
        } else {
            to.set(key, from.get(key));
        }
        to.setComments(key, from.getComments(key));
        to.setInlineComments(key, from.getInlineComments(key));
    }

    // Orders async writes of each system file, so an older save can't finish after a newer one
    private static final Object SAVE_LOCK = new Object();
    private static final AtomicLong SAVE_COUNTER = new AtomicLong();
    private static final Map<String, Long> lastWrittenSave = new HashMap<>();

    /***
     * Writes a system's file. The contents are taken now, the file is written in the background
     */
    public static void save(ServerLevels plugin, String system, YamlConfiguration file) {
        String yaml = file.saveToString();
        File target = new File(new File(plugin.getDataFolder(), SYSTEMS_FOLDER), system + ".yml");
        long saveId = SAVE_COUNTER.incrementAndGet();
        Runnable write = () -> {
            synchronized (SAVE_LOCK) {
                // A newer save already wrote the file, don't overwrite it with older contents
                if (saveId < lastWrittenSave.getOrDefault(system, 0L)) {
                    return;
                }
                try {
                    Files.createDirectories(target.getParentFile().toPath());
                    Files.writeString(target.toPath(), yaml);
                    lastWrittenSave.put(system, saveId);
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save " + SYSTEMS_FOLDER + "/" + system + ".yml: " + e.getMessage());
                }
            }
        };

        // Async tasks can't be scheduled while the plugin is shutting down, so write right away then
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }
}
