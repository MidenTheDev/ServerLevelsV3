package us.to.midensthings.serverLevels.systems;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.NamespacedKey;
import org.bukkit.Keyed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.util.MessageFormatter;
import us.to.midensthings.serverLevels.compat.LuckPermsCompat;
import us.to.midensthings.serverLevels.data.PlayerCache;
import us.to.midensthings.serverLevels.data.SystemConfigs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class LevelSystem {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    private String systemName;

    // Whether or not this system sends the player a message when they level up
    // Whether or not this system uses the milestone system to reward levelups
    private boolean useMilestones;

    // The message to be sent to the player on level up
    private String lvlUpMsg;

    // Max level of this system
    private int maxLevel;
    // Multiplier on exp gains for this system
    // Multiplies all exp gained in this system, along with the player's own multiplier and boosters
    private double expMultiplier = 1;
    // Players in creative or spectator mode don't gain exp in this system
    private boolean ignoreCreative;
    // Worlds where nobody gains exp in this system, lower case
    private Set<String> disabledWorlds = new HashSet<>();

    // EXP required to go from level 0 to level 1;
    private double baseExpRequirement;
    // Multiplier for increasing exp requirements per level
    private double expRequirementMultiplier;
    // ScalarType for how the multiplier is applied to the exp requirements
    private ScalarType scalarType;

    // Exp gain on event
    private double expOnChat;
    private double expOnMobKill;
    private double expOnPlayerKill;
    private double expOnBlockBreak;
    private double expOnBlockPlace;

    private boolean autoAddPlayers;
    private String expGainMsg;

    // This system's booster broadcast messages. null uses the ones in config.yml, empty turns them off
    private String boosterStartMsg;
    private String boosterEndMsg;

    // Used when the deprecated setLevelUpMsg(true) / setShowExpGain(true) turn a message on that had none
    public static final String DEFAULT_LEVELUP_MSG = "<green>You leveled up! New level: %level%</green>";
    public static final String DEFAULT_EXP_GAIN_MSG = "<green>Gained </green><dark_green>%exp%</dark_green><green> exp</green>";

    // The booster messages in config.yml before they could be changed there, also used if config.yml is missing them
    public static final String DEFAULT_BOOSTER_START_MSG = "<gold>A <yellow>%multiplier%x</yellow> exp booster for <yellow>%system%</yellow> has started! It lasts <yellow>%duration%</yellow>.</gold>";
    public static final String DEFAULT_BOOSTER_END_MSG = "<gold>The <yellow>%multiplier%x</yellow> exp booster for <yellow>%system%</yellow> has ended.</gold>";

    // Levels with a milestone, and the intervals of repeating milestones (every-N keys)
    private List<Integer> milestones;
    private List<Integer> repeatingMilestones = new ArrayList<>();

    private PlayerCache playerCache;

    // Per mob/block exp overrides from the mobs and blocks sections, parsed once at load
    private Map<EntityType, Double> mobKillExp;
    // Exp for mobs from mob spawners, from the spawner part of the mobs section
    private Map<EntityType, Double> spawnerMobKillExp;
    // Multiplies exp from mobs that came from a mob spawner, for mobs without their own spawner exp in the mobs section
    private double spawnerMobMultiplier = 1;
    private Map<Material, Double> blockBreakExp;
    private Map<Material, Double> blockBreakSilkExp;
    private Map<Material, Double> blockPlaceExp;
    // Exp for ItemsAdder and CraftEngine blocks, by id like "itemsadder:namespace:id"
    private Map<String, Double> customBlockBreakExp;
    private Map<String, Double> customBlockBreakSilkExp;
    private Map<String, Double> customBlockPlaceExp;

    // getExpRequirement for each level from 0 to max level, rebuilt whenever the requirement settings change
    private volatile double[] expRequirementTable;
    // Limits the table's memory use if max-level is set absurdly high, levels past this are calculated on demand
    private static final int MAX_PRECOMPUTED_LEVELS = 100_000;

    private YamlConfiguration levelSystemConf = plugin.getLevelSystemsConf();


    /***
     * Creates a level system with every setting at its zero value (0, false, no messages, LINEAR scaling), without reading its file.
     * It isn't registered or saved. Use saveToConfig() to write it to systems/[name].yml.
     */
    public static LevelSystem createEmpty(String systemName) {
        return new LevelSystem(systemName, true);
    }

    // Leaves every setting at its zero value. The unused flag only separates this from the config reading constructor
    private LevelSystem(String systemName, boolean empty) {
        this.systemName = systemName;
        this.playerCache = new PlayerCache(systemName);
        milestones = new ArrayList<>();
        scalarType = ScalarType.LINEAR;

        mobKillExp = new EnumMap<>(EntityType.class);
        spawnerMobKillExp = new EnumMap<>(EntityType.class);
        blockBreakExp = new EnumMap<>(Material.class);
        blockBreakSilkExp = new EnumMap<>(Material.class);
        blockPlaceExp = new EnumMap<>(Material.class);
        customBlockBreakExp = new HashMap<>();
        customBlockBreakSilkExp = new HashMap<>();
        customBlockPlaceExp = new HashMap<>();
        rebuildExpRequirementTable();
    }

    public LevelSystem(String systemName) {
        this.systemName = systemName;
        this.playerCache = new PlayerCache(systemName);
        // Get yamlconfiguration for level systems and fill in the class
        // An empty (or missing) message turns it off
        lvlUpMsg = levelSystemConf.getString(systemName+".levelup-message", "");

        useMilestones = levelSystemConf.getBoolean(systemName+".use-milestones");
        milestones = new ArrayList<>();
        if (useMilestones) {
            YamlConfiguration milestoneConf = plugin.getMilestonesConf();
            if (milestoneConf.getConfigurationSection(systemName) != null)  {
                // setup milestones
                // Keys are a level, or every-N for a milestone at every multiple of N
                milestoneConf.getConfigurationSection(systemName).getKeys(false).forEach(milestone -> {
                    String key = milestone.toLowerCase(Locale.ROOT);
                    try {
                        if (key.startsWith("every-")) {
                            int interval = Integer.parseInt(key.substring("every-".length()));
                            if (interval > 0) {
                                repeatingMilestones.add(interval);
                                return;
                            }
                        } else {
                            milestones.add(Integer.parseInt(key));
                            return;
                        }
                    } catch (NumberFormatException e) {
                        // Warned below
                    }
                    plugin.getLogger().warning("Invalid milestone '" + milestone + "' in systems/" + systemName
                            + ".yml, use a level like 10, or every-10 for every 10 levels");
                });
            } else {
                plugin.getLogger().warning("Level System " + systemName + " has milestones enabled but doesn't have any milestones in its milestones section!");
            }

        }

        maxLevel = levelSystemConf.getInt(systemName+".max-level", 100);
        expMultiplier = levelSystemConf.getDouble(systemName+".exp-multiplier", 1);
        ignoreCreative = levelSystemConf.getBoolean(systemName+".ignore-creative", false);
        disabledWorlds = new HashSet<>();
        levelSystemConf.getStringList(systemName+".disabled-worlds").forEach(world -> disabledWorlds.add(world.toLowerCase(Locale.ROOT)));

        expOnChat = levelSystemConf.getDouble(systemName+".exp-gain.on-chat");
        expOnMobKill = levelSystemConf.getDouble(systemName+".exp-gain.on-mobkill");
        expOnPlayerKill = levelSystemConf.getDouble(systemName+".exp-gain.on-playerkill");
        expOnBlockBreak = levelSystemConf.getDouble(systemName+".exp-gain.on-blockbreak");
        expOnBlockPlace = levelSystemConf.getDouble(systemName+".exp-gain.on-blockplace");

        baseExpRequirement = levelSystemConf.getDouble(systemName+".exp-requirements.base-exp-req");
        expRequirementMultiplier = levelSystemConf.getDouble(systemName+".exp-requirements.exp-req-multiplier");

        autoAddPlayers = levelSystemConf.getBoolean(systemName+".auto-add-players");

        scalarType = parseScalarType(levelSystemConf.getString(systemName+".exp-requirements.exp-scalar-type"));
        rebuildExpRequirementTable();

        expGainMsg = levelSystemConf.getString(systemName+".exp-gain-message", "");

        boosterStartMsg = levelSystemConf.getString(systemName+".booster-start-message");
        boosterEndMsg = levelSystemConf.getString(systemName+".booster-end-message");

        String fileName = "systems/" + systemName + ".yml";
        mobKillExp = loadExpOverrides(plugin.getMobsConf(), systemName, EntityType.class, Tag.REGISTRY_ENTITY_TYPES, fileName);
        spawnerMobKillExp = loadExpOverrides(plugin.getMobsConf(), systemName+".spawner", EntityType.class, Tag.REGISTRY_ENTITY_TYPES, fileName);
        spawnerMobMultiplier = levelSystemConf.getDouble(systemName+".spawner-mobs.exp-multiplier", 1);
        blockBreakExp = loadExpOverrides(plugin.getBlocksConf(), systemName+".break", Material.class, Tag.REGISTRY_BLOCKS, fileName);
        blockBreakSilkExp = loadExpOverrides(plugin.getBlocksConf(), systemName+".break-silk-touch", Material.class, Tag.REGISTRY_BLOCKS, fileName);
        blockPlaceExp = loadExpOverrides(plugin.getBlocksConf(), systemName+".place", Material.class, Tag.REGISTRY_BLOCKS, fileName);
        customBlockBreakExp = loadCustomBlockExp(plugin.getBlocksConf(), systemName+".break");
        customBlockBreakSilkExp = loadCustomBlockExp(plugin.getBlocksConf(), systemName+".break-silk-touch");
        customBlockPlaceExp = loadCustomBlockExp(plugin.getBlocksConf(), systemName+".place");
    }

    // A missing or misspelled scalar type used to stop the whole plugin from loading, so warn and fall back to LINEAR instead
    private ScalarType parseScalarType(String value) {
        if (value != null) {
            try {
                return ScalarType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // Warned below
            }
        }
        plugin.getLogger().warning("Level system " + systemName + " has " + (value == null ? "no" : "an invalid") + " exp-scalar-type"
                + (value == null ? "" : " '" + value + "'") + ", using LINEAR. Options: " + Arrays.toString(ScalarType.values()));
        return ScalarType.LINEAR;
    }

    /***
     * Reads a section of names to exp values. Keys can be:
     * an exact name (DIAMOND_ORE), a tag ('#minecraft:logs'), or a wildcard ('*_ORE').
     * When several match, the most specific wins: exact names, then tags, then wildcards.
     * Unknown names are warned about and skipped. Custom block ids (itemsadder:..., craftengine:...) are read by loadCustomBlockExp.
     */
    private <E extends Enum<E> & Keyed> Map<E, Double> loadExpOverrides(YamlConfiguration conf, String path, Class<E> type,
                                                                      String tagRegistry, String fileName) {
        Map<E, Double> overrides = new EnumMap<>(type);
        ConfigurationSection section = conf.getConfigurationSection(path);
        if (section == null) {
            return overrides;
        }
        Map<String, Double> wildcards = new LinkedHashMap<>();
        Map<String, Double> tags = new LinkedHashMap<>();
        Map<String, Double> exact = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key) || isCustomBlockId(key)) {
                // A sub section like spawner in the mobs section, or a custom block
                continue;
            }
            (key.startsWith("#") ? tags : key.contains("*") ? wildcards : exact).put(key, section.getDouble(key));
        }

        wildcards.forEach((key, exp) -> {
            Pattern pattern = Pattern.compile(Pattern.quote(key.toUpperCase(Locale.ROOT)).replace("*", "\\E.*\\Q"));
            int matched = 0;
            for (E value : type.getEnumConstants()) {
                if (pattern.matcher(value.name()).matches()) {
                    overrides.put(value, exp);
                    matched++;
                }
            }
            if (matched == 0) {
                plugin.getLogger().warning("'" + key + "' in " + fileName + " at " + path + " doesn't match any " + type.getSimpleName());
            }
        });
        tags.forEach((key, exp) -> {
            NamespacedKey tagKey = NamespacedKey.fromString(key.substring(1).toLowerCase(Locale.ROOT));
            Tag<E> tag = tagKey == null ? null : Bukkit.getTag(tagRegistry, tagKey, type);
            if (tag == null) {
                plugin.getLogger().warning("Unknown tag '" + key + "' in " + fileName + " at " + path + ", skipping it");
                return;
            }
            tag.getValues().forEach(value -> overrides.put(value, exp));
        });
        exact.forEach((key, exp) -> {
            try {
                overrides.put(Enum.valueOf(type, key.toUpperCase(Locale.ROOT)), exp);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown " + type.getSimpleName() + " '" + key + "' in " + fileName + " at " + path + ", skipping it");
            }
        });
        return overrides;
    }

    private static boolean isCustomBlockId(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.startsWith("itemsadder:") || lower.startsWith("craftengine:");
    }

    // Reads the ItemsAdder and CraftEngine block entries of a section, keyed by their lower case id
    private Map<String, Double> loadCustomBlockExp(YamlConfiguration conf, String path) {
        Map<String, Double> custom = new HashMap<>();
        ConfigurationSection section = conf.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (isCustomBlockId(key)) {
                    custom.put(key.toLowerCase(Locale.ROOT), section.getDouble(key));
                }
            }
        }
        return custom;
    }

    /***
     * Writes this system's settings to systems/[name].yml. The file is written in the background, errors are logged.
     */
    public void saveToConfig() {
        // Written to the system's own file in systems/, keeping its other sections (mobs, blocks, milestones, ...)
        YamlConfiguration file = plugin.getSystemFile(systemName);
        file.set("levelup-message",lvlUpMsg);

        file.set("use-milestones",useMilestones);
        file.set("auto-add-players",autoAddPlayers);

        file.set("max-level",maxLevel);
        file.set("exp-multiplier",expMultiplier);
        file.set("ignore-creative",ignoreCreative);
        file.set("disabled-worlds", disabledWorlds.isEmpty() ? null : new ArrayList<>(disabledWorlds));

        file.set("exp-gain.on-chat",expOnChat);
        file.set("exp-gain.on-mobkill",expOnMobKill);
        file.set("spawner-mobs.exp-multiplier",spawnerMobMultiplier);
        file.set("exp-gain.on-playerkill",expOnPlayerKill);
        file.set("exp-gain.on-blockbreak",expOnBlockBreak);
        file.set("exp-gain.on-blockplace",expOnBlockPlace);

        file.set("exp-requirements.base-exp-req",baseExpRequirement);
        file.set("exp-requirements.exp-req-multiplier",expRequirementMultiplier);

        file.set("exp-requirements.exp-scalar-type",scalarType.toString());

        file.set("exp-gain-message",expGainMsg);

        file.set("booster-start-message",boosterStartMsg);
        file.set("booster-end-message",boosterEndMsg);

        SystemConfigs.save(plugin, systemName, file);
    }

    public boolean doAutoAddPlayers() {
        return autoAddPlayers;
    }

    public void setAutoAddPlayers(boolean autoAddPlayers) {
        this.autoAddPlayers = autoAddPlayers;
    }

    /***
     * @return the total exp needed to go from level 0 to level + 1
     */
    public double getExpRequirement(int level) {
        if (level < 0) {
            return 0;
        }
        double[] table = expRequirementTable;
        if (level < table.length) {
            return table[level];
        }
        // Past the precomputed levels, only happens above max level
        return calculateExpRequirement(level);
    }

    // Precomputes getExpRequirement for every level up to max level, so level checks don't loop on every exp gain.
    // Each entry is built from the previous one using the same steps as calculateExpRequirement, so the values are identical
    private void rebuildExpRequirementTable() {
        int size = Math.clamp((long) maxLevel + 1, 1, MAX_PRECOMPUTED_LEVELS);
        double[] table = new double[size];
        table[0] = baseExpRequirement;

        double expLevelReq = baseExpRequirement;
        for (int level = 1; level < size; level++) {
            if (scalarType == ScalarType.EXPONENTIAL) {
                expLevelReq = expLevelReq * expRequirementMultiplier;
                table[level] = table[level - 1] + expLevelReq;
            } else if (scalarType == ScalarType.SQRT) {
                table[level] = table[level - 1] + sqrtLevelCost(level);
            } else {
                // assume linear
                table[level] = table[level - 1] + (level * (baseExpRequirement * expRequirementMultiplier) + baseExpRequirement);
            }
        }
        expRequirementTable = table;
    }

    // SQRT: the exp to go from a level to the next is base + base * multiplier * sqrt(level).
    // Each level costs more than the last, but the increase gets smaller, so it's gentler than LINEAR at high levels
    private double sqrtLevelCost(int level) {
        return baseExpRequirement + baseExpRequirement * expRequirementMultiplier * Math.sqrt(level);
    }

    private double calculateExpRequirement(int level) {
        if (level==0) {
            return baseExpRequirement;
        }
        if (level < 0) {
            return 0;
        }
        double expLevelReq;
        double expReq = 0;
        if (scalarType == ScalarType.EXPONENTIAL) {
            expLevelReq = baseExpRequirement;
            expReq = baseExpRequirement;
            for (int calcLevel = 0;calcLevel<level;calcLevel++) {
                expLevelReq = expLevelReq*expRequirementMultiplier;
                expReq += expLevelReq;
            }
        } else if (scalarType == ScalarType.SQRT){
            for (int calcLevel = 0;calcLevel<=level;calcLevel++) {
                expReq += sqrtLevelCost(calcLevel);
            }
        } else {
            // assume linear
            // <= so the cost of the current level is included, matching the exponential formula
            for (int calcLevel = 0;calcLevel<=level;calcLevel++) {
                expLevelReq = calcLevel * (baseExpRequirement * expRequirementMultiplier) + baseExpRequirement;
                expReq += expLevelReq;
            }

        }
        return expReq;
    }

    public void sendLevelupMessage(Player p, int level) {
        // Support MiniMessage formatting for colors by using MiniMessage API
        if (p != null && sendsMsgOnLevelup()) {
            String msg = lvlUpMsg.replace("%player%",p.getName()).replace("%level%",String.valueOf(level));
            p.sendMessage(MessageFormatter.format(p, msg));
        }
    }

    public LeveledPlayer getLeveledPlayer(Player p) {
        return LeveledPlayer.fromPlayer(p,this.systemName);
    }

    /***
     * Gives the rewards of every milestone for the level: the milestone for that exact level, then each repeating
     * (every-N) milestone the level is a multiple of
     */
    public void runMilestone(int level, Player p) {
        if (p == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        if (milestones.contains(level)) {
            keys.add(String.valueOf(level));
        }
        for (int interval : repeatingMilestones) {
            if (level % interval == 0) {
                keys.add("every-" + interval);
            }
        }
        if (keys.isEmpty()) {
            return;
        }

        ConfigurationSection systemMilestones = plugin.getMilestonesConf().getConfigurationSection(systemName);
        for (String key : keys) {
            ConfigurationSection milestone = findIgnoreCase(systemMilestones, key);
            if (milestone != null) {
                runMilestoneRewards(milestone, level, p);
            }
        }
    }

    // Messages, commands and permissions of one milestone
    private void runMilestoneRewards(ConfigurationSection milestone, int level, Player p) {
        for (String message : milestone.getStringList("messages")) {
            p.sendMessage(MessageFormatter.format(p, fillMilestonePlaceholders(message, level, p)));
        }
        // Commands run as the console unless run-as is player
        boolean runAsPlayer = milestone.getString("run-as", "console").equalsIgnoreCase("player");
        for (String command : milestone.getStringList("commands")) {
            String filled = MessageFormatter.formatPlain(p, fillMilestonePlaceholders(command, level, p));
            if (runAsPlayer) {
                p.performCommand(filled.startsWith("/") ? filled.substring(1) : filled);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), filled);
            }
        }

        if (plugin.enabledCompats.contains("LuckPerms")) {
            List<String> perms = milestone.getStringList("permissions");
            LuckPermsCompat lpComp = new LuckPermsCompat();
            lpComp.addPermissions(p,perms);
        }
    }

    private String fillMilestonePlaceholders(String text, int level, Player p) {
        return text.replace("%player%", p.getName()).replace("%level%", String.valueOf(level)).replace("%system%", systemName);
    }

    // A sub section by key, ignoring case (so Every-10 and every-10 both work)
    private static ConfigurationSection findIgnoreCase(ConfigurationSection section, String key) {
        if (section == null) {
            return null;
        }
        for (String existing : section.getKeys(false)) {
            if (existing.equalsIgnoreCase(key) && section.isConfigurationSection(existing)) {
                return section.getConfigurationSection(existing);
            }
        }
        return null;
    }


    // setters and getters


    /***
     * @return whether a booster starting or ending in this system is broadcast
     */
    public boolean broadcastsBoosters() {
        return !getBoosterStartMsg().isEmpty() || !getBoosterEndMsg().isEmpty();
    }

    /***
     * @deprecated booster broadcasts are turned off by setting the messages to empty, use setBoosterStartMsg / setBoosterEndMsg
     */
    @Deprecated
    public void setBroadcastBoosters(boolean broadcastBoosters) {
        // On uses config.yml's messages, off turns both off for this system
        setBoosterStartMsg(broadcastBoosters ? null : "");
        setBoosterEndMsg(broadcastBoosters ? null : "");
    }

    /***
     * @return the message broadcast when a booster starts: this system's own, or config.yml's. Empty when it's turned off.
     * Supports MiniMessage and the %multiplier%, %system% and %duration% placeholders
     */
    public String getBoosterStartMsg() {
        return boosterStartMsg != null ? boosterStartMsg : plugin.getConfig().getString("booster-start-message", DEFAULT_BOOSTER_START_MSG);
    }

    /***
     * @param boosterStartMsg this system's own message, empty to turn it off for this system, or null to use config.yml's
     */
    public void setBoosterStartMsg(String boosterStartMsg) {
        this.boosterStartMsg = boosterStartMsg;
    }

    /***
     * @return the message broadcast when a booster ends: this system's own, or config.yml's. Empty when it's turned off.
     * Supports MiniMessage and the %multiplier% and %system% placeholders
     */
    public String getBoosterEndMsg() {
        return boosterEndMsg != null ? boosterEndMsg : plugin.getConfig().getString("booster-end-message", DEFAULT_BOOSTER_END_MSG);
    }

    /***
     * @param boosterEndMsg this system's own message, empty to turn it off for this system, or null to use config.yml's
     */
    public void setBoosterEndMsg(String boosterEndMsg) {
        this.boosterEndMsg = boosterEndMsg;
    }

    /***
     * @return the message shown above the hotbar when exp is gained, empty when it's turned off
     */
    public String getExpGainMsg() {
        return expGainMsg == null ? "" : expGainMsg;
    }

    /***
     * @param expGainMsg the message shown above the hotbar when exp is gained, empty or null to turn it off
     */
    public void setExpGainMsg(String expGainMsg) {
        this.expGainMsg = expGainMsg == null ? "" : expGainMsg;
    }

    public boolean showExpGain() {
        return !getExpGainMsg().isEmpty();
    }

    /***
     * @deprecated the exp gain message is turned off by setting it to empty, use setExpGainMsg
     */
    @Deprecated
    public void setShowExpGain(boolean showExpGain) {
        if (!showExpGain) {
            setExpGainMsg("");
        } else if (!showExpGain()) {
            setExpGainMsg(DEFAULT_EXP_GAIN_MSG);
        }
    }

    /***
     * @return the intervals of this system's repeating milestones, e.g. 10 for an every-10 milestone
     */
    public List<Integer> getRepeatingMilestones() {
        return Collections.unmodifiableList(repeatingMilestones);
    }

    public List<Integer> getMilestones() {
        return milestones;
    }

    public boolean sendsMsgOnLevelup() {
        return lvlUpMsg != null && !lvlUpMsg.isEmpty();
    }

    /***
     * @deprecated the level up message is turned off by setting it to empty, use setLevelUpMsg(String)
     */
    @Deprecated
    public void setLevelUpMsg (boolean sendMsgOnLevelup) {
        if (!sendMsgOnLevelup) {
            setLevelUpMsg("");
        } else if (!sendsMsgOnLevelup()) {
            setLevelUpMsg(DEFAULT_LEVELUP_MSG);
        }
    }

    public boolean usingMilestones() {
        return useMilestones;
    }

    public void setUsingMilestones(boolean useMilestones) {
        this.useMilestones = useMilestones;
    }

    /***
     * @return the message sent on level up, empty when it's turned off
     */
    public String getLevelUpMsg() {
        return lvlUpMsg == null ? "" : lvlUpMsg;
    }

    /***
     * @param lvlUpMsg the message sent on level up, empty or null to turn it off
     */
    public void setLevelUpMsg(String lvlUpMsg) {
        this.lvlUpMsg = lvlUpMsg == null ? "" : lvlUpMsg;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
        rebuildExpRequirementTable();
    }

    /***
     * @return whether the player can earn exp in this system right now. Not in creative or spectator mode when ignore-creative
     * is on, and not in one of the disabled-worlds. Only gameplay exp is blocked, admin commands still work
     */
    public boolean canGainExp(Player player) {
        if (ignoreCreative && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        return !disabledWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public boolean ignoresCreative() {
        return ignoreCreative;
    }

    public void setIgnoreCreative(boolean ignoreCreative) {
        this.ignoreCreative = ignoreCreative;
    }

    /***
     * @return the worlds where nobody gains exp in this system, lower case
     */
    public Set<String> getDisabledWorlds() {
        return Collections.unmodifiableSet(disabledWorlds);
    }

    public void setDisabledWorlds(Collection<String> worlds) {
        disabledWorlds = new HashSet<>();
        worlds.forEach(world -> disabledWorlds.add(world.toLowerCase(Locale.ROOT)));
    }

    /***
     * @return the multiplier for all exp gained in this system, 1 for normal exp
     */
    public double getExpMultiplier() {
        return expMultiplier;
    }

    public void setExpMultiplier(double expMultiplier) {
        this.expMultiplier = expMultiplier;
    }

    /***
     * @deprecated the system multiplier used to be added to the player's multiplier, so 0 meant normal exp. Use getExpMultiplier,
     * which multiplies instead. This returns getExpMultiplier() - 1, the old equivalent
     */
    @Deprecated
    public double getExpGainMultiplier() {
        return expMultiplier - 1;
    }

    public String getSystemName() {
        return systemName;
    }

    /***
     * @deprecated see getExpGainMultiplier. Use setExpMultiplier, this sets it to expGainMultiplier + 1
     */
    @Deprecated
    public void setExpGainMultiplier(float expGainMultiplier) {
        // Through the float's decimal form, so 0.1f becomes 0.1 instead of 0.10000000149011612
        setExpGainMultiplier(Double.parseDouble(Float.toString(expGainMultiplier)));
    }

    /***
     * @deprecated see getExpGainMultiplier. Use setExpMultiplier, this sets it to expGainMultiplier + 1
     */
    @Deprecated
    public void setExpGainMultiplier(double expGainMultiplier) {
        this.expMultiplier = expGainMultiplier + 1;
    }

    public double getBaseExpRequirement() {
        return baseExpRequirement;
    }

    public void setBaseExpRequirement(double baseExpRequirement) {
        this.baseExpRequirement = baseExpRequirement;
        rebuildExpRequirementTable();
    }

    public double getExpRequirementMultiplier() {
        return expRequirementMultiplier;
    }

    public void setExpRequirementMultiplier(double expRequirementMultiplier) {
        this.expRequirementMultiplier = expRequirementMultiplier;
        rebuildExpRequirementTable();
    }

    public ScalarType getScalarType() {
        return scalarType;
    }

    public void setScalarType(ScalarType scalarType) {
        this.scalarType = scalarType;
        rebuildExpRequirementTable();
    }

    public double getExpOnChat() {
        return expOnChat;
    }

    public void setExpOnChat(double expOnChat) {
        this.expOnChat = expOnChat;
    }

    public double getDefaultExpOnMobKill() {
        return expOnMobKill;
    }

    public void setDefaultExpOnMobKill(double expOnMobKill) {
        this.expOnMobKill = expOnMobKill;
    }

    public double getExpOnPlayerKill() {
        return expOnPlayerKill;
    }

    public void setExpOnPlayerKill(double expOnPlayerKill) {
        this.expOnPlayerKill = expOnPlayerKill;
    }

    public double getExpOnBlockBreak() {
        return expOnBlockBreak;
    }

    public void setExpOnBlockBreak(double expOnBlockBreak) {
        this.expOnBlockBreak = expOnBlockBreak;
    }

    public double getExpOnBlockPlace() {
        return expOnBlockPlace;
    }

    /***
     * @return the exp for killing this mob type from the mobs section, or the system's default mob kill exp
     */
    /***
     * @return the exp for killing this mob type, adjusted for mobs that came from a mob spawner:
     * the mob's spawner exp from the mobs section if it has one, otherwise its normal exp times spawner-mobs.exp-multiplier
     */
    public double getMobKillExp(EntityType entityType, boolean fromSpawner) {
        if (!fromSpawner) {
            return getMobKillExp(entityType);
        }
        Double spawnerExp = spawnerMobKillExp.get(entityType);
        return spawnerExp != null ? spawnerExp : getMobKillExp(entityType) * spawnerMobMultiplier;
    }

    /***
     * @return the multiplier for exp from mobs that came from a mob spawner, from spawner-mobs.exp-multiplier
     */
    public double getSpawnerMobMultiplier() {
        return spawnerMobMultiplier;
    }

    public void setSpawnerMobMultiplier(double spawnerMobMultiplier) {
        this.spawnerMobMultiplier = spawnerMobMultiplier;
    }

    public double getMobKillExp(EntityType entityType) {
        return mobKillExp.getOrDefault(entityType, expOnMobKill);
    }

    /***
     * @return the exp for breaking this block from the blocks section, or the system's default block break exp
     */
    public double getBlockBreakExp(Material block, boolean silkTouch) {
        // break-silk-touch only lists what's different with silk touch, everything else uses break
        if (silkTouch && blockBreakSilkExp.containsKey(block)) {
            return blockBreakSilkExp.get(block);
        }
        return blockBreakExp.getOrDefault(block, expOnBlockBreak);
    }

    /***
     * @return the exp for placing this block from the blocks section, or the system's default block place exp
     */
    public double getBlockPlaceExp(Material block) {
        return blockPlaceExp.getOrDefault(block, expOnBlockPlace);
    }

    /***
     * @param customBlockId an ItemsAdder or CraftEngine block id, like "itemsadder:namespace:id"
     * @return the exp for breaking this custom block, or the system's default block break exp. Never the exp of the vanilla
     * block it's made from
     */
    public double getCustomBlockBreakExp(String customBlockId, boolean silkTouch) {
        String id = customBlockId.toLowerCase(Locale.ROOT);
        if (silkTouch && customBlockBreakSilkExp.containsKey(id)) {
            return customBlockBreakSilkExp.get(id);
        }
        return customBlockBreakExp.getOrDefault(id, expOnBlockBreak);
    }

    /***
     * @param customBlockId an ItemsAdder or CraftEngine block id, like "craftengine:namespace:id"
     * @return the exp for placing this custom block, or the system's default block place exp
     */
    public double getCustomBlockPlaceExp(String customBlockId) {
        return customBlockPlaceExp.getOrDefault(customBlockId.toLowerCase(Locale.ROOT), expOnBlockPlace);
    }

    public void setExpOnBlockPlace(double expOnBlockPlace) {
        this.expOnBlockPlace = expOnBlockPlace;
    }

    public PlayerCache getPlayerCache() {
        return playerCache;
    }

    /***
     * Replaces this system's player cache. Used on reload to keep online players' data when the system is recreated.
     */
    @ApiStatus.Internal
    public void setPlayerCache(PlayerCache playerCache) {
        this.playerCache = playerCache;
    }
}
