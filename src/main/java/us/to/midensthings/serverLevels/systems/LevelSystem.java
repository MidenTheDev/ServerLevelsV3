package us.to.midensthings.serverLevels.systems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.compat.LuckPermsCompat;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    private List<Integer> milestones;

    // Per mob/block exp overrides from mobs.yml and blocks.yml, parsed once at load
    private Map<EntityType, Double> mobKillExp;
    private Map<Material, Double> blockBreakExp;
    private Map<Material, Double> blockBreakSilkExp;
    private Map<Material, Double> blockPlaceExp;

    private YamlConfiguration levelSystemConf = plugin.getLevelSystemsConf();

    public LevelSystem(String systemName) {
        this.systemName = systemName;

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

        showExpGain = levelSystemConf.getBoolean(systemName+".show-exp-gain");
        expGainMsg = levelSystemConf.getString(systemName+".exp-gain-message");

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

    public void saveToConfig() {
         levelSystemConf.set(systemName+".send-message-on-levelup",sendMsgOnLevelup);
        if (sendMsgOnLevelup) {
            levelSystemConf.set(systemName+".levelup-message",lvlUpMsg);
        }

        levelSystemConf.set(systemName+".use-milestones",useMilestones);

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

        File levelsystemsYml = new File(plugin.getDataFolder()+"/levelsystems.yml");
        try {
            levelSystemConf.save(levelsystemsYml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean doAutoAddPlayers() {
        return autoAddPlayers;
    }

    public void setAutoAddPlayers(boolean autoAddPlayers) {
        this.autoAddPlayers = autoAddPlayers;
    }

    public double getExpRequirement(int level) {
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
            p.sendMessage(MiniMessage.miniMessage().deserialize(lvlUpMsg.replace("%player%",p.getName()).replace("%level%",String.valueOf(level))));
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
            p.sendMessage(MiniMessage.miniMessage().deserialize(message.replace("%player%",p.getName())));
        }
        for(String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.replace("%player%",p.getName()));
        }

        if (plugin.enabledCompats.contains("LuckPerms")) {
            List<String> perms = milestoneConf.getStringList(systemName+"."+level+".permissions");
            LuckPermsCompat lpComp = new LuckPermsCompat();
            lpComp.addPermissions(p,perms);
        }
    }


    // setters and getters


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
    }

    public double getExpGainMultiplier() {
        return expGainMultiplier;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setExpGainMultiplier(float expGainMultiplier) {
        this.expGainMultiplier = expGainMultiplier;
    }

    public double getBaseExpRequirement() {
        return baseExpRequirement;
    }

    public void setBaseExpRequirement(double baseExpRequirement) {
        this.baseExpRequirement = baseExpRequirement;
    }

    public double getExpRequirementMultiplier() {
        return expRequirementMultiplier;
    }

    public void setExpRequirementMultiplier(double expRequirementMultiplier) {
        this.expRequirementMultiplier = expRequirementMultiplier;
    }

    public ScalarType getScalarType() {
        return scalarType;
    }

    public void setScalarType(ScalarType scalarType) {
        this.scalarType = scalarType;
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
}
