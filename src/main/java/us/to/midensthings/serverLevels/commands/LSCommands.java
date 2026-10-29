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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.LevelSystemRegistry;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.ScalarType;

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
                            .then(setting("expGainMultiplier", DoubleArgumentType.doubleArg(), Double.class,
                                    LevelSystem::getExpGainMultiplier, LevelSystem::setExpGainMultiplier))

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
                            .then(setting("useMilestones", BoolArgumentType.bool(), Boolean.class,
                                    LevelSystem::usingMilestones, LevelSystem::setUsingMilestones))

                            // Messages take the rest of the command, so they can contain spaces and MiniMessage tags.
                            // "off" (or "none") turns the message off
                            .then(setting("expGainMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getExpGainMsg, (ls, msg) -> ls.setExpGainMsg(messageOrOff(msg))))
                            .then(setting("levelUpMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getLevelUpMsg, (ls, msg) -> ls.setLevelUpMsg(messageOrOff(msg))))

                            .then(setting("broadcastBoosters", BoolArgumentType.bool(), Boolean.class,
                                    LevelSystem::broadcastsBoosters, LevelSystem::setBroadcastBoosters))
                            .then(setting("boosterStartMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getBoosterStartMsg, LevelSystem::setBoosterStartMsg))
                            .then(setting("boosterEndMessage", StringArgumentType.greedyString(), String.class,
                                    LevelSystem::getBoosterEndMsg, LevelSystem::setBoosterEndMsg))))

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
                                sender.sendMessage(Component.text("Saved level system " + levelSystem.getSystemName() + " to systems/" + levelSystem.getSystemName() + ".yml")
                                        .color(TextColor.color(Color.GREEN.asRGB())));
                                return Command.SINGLE_SUCCESS;
                            })))
            .then(Commands.literal("create").requires(source -> source.getSender().hasPermission("serverlevels.commands.create"))
                    .then(Commands.argument("systemName",StringArgumentType.word())
                            .executes(ctx -> {
                                // ls create [systemName]
                                CommandSender sender = ctx.getSource().getSender();
                                String systemName = StringArgumentType.getString(ctx, "systemName");

                                if (!LevelSystemRegistry.isValidSystemName(systemName)) {
                                    sender.sendMessage(Component.text("Level system names can only contain letters, numbers, and underscores!")
                                            .color(TextColor.color(Color.RED.asRGB())));
                                    return Command.SINGLE_SUCCESS;
                                }
                                String existingName = findExistingSystemName(systemName);
                                if (existingName != null) {
                                    sender.sendMessage(Component.text("A level system named " + existingName + " already exists!")
                                            .color(TextColor.color(Color.RED.asRGB())));
                                    return Command.SINGLE_SUCCESS;
                                }

                                // Create the table first. It's queued on the database thread, so players added to the system later always find it
                                plugin.getDatabaseManager().createTable(systemName);
                                plugin.getLevelSystemRegistry().registerLevelSystem(LevelSystem.createEmpty(systemName));

                                sender.sendMessage(Component.text("Created level system " + systemName + " with every setting at 0")
                                        .color(TextColor.color(Color.GREEN.asRGB())));
                                sender.sendMessage(Component.text("Use ")
                                        .append(Component.text("/ls set " + systemName + " <setting> <value>")
                                                .color(TextColor.color(Color.YELLOW.asRGB()))
                                                .clickEvent(ClickEvent.suggestCommand("/ls set " + systemName + " "))
                                                .hoverEvent(HoverEvent.showText(Component.text("Click to start the command"))))
                                        .append(Component.text(" to configure it, then "))
                                        .append(Component.text("/ls save " + systemName)
                                                .color(TextColor.color(Color.YELLOW.asRGB()))
                                                .clickEvent(ClickEvent.suggestCommand("/ls save " + systemName))
                                                .hoverEvent(HoverEvent.showText(Component.text("Click to start the command"))))
                                        .append(Component.text(" to save it to systems/" + systemName + ".yml. Unsaved systems are removed on reload or restart."))
                                        .color(TextColor.color(Color.GRAY.asRGB())));
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
                                ctx.getSource().getSender().sendMessage(Component.text("Invalid scalar type! Use LINEAR, EXPONENTIAL or SQRT")
                                        .color(TextColor.color(Color.RED.asRGB())));
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
        // Component.text so message settings are shown as typed, not formatted
        sender.sendMessage(Component.text("Changed " + setting + " of " + levelSystem.getSystemName() + " from " + oldValue + " to " + newValue)
                .color(TextColor.color(Color.GREEN.asRGB())));
        sender.sendMessage(Component.text("Use /ls save " + levelSystem.getSystemName() + " to keep this change after a reload or restart")
                .color(TextColor.color(Color.GRAY.asRGB())));
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
            ctx.getSource().getSender().sendMessage(Component.text("Invalid Level System!").color(TextColor.color(Color.RED.asRGB())));
        }
        return levelSystem;
    }
}
