package us.to.midensthings.serverLevels.systems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LevelSystem {
    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);
    String systemName;

    // Whether or not this system sends the player a message when they level up
    boolean sendMsgOnLevelup;
    // Whether or not this system uses the milestone system to reward levelups
    boolean useMilestones;

    // The message to be sent to the player on level up
    String lvlUpMsg;

    // Max level of this system
    int maxLevel;
    // Multiplier on exp gains for this system
    double expGainMultiplier;

    // EXP required to go from level 0 to level 1;
    double baseExpRequirement;
    // Multiplier for increasing exp requirements per level
    double expRequirementMultiplier;
    // ScalarType for how the multiplier is applied to the exp requirements
    ScalarType scalarType;

    // Exp gain on event
    double expOnChat;
    double expOnMobKill;
    double expOnPlayerKill;
    double expOnBlockBreak;
    double expOnBlockPlace;

    List<Integer> milestones;

    YamlConfiguration levelSystemConf = plugin.getLevelSystemsConf();

    public LevelSystem(String systemName) {
        this.systemName = systemName;

        // Get yamlconfiguration for level systems and fill in the class
        sendMsgOnLevelup = levelSystemConf.getBoolean(systemName+".send-message-on-levelup");
        if (sendMsgOnLevelup) {
            lvlUpMsg = levelSystemConf.getString(systemName+".levelup-message");
        }

        useMilestones = levelSystemConf.getBoolean(systemName+".use-milestones");
        if (useMilestones) {
            // setup milestones
            YamlConfiguration milestoneConf = plugin.getMilestonesConf();
            milestones = new ArrayList<>();
            milestoneConf.getConfigurationSection(systemName).getKeys(false).forEach(milestone -> {
                milestones.add(Integer.parseInt(milestone));
            });
        }

        maxLevel = levelSystemConf.getInt(systemName+".max-level");
        expGainMultiplier = levelSystemConf.getDouble(systemName+".exp-gain-multiplier");

        expOnChat = levelSystemConf.getDouble(systemName+".exp-gain.on-chat");
        expOnMobKill = levelSystemConf.getDouble(systemName+".exp-gain.on-mobkill");
        expOnPlayerKill = levelSystemConf.getDouble(systemName+".exp-gain.on-playerkill");
        expOnBlockBreak = levelSystemConf.getDouble(systemName+".exp-gain.on-blockbreak");
        expOnBlockPlace = levelSystemConf.getDouble(systemName+".exp-gain.on-blockplace");

        baseExpRequirement = levelSystemConf.getDouble(systemName+".exp-requirements.base-exp-req");
        expRequirementMultiplier = levelSystemConf.getDouble(systemName+".exp-requirements.exp-req-multiplier");

        scalarType = ScalarType.valueOf(levelSystemConf.getString(systemName+".exp-requirements.exp-scalar-type"));
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

        File levelsystemsYml = new File(plugin.getDataFolder()+"/levelsystems.yml");
        try {
            levelSystemConf.save(levelsystemsYml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            for (int calcLevel = 0;calcLevel<level;calcLevel++) {
                expLevelReq = calcLevel * (baseExpRequirement * expRequirementMultiplier) + baseExpRequirement;
                expReq += expLevelReq;
            }

        }
        return expReq;
    }

    public void sendLevelupMessage(Player p, int level) {
        // Support MiniMessage formatting for colors by using MiniMessage API
        p.sendMessage(MiniMessage.miniMessage().deserialize(lvlUpMsg.replace("%player%",p.getName()).replace("%level%",String.valueOf(level))));
    }

    public LeveledPlayer generateLeveledPlayer(Player p) {
        return plugin.getDatabaseManager().getLeveledPlayer(p, systemName);
    }

    public void runMilestone(int level, Player p) {
        if (!milestones.contains(level)) {
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
    }


    // setters and getters


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

    public double getExpOnMobKill() {
        return expOnMobKill;
    }

    public void setExpOnMobKill(double expOnMobKill) {
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

    public void setExpOnBlockPlace(double expOnBlockPlace) {
        this.expOnBlockPlace = expOnBlockPlace;
    }
}
