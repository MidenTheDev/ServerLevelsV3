package us.to.midensthings.serverLevels.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.LevelSystemRegistry;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.ScalarType;
import us.to.midensthings.serverLevels.util.Messages;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/***
 * Commands for editing existing level systems. Changes apply right away, /ls save writes them to the system's file in systems/.
 */
public class LSCommands {

    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    private final SuggestionProvider<CommandSourceStack> systemSuggestions = (ctx, builder) -> {
        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
            builder.suggest(levelSystem.getSystemName());
        });
        return builder.buildFuture();
    };

    public LiteralCommandNode<CommandSourceStack> lsRootCommand = Commands.literal("ls")
            // ls set [system] [setting] [value]
            .then(Commands.literal("set").requires(source -> source.getSender().hasPermission("serverlevels.commands.editsystem"))
                    .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests(systemSuggestions)

                            .then(setting("maxLevel", IntegerArgumentType.integer(0), Integer.class,
                                    LevelSystem::getMaxLevel, LevelSystem::setMaxLevel))
                            .then(setting("expMultiplier", DoubleArgumentType.doubleArg(0), Double.class,
                                    LevelSystem::getExpMultiplier, LevelSystem::setExpMultiplier))

                            .then(setting("baseExpRequirement", DoubleArgumentType.doubleArg(0), Double.class,
                                    LevelSystem::getBaseExpRequirement, LevelSystem::setBaseExpRequirement))
                            .then(setting("expRequirementMultiplier", DoubleArgumentType.doubleArg(0), Double.class,
                                    LevelSystem::getExpRequirementMultiplier, LevelSystem::setExpRequirementMultiplier))
                            .then(scalarTypeSetting())

                            .then(setting("expOnChat", DoubleArgumentType.doubleArg(), Double.class,
                                    LevelSystem::getExpOnChat, LevelSystem::setExpOnChat))
                            .then(setting("expOnMobKill", DoubleArgumentType.doubleArg(), Double.class,
                                    LevelSystem::getDefaultExpOnMobKill, LevelSystem::setDefaultExpOnMobKill))
                            .then(setting("expOnPlayerKill", DoubleArgumentType.doubleArg(), Double.class,
                                    LevelSystem::getExpOnPlayerKill, LevelSystem::setExpOnPlayerKill))
                            .then(setting("expOnBlockBreak", DoubleArgumentType.doubleArg(), Double.class,
                                    LevelSystem::getExpOnBlockBreak, LevelSystem::setExpOnBlockBreak))
                            .then(setting("expOnBlockPlace", DoubleArgumentType.doubleArg(), Double.class,
                                    LevelSystem::getExpOnBlockPlace, LevelSystem::setExpOnBlockPlace))
                            .then(setting("spawnerMobMultiplier", DoubleArgumentType.doubleArg(0), Double.class,
                                    LevelSystem::getSpawnerMobMultiplier, LevelSystem::setSpawnerMobMultiplier))

                            .then(setting("autoAddPlayers", BoolArgumentType.bool(), Boolean.class,
                                    LevelSystem::doAutoAddPlayers, LevelSystem::setAutoAddPlayers))
                            .then(setting("ignoreCreative", BoolArgumentType.bool(), Boolean.class,
                                    LevelSystem::ignoresCreative, LevelSystem::setIgnoreCreative))
                            .then(setting("useMilestones", BoolArgumentType.bool(), Boolean.class,
                                    LevelSystem::usingMilestones, LevelSystem::setUsingMilestones))

                            // Messages take the rest of the command, so they can contain spaces and MiniMessage tags.
                            // "off" (or "none") turns the message off
                            .then(setting("expGainMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getExpGainMsg, (ls, msg) -> ls.setExpGainMsg(messageOrOff(msg))))
                            .then(setting("levelUpMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getLevelUpMsg, (ls, msg) -> ls.setLevelUpMsg(messageOrOff(msg))))

                            // "default" goes back to the messages in config.yml
                            .then(setting("boosterStartMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getBoosterStartMsg, (ls, msg) -> ls.setBoosterStartMsg(boosterMessage(msg))))
                            .then(setting("boosterEndMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getBoosterEndMsg, (ls, msg) -> ls.setBoosterEndMsg(boosterMessage(msg))))))

            // ls save [system]
            .then(Commands.literal("save").requires(source -> source.getSender().hasPermission("serverlevels.commands.savesystem"))
                    .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests(systemSuggestions)
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                LevelSystem levelSystem = getLevelSystem(ctx);
                                if (levelSystem == null) {
                                    return Command.SINGLE_SUCCESS;
                                }

                                levelSystem.saveToConfig();
                                Messages.send(sender, "system-saved", "system", levelSystem.getSystemName());
                                return Command.SINGLE_SUCCESS;
                            })))
            .then(Commands.literal("create").requires(source -> source.getSender().hasPermission("serverlevels.commands.create"))
                    .then(Commands.argument("systemName",StringArgumentType.word())
                            .executes(ctx -> {
                                // ls create [systemName]
                                CommandSender sender = ctx.getSource().getSender();
                                String systemName = StringArgumentType.getString(ctx, "systemName");

                                if (!LevelSystemRegistry.isValidSystemName(systemName)) {
                                    Messages.send(sender, "invalid-system-name");
                                    return Command.SINGLE_SUCCESS;
                                }
                                String existingName = findExistingSystemName(systemName);
                                if (existingName != null) {
                                    Messages.send(sender, "system-name-taken", "system", existingName);
                                    return Command.SINGLE_SUCCESS;
                                }

                                // Create the table first. It's queued on the database thread, so players added to the system later always find it
                                plugin.getDatabaseManager().createTable(systemName);
                                plugin.getLevelSystemRegistry().registerLevelSystem(LevelSystem.createEmpty(systemName));

                                Messages.send(sender, "system-created", "system", systemName);
                                // The name is only letters, numbers and underscores, so it's safe inside the click actions
                                Messages.send(sender, "system-created-help", "system", systemName);
                                return Command.SINGLE_SUCCESS;
                            })
                    ))
            // ls booster [system] add|list|cancel, same as /sl booster
            .then(BoosterCommands.boosterCommand())
            .build();


    /***
     * Builds a /ls set [system] [setting] [value] command that changes one setting and tells the sender the old and new value
     */
    private <T> LiteralArgumentBuilder<CommandSourceStack> setting(String name, ArgumentType<T> argumentType, Class<T> valueType,
                                                                   Function<LevelSystem, Object> getter, BiConsumer<LevelSystem, T> setter) {
        return Commands.literal(name)
                .then(Commands.argument("value", argumentType)
                        .executes(ctx -> {
                            LevelSystem levelSystem = getLevelSystem(ctx);
                            if (levelSystem == null) {
                                return Command.SINGLE_SUCCESS;
                            }

                            Object oldValue = getter.apply(levelSystem);
                            T newValue = ctx.getArgument("value", valueType);
                            setter.accept(levelSystem, newValue);
                            sendChanged(ctx, levelSystem, name, oldValue, newValue);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // scalarType needs its own command since it's an enum, and brigadier has no enum argument
    private LiteralArgumentBuilder<CommandSourceStack> scalarTypeSetting() {
        return Commands.literal("scalarType")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (ScalarType type : ScalarType.values()) {
                                builder.suggest(type.name());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            LevelSystem levelSystem = getLevelSystem(ctx);
                            if (levelSystem == null) {
                                return Command.SINGLE_SUCCESS;
                            }

                            String input = StringArgumentType.getString(ctx, "value");
                            ScalarType newType;
                            try {
                                newType = ScalarType.valueOf(input.toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                Messages.send(ctx.getSource().getSender(), "invalid-scalar-type");
                                return Command.SINGLE_SUCCESS;
                            }

                            ScalarType oldType = levelSystem.getScalarType();
                            levelSystem.setScalarType(newType);
                            sendChanged(ctx, levelSystem, "scalarType", oldType, newType);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void sendChanged(CommandContext<CommandSourceStack> ctx, LevelSystem levelSystem, String setting, Object oldValue, Object newValue) {
        CommandSender sender = ctx.getSource().getSender();
        // Values are escaped by Messages, so message settings are shown as typed, not formatted
        Messages.send(sender, "setting-changed", "setting", setting, "system", levelSystem.getSystemName(), "old", oldValue, "new", newValue);
        Messages.send(sender, "setting-changed-save-reminder", "system", levelSystem.getSystemName());
    }

    // A booster message typed in a command, where "default" means config.yml's message and "off" means no message
    private static String boosterMessage(String message) {
        return message.equalsIgnoreCase("default") ? null : messageOrOff(message);
    }

    // A message typed in a command, where "off" or "none" means no message
    private static String messageOrOff(String message) {
        return message.equalsIgnoreCase("off") || message.equalsIgnoreCase("none") ? "" : message;
    }

    /***
     * Finds a level system that already uses this name, ignoring case. Checks the system files too, so disabled systems count.
     * Case is ignored because H2 table names aren't case sensitive, so "Mining" and "mining" would share a table.
     * @return the existing system's name, or null if the name is free
     */
    private String findExistingSystemName(String systemName) {
        for (LevelSystem levelSystem : plugin.getLevelSystemRegistry().getAllSystems()) {
            if (levelSystem.getSystemName().equalsIgnoreCase(systemName)) {
                return levelSystem.getSystemName();
            }
        }
        for (String configSystem : plugin.getLevelSystemsConf().getKeys(false)) {
            if (configSystem.equalsIgnoreCase(systemName)) {
                return configSystem;
            }
        }
        return null;
    }

    // Gets the level system from the levelSystem argument, telling the sender if it doesn't exist
    private LevelSystem getLevelSystem(CommandContext<CommandSourceStack> ctx) {
        String systemName = StringArgumentType.getString(ctx, "levelSystem");
        LevelSystem levelSystem = plugin.getLevelSystemRegistry().getLevelSystem(systemName);
        if (levelSystem == null) {
            Messages.send(ctx.getSource().getSender(), "invalid-system");
        }
        return levelSystem;
    }
}
