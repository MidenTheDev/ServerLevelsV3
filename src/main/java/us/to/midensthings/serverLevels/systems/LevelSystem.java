package us.to.midensthings.serverLevels.systems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.util.MessageFormatter;
import us.to.midensthings.serverLevels.compat.LuckPermsCompat;
import us.to.midensthings.serverLevels.data.PlayerCache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class LevelSystem {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    private String systemName;

    // Whether or not this system sends the player a message when they level up
    private boolean sendMsgOnLevelup;
    // Whether or not this system uses the milestone system to reward levelups
    private boolean useMilestones;

    // The message to be sent to the player on level up
    private String lvlUpMsg;

    // Max level of this system
    private int maxLevel;
    // Multiplier on exp gains for this system
    private double expGainMultiplier;

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

    private boolean showExpGain;
    private boolean autoAddPlayers;
    private String expGainMsg;

    // Whether to broadcast a message to everyone when a booster for this system starts or ends
    private boolean broadcastBoosters;
    // Custom booster broadcast messages, null uses the defaults
    private String boosterStartMsg;
    private String boosterEndMsg;

    private static final String DEFAULT_BOOSTER_START_MSG = "<gold>A <yellow>%multiplier%x</yellow> exp booster for <yellow>%system%</yellow> has started! It lasts <yellow>%duration%</yellow>.</gold>";
    private static final String DEFAULT_BOOSTER_END_MSG = "<gold>The <yellow>%multiplier%x</yellow> exp booster for <yellow>%system%</yellow> has ended.</gold>";

    private List<Integer> milestones;

    private PlayerCache playerCache;

    // Per mob/block exp overrides from mobs.yml and blocks.yml, parsed once at load
    private Map<EntityType, Double> mobKillExp;
    private Map<Material, Double> blockBreakExp;
    private Map<Material, Double> blockBreakSilkExp;
    private Map<Material, Double> blockPlaceExp;

    // Orders async writes of levelsystems.yml from saveToConfig, so an older save can't finish after a newer one
    private static final Object SAVE_LOCK = new Object();
    private static final AtomicLong SAVE_COUNTER = new AtomicLong();
    private static long lastWrittenSave = 0;

    // getExpRequirement for each level from 0 to max level, rebuilt whenever the requirement settings change
    private volatile double[] expRequirementTable;
    // Limits the table's memory use if max-level is set absurdly high, levels past this are calculated on demand
    private static final int MAX_PRECOMPUTED_LEVELS = 100_000;

    private YamlConfiguration levelSystemConf = plugin.getLevelSystemsConf();


    /***
     * Creates a level system with every setting at its zero value (0, false, no messages, LINEAR scaling), without reading levelsystems.yml.
     * It isn't registered or saved. Use saveToConfig() to write it to levelsystems.yml.
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
        blockBreakExp = new EnumMap<>(Material.class);
        blockBreakSilkExp = new EnumMap<>(Material.class);
        blockPlaceExp = new EnumMap<>(Material.class);
        rebuildExpRequirementTable();
    }

    public LevelSystem(String systemName) {
        this.systemName = systemName;
        this.playerCache = new PlayerCache(systemName);
        // Get yamlconfiguration for level systems and fill in the class
        sendMsgOnLevelup = levelSystemConf.getBoolean(systemName+".send-message-on-levelup");
        if (sendMsgOnLevelup) {
            lvlUpMsg = levelSystemConf.getString(systemName+".levelup-message");
        }

        useMilestones = levelSystemConf.getBoolean(systemName+".use-milestones");
        milestones = new ArrayList<>();
        if (useMilestones) {
            YamlConfiguration milestoneConf = plugin.getMilestonesConf();
            if (milestoneConf.getConfigurationSection(systemName) != null)  {
                // setup milestones
                milestoneConf.getConfigurationSection(systemName).getKeys(false).forEach(milestone -> {
                    milestones.add(Integer.parseInt(milestone));
                });
            } else {
                plugin.getLogger().warning("Level System " + systemName + " has milestones enabled but doesn't have any milestones in milestones.yml!");
            }

        }

        maxLevel = levelSystemConf.getInt(systemName+".max-level", 100);
        expGainMultiplier = levelSystemConf.getDouble(systemName+".exp-gain-multiplier");

        expOnChat = levelSystemConf.getDouble(systemName+".exp-gain.on-chat");
        expOnMobKill = levelSystemConf.getDouble(systemName+".exp-gain.on-mobkill");
        expOnPlayerKill = levelSystemConf.getDouble(systemName+".exp-gain.on-playerkill");
        expOnBlockBreak = levelSystemConf.getDouble(systemName+".exp-gain.on-blockbreak");
        expOnBlockPlace = levelSystemConf.getDouble(systemName+".exp-gain.on-blockplace");

        baseExpRequirement = levelSystemConf.getDouble(systemName+".exp-requirements.base-exp-req");
        expRequirementMultiplier = levelSystemConf.getDouble(systemName+".exp-requirements.exp-req-multiplier");

        autoAddPlayers = levelSystemConf.getBoolean(systemName+".auto-add-players");

        scalarType = ScalarType.valueOf(levelSystemConf.getString(systemName+".exp-requirements.exp-scalar-type"));
        rebuildExpRequirementTable();

        showExpGain = levelSystemConf.getBoolean(systemName+".show-exp-gain");
        expGainMsg = levelSystemConf.getString(systemName+".exp-gain-message");

        broadcastBoosters = levelSystemConf.getBoolean(systemName+".broadcast-boosters", false);
        boosterStartMsg = levelSystemConf.getString(systemName+".booster-start-message");
        boosterEndMsg = levelSystemConf.getString(systemName+".booster-end-message");

        mobKillExp = loadExpOverrides(plugin.getMobsConf(), systemName, EntityType.class, "mobs.yml");
        blockBreakExp = loadExpOverrides(plugin.getBlocksConf(), systemName+".break.no-silk", Material.class, "blocks.yml");
        blockBreakSilkExp = loadExpOverrides(plugin.getBlocksConf(), systemName+".break.silk", Material.class, "blocks.yml");
        blockPlaceExp = loadExpOverrides(plugin.getBlocksConf(), systemName+".place", Material.class, "blocks.yml");
    }

    // Reads a section of enum names to exp values, warning about (and skipping) names that don't exist
    private <E extends Enum<E>> Map<E, Double> loadExpOverrides(YamlConfiguration conf, String path, Class<E> type, String fileName) {
        Map<E, Double> overrides = new EnumMap<>(type);
        ConfigurationSection section = conf.getConfigurationSection(path);
        if (section == null) {
            return overrides;
        }
        for (String key : section.getKeys(false)) {
            try {
                overrides.put(Enum.valueOf(type, key.toUpperCase()), section.getDouble(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown " + type.getSimpleName() + " '" + key + "' in " + fileName + " at " + path + ", skipping it");
            }
        }
        return overrides;
    }

    /***
     * Writes this system's settings to levelsystems.yml. The file is written in the background, errors are logged.
     */
    public void saveToConfig() {
         levelSystemConf.set(systemName+".send-message-on-levelup",sendMsgOnLevelup);
        if (sendMsgOnLevelup) {
            levelSystemConf.set(systemName+".levelup-message",lvlUpMsg);
        }

        levelSystemConf.set(systemName+".use-milestones",useMilestones);
        levelSystemConf.set(systemName+".auto-add-players",autoAddPlayers);

        levelSystemConf.set(systemName+".max-level",maxLevel);
        levelSystemConf.set(systemName+".exp-gain-multiplier",expGainMultiplier);

        levelSystemConf.set(systemName+".exp-gain.on-chat",expOnChat);
        levelSystemConf.set(systemName+".exp-gain.on-mobkill",expOnMobKill);
        levelSystemConf.set(systemName+".exp-gain.on-playerkill",expOnPlayerKill);
        levelSystemConf.set(systemName+".exp-gain.on-blockbreak",expOnBlockBreak);
        levelSystemConf.set(systemName+".exp-gain.on-blockplace",expOnBlockPlace);

        levelSystemConf.set(systemName+".exp-requirements.base-exp-req",baseExpRequirement);
        levelSystemConf.set(systemName+".exp-requirements.exp-req-multiplier",expRequirementMultiplier);

        levelSystemConf.set(systemName+".exp-requirements.exp-scalar-type",scalarType.toString());

        levelSystemConf.set(systemName+".show-exp-gain",showExpGain);
        levelSystemConf.set(systemName+".exp-gain-message",expGainMsg);

        levelSystemConf.set(systemName+".broadcast-boosters",broadcastBoosters);
        levelSystemConf.set(systemName+".booster-start-message",boosterStartMsg);
        levelSystemConf.set(systemName+".booster-end-message",boosterEndMsg);

        // Build the file contents now, then write them off the main thread
        String yaml = levelSystemConf.saveToString();
        File levelsystemsYml = new File(plugin.getDataFolder()+"/levelsystems.yml");
        long saveId = SAVE_COUNTER.incrementAndGet();
        Runnable write = () -> {
            synchronized (SAVE_LOCK) {
                // A newer save already wrote the file, don't overwrite it with older contents
                if (saveId < lastWrittenSave) {
                    return;
                }
                try {
                    Files.writeString(levelsystemsYml.toPath(), yaml);
                    lastWrittenSave = saveId;
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save levelsystems.yml: " + e.getMessage());
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
            if (scalarType == ScalarType.EXPONENTIAL || scalarType == ScalarType.SQRT) {
                expLevelReq = expLevelReq * expRequirementMultiplier;
                // SQRT doesn't include the base requirement in its total, see calculateExpRequirement
                double previous = (scalarType == ScalarType.SQRT && level == 1) ? 0 : table[level - 1];
                table[level] = previous + expLevelReq;
            } else {
                // assume linear
                table[level] = table[level - 1] + (level * (baseExpRequirement * expRequirementMultiplier) + baseExpRequirement);
            }
        }
        expRequirementTable = table;
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
            // TODO: Actual sqrt formula
            expLevelReq = baseExpRequirement;
            for (int calcLevel = 0;calcLevel<level;calcLevel++) {
                expLevelReq = expLevelReq*expRequirementMultiplier;
                expReq += expLevelReq;
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
        if (p != null && this.sendMsgOnLevelup && lvlUpMsg != null) {
            String msg = lvlUpMsg.replace("%player%",p.getName()).replace("%level%",String.valueOf(level));
            p.sendMessage(MessageFormatter.format(p, msg));
        }
    }

    public LeveledPlayer getLeveledPlayer(Player p) {
        return LeveledPlayer.fromPlayer(p,this.systemName);
    }

    public void runMilestone(int level, Player p) {
        if (p == null || !milestones.contains(level)) {
            // No milestones found
            return;
        }

        YamlConfiguration milestoneConf = plugin.getMilestonesConf();
        List<String> messages = milestoneConf.getStringList(systemName+"."+level+".messages");
        List<String> commands = milestoneConf.getStringList(systemName+"."+level+".commands");

        for(String message : messages) {
            p.sendMessage(MessageFormatter.format(p, message.replace("%player%",p.getName())));
        }
        for(String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),MessageFormatter.formatPlain(p, command.replace("%player%",p.getName())));
        }

        if (plugin.enabledCompats.contains("LuckPerms")) {
            List<String> perms = milestoneConf.getStringList(systemName+"."+level+".permissions");
            LuckPermsCompat lpComp = new LuckPermsCompat();
            lpComp.addPermissions(p,perms);
        }
    }


    // setters and getters


    public boolean broadcastsBoosters() {
        return broadcastBoosters;
    }

    public void setBroadcastBoosters(boolean broadcastBoosters) {
        this.broadcastBoosters = broadcastBoosters;
    }

    /***
     * @return the message broadcast when a booster starts. Supports MiniMessage and the %multiplier%, %system% and %duration% placeholders
     */
    public String getBoosterStartMsg() {
        return boosterStartMsg == null ? DEFAULT_BOOSTER_START_MSG : boosterStartMsg;
    }

    public void setBoosterStartMsg(String boosterStartMsg) {
        this.boosterStartMsg = boosterStartMsg;
    }

    /***
     * @return the message broadcast when a booster ends. Supports MiniMessage and the %multiplier% and %system% placeholders
     */
    public String getBoosterEndMsg() {
        return boosterEndMsg == null ? DEFAULT_BOOSTER_END_MSG : boosterEndMsg;
    }

    public void setBoosterEndMsg(String boosterEndMsg) {
        this.boosterEndMsg = boosterEndMsg;
    }

    public String getExpGainMsg() {
        if (expGainMsg == null) {
            return "<green>Gained </green><dark_green>%exp%</dark_green><green> exp</green>";
        }
        return expGainMsg;
    }

    public void setExpGainMsg(String expGainMsg) {
        this.expGainMsg = expGainMsg;
    }

    public boolean showExpGain() {
        return showExpGain;
    }

    public void setShowExpGain(boolean showExpGain) {
        this.showExpGain = showExpGain;
    }

    public List<Integer> getMilestones() {
        return milestones;
    }

    public boolean sendsMsgOnLevelup() {
        return sendMsgOnLevelup;
    }

    public void setLevelUpMsg (boolean sendMsgOnLevelup) {
        this.sendMsgOnLevelup = sendMsgOnLevelup;
    }

    public boolean usingMilestones() {
        return useMilestones;
    }

    public void setUsingMilestones(boolean useMilestones) {
        this.useMilestones = useMilestones;
    }

    public String getLevelUpMsg() {
        return lvlUpMsg;
    }

    public void setLevelUpMsg(String lvlUpMsg) {
        this.lvlUpMsg = lvlUpMsg;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
        rebuildExpRequirementTable();
    }

    public double getExpGainMultiplier() {
        return expGainMultiplier;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setExpGainMultiplier(float expGainMultiplier) {
        // Through the float's decimal form, so 0.1f becomes 0.1 instead of 0.10000000149011612
        setExpGainMultiplier(Double.parseDouble(Float.toString(expGainMultiplier)));
    }

    public void setExpGainMultiplier(double expGainMultiplier) {
        this.expGainMultiplier = expGainMultiplier;
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
     * @return the exp for killing this mob type from mobs.yml, or the system's default mob kill exp
     */
    public double getMobKillExp(EntityType entityType) {
        return mobKillExp.getOrDefault(entityType, expOnMobKill);
    }

    /***
     * @return the exp for breaking this block from blocks.yml, or the system's default block break exp
     */
    public double getBlockBreakExp(Material block, boolean silkTouch) {
        return (silkTouch ? blockBreakSilkExp : blockBreakExp).getOrDefault(block, expOnBlockBreak);
    }

    /***
     * @return the exp for placing this block from blocks.yml, or the system's default block place exp
     */
    public double getBlockPlaceExp(Material block) {
        return blockPlaceExp.getOrDefault(block, expOnBlockPlace);
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
