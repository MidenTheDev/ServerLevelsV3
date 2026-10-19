package us.to.midensthings.serverLevels.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.PlayerLoader;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;
import us.to.midensthings.serverLevels.util.NamespacedKeys;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class CommandCore {

    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    public LiteralCommandNode<CommandSourceStack> rootCommand = Commands.literal("sl")
            .then(Commands.literal("set").requires(source -> source.getSender().hasPermission("serverlevels.commands.set"))
                    .then(Commands.literal("exp")
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word())

                                                    .suggests((ctx,builder) -> {
                                                        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                            builder.suggest(levelSystem.getSystemName());
                                                        });
                                                        return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl set exp [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();

                                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Command Logic
                                                if (!setPlayerExp(ctx,p,DoubleArgumentType.getDouble(ctx,"expAmount"),levelSystem)) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                sender.sendMessage(
                                                        Component.text("Successfully set "+p.getName()+"'s exp to "+DoubleArgumentType.getDouble(ctx,"expAmount")+" in system "+levelSystem)
                                                        .color(TextColor.color(Color.GREEN.asRGB())));

                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })


                                                    .executes(ctx -> {
                                                // sl set level [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();

                                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Command Logic
                                                if (!setPlayerLevel(ctx,p,IntegerArgumentType.getInteger(ctx,"levelAmount"),levelSystem)) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                sender.sendMessage(
                                                        Component.text("Successfully set "+p.getName()+"'s level to "+IntegerArgumentType.getInteger(ctx,"levelAmount")+" in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .then(Commands.literal("add").requires(source -> source.getSender().hasPermission("serverlevels.commands.add"))
                    .then(Commands.literal("exp")
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl add exp [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.add")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Command Logic
                                                if (!addPlayerExp(ctx,p,DoubleArgumentType.getDouble(ctx,"expAmount"),levelSystem)) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                sender.sendMessage(
                                                        Component.text("Successfully gave "+p.getName()+" "+DoubleArgumentType.getDouble(ctx,"expAmount")+" exp in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl add level [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();

                                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Command Logic
                                                if (!addPlayerLevel(ctx,p,IntegerArgumentType.getInteger(ctx,"levelAmount"),levelSystem)) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                sender.sendMessage(
                                                        Component.text("Successfully gave "+p.getName()+" "+IntegerArgumentType.getInteger(ctx,"levelAmount")+" levels in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .then(Commands.literal("remove").requires(source -> source.getSender().hasPermission("serverlevels.commands.remove"))
                    .then(Commands.literal("exp")
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })


                                                    .executes(ctx -> {
                                                // sl remove exp [player] [amount] [system]

                                                CommandSender sender = ctx.getSource().getSender();

                                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Command Logic
                                                if (!removePlayerExp(ctx,p,DoubleArgumentType.getDouble(ctx,"expAmount"),levelSystem)) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                sender.sendMessage(
                                                        Component.text("Successfully removed "+DoubleArgumentType.getDouble(ctx,"expAmount")+" exp from "+p.getName()+" in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl remove level [player] [amount] [system]

                                                CommandSender sender = ctx.getSource().getSender();

                                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                // Command Logic
                                                if (!removePlayerLevel(ctx,p,IntegerArgumentType.getInteger(ctx,"levelAmount"),levelSystem)) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                sender.sendMessage(
                                                        Component.text("Successfully removed "+IntegerArgumentType.getInteger(ctx,"levelAmount")+" levels from "+p.getName()+" in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));

                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .then(Commands.literal("addSystem").requires(source -> source.getSender().hasPermission("serverlevels.commands.addsystem"))
                    .then(Commands.argument("player", ArgumentTypes.player())
                            .then(Commands.argument("levelSystem",StringArgumentType.word())
                                    .suggests((ctx,builder) -> {
                                        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                            builder.suggest(levelSystem.getSystemName());
                                        });
                                        return builder.buildFuture();
                                    })


                                    .executes(ctx -> {
                                // Verify permissions first
                                CommandSender sender = ctx.getSource().getSender();
                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                    return Command.SINGLE_SUCCESS;
                                }

                                LevelSystem system = plugin.getLevelSystemRegistry().getLevelSystem(levelSystem);
                                if (system.getPlayerCache().getLeveledPlayer(p.getUniqueId()) == null) {
                                    // Insert, then load from the database instead of caching a blank record, in case their real data is still loading.
                                    // Both run on the database thread in order, so the load always sees the new record
                                    plugin.getDatabaseManager().initializePlayer(p.getUniqueId(), levelSystem);
                                    PlayerLoader.loadOnlinePlayer(p, List.of(system));
                                } else {
                                    sender.sendMessage(Component.text("Player is already in system "+ levelSystem).color(TextColor.color(Color.RED.asRGB())));
                                }

                                sender.sendMessage(Component.text("Successfully added player to system "+ levelSystem).color(TextColor.color(Color.LIME.asRGB())));
                                return Command.SINGLE_SUCCESS;
                            }))))
            .then(Commands.literal("removeSystem").requires(source -> source.getSender().hasPermission("serverlevels.commands.removesystem"))
                    .then(Commands.argument("player",ArgumentTypes.player())
                            .then(Commands.argument("levelSystem",StringArgumentType.word())

                                    .suggests((ctx,builder) -> {
                                        plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                            builder.suggest(levelSystem.getSystemName());
                                        });
                                        return builder.buildFuture();
                                    })



                                    .executes(ctx -> {
                                // Verify permissions first
                                CommandSender sender = ctx.getSource().getSender();

                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                if (!(checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem)))) {
                                    return Command.SINGLE_SUCCESS;
                                }

                                plugin.getLevelSystemRegistry().getLevelSystem(levelSystem).getPlayerCache().deletePlayer(p.getUniqueId());
                                plugin.getDatabaseManager().removePlayer(p,levelSystem);

                                sender.sendMessage(Component.text("Successfully removed player from system "+ levelSystem).color(TextColor.color(Color.LIME.asRGB())));
                                return Command.SINGLE_SUCCESS;
                            }))))
            .then(Commands.literal("setMultiplier").requires(source -> source.getSender().hasPermission("serverlevels.commands.setMultiplier"))
                    .then(Commands.argument("player",ArgumentTypes.player())
                            .then(Commands.argument("multiplier",DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("levelSystem",StringArgumentType.word())

                                            .suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                            })

                                            .executes(ctx -> {
                                // sl setMultiplier [player] [multiplier] [system]
                                CommandSender sender = ctx.getSource().getSender();

                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player",PlayerSelectorArgumentResolver.class);
                                Player p = resolver.resolve(ctx.getSource()).getFirst();

                                String systemName = StringArgumentType.getString(ctx,"levelSystem");
                                if (!checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(systemName))) {
                                    return Command.SINGLE_SUCCESS;
                                }

                                double multiplier = DoubleArgumentType.getDouble(ctx,"multiplier");
                                LeveledPlayer lp = LeveledPlayer.fromPlayer(p,systemName);
                                if (lp != null) {
                                    lp.setExpMultiplier(multiplier);
                                } else {
                                    // Not in the system yet, store it so it applies if they're added later
                                    p.getPersistentDataContainer().set(NamespacedKeys.generatePlayerExpMultiplierKey(systemName), PersistentDataType.DOUBLE,multiplier);
                                }
                                sender.sendMessage(
                                        Component.text("Successfully set "+p.getName()+"'s exp multiplier to "+multiplier+" in system "+systemName)
                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                return Command.SINGLE_SUCCESS;
                            })))))
            .then(Commands.literal("reload").requires(source -> source.getSender().hasPermission("serverlevels.commands.reload")).executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();

                sender.sendMessage(Component.text("Reloading Server Levels...").color(TextColor.color(Color.GREEN.asRGB())));

                // Files and the database are reloaded in the background, so the server doesn't freeze while it runs
                plugin.reloadAsync().whenComplete((ignored, error) -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                        plugin.getLogger().log(Level.SEVERE, "Failed to reload Server Levels", cause);
                        sender.sendMessage(Component.text("Failed to reload Server Levels: " + cause.getMessage()).color(TextColor.color(Color.RED.asRGB())));
                        return;
                    }
                    sender.sendMessage(Component.text("Server Levels Reloaded").color(TextColor.color(Color.GREEN.asRGB())));
                });
                return Command.SINGLE_SUCCESS;
            }))
            .build();




    // Each of these return false (after telling the sender) if the player isn't in the level system

    private boolean setPlayerExp(CommandContext<CommandSourceStack> ctx, Player p, double newExp, String levelSystem) {
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return false;
        }
        lp.setExp(newExp);
        lp.adjustLevel();
        lp.save();
        return true;
    }

    private boolean setPlayerLevel(CommandContext<CommandSourceStack> ctx, Player p, int newLevel, String levelSystem) {
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return false;
        }
        lp.setLevel(newLevel);
        lp.adjustExp();
        lp.save();
        return true;
    }

    private boolean addPlayerExp(CommandContext<CommandSourceStack> ctx, Player p, double addedExp, String levelSystem) {
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return false;
        }

        lp.incrementExp(addedExp);
        lp.save();
        return true;
    }

    private boolean addPlayerLevel(CommandContext<CommandSourceStack> ctx, Player p, int addedLevels, String levelSystem) {
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return false;
        }

        int newLevel = lp.getLevel() + addedLevels;

        lp.setLevel(newLevel);
        lp.adjustExp();
        lp.save();
        return true;
    }

    private boolean removePlayerExp(CommandContext<CommandSourceStack> ctx, Player p, double removedExp, String levelSystem) {
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return false;
        }

        // Removals ignore exp multipliers and don't show an exp gain message
        lp.decrementExp(removedExp);
        lp.save();
        return true;
    }

    private boolean removePlayerLevel(CommandContext<CommandSourceStack> ctx, Player p, int removedLevels, String levelSystem) {
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return false;
        }

        int newLevel = lp.getLevel() - removedLevels;

        lp.setLevel(newLevel);
        lp.adjustExp();
        lp.save();
        return true;
    }

    private LeveledPlayer getLeveledPlayerOrNotify(CommandContext<CommandSourceStack> ctx, Player p, String levelSystem) {
        LeveledPlayer lp = LeveledPlayer.fromPlayer(p,levelSystem);
        if (lp == null) {
            ctx.getSource().getSender().sendMessage(Component.text(p.getName()+" is not in level system "+levelSystem+"!").color(TextColor.color(Color.RED.asRGB())));
        }
        return lp;
    }

    private boolean checkValidCommand(CommandContext<CommandSourceStack> ctx,Player p, LevelSystem ls) {
        if (p == null) {
            // Player hasn't been on server before, won't be in database.
            ctx.getSource().getSender().sendMessage(Component.text("Invalid Player!").color(TextColor.color(Color.RED.asRGB())));
            return false;
        }
        if (ls == null) {
            // no level system found
            ctx.getSource().getSender().sendMessage(Component.text("Invalid Level System!").color(TextColor.color(Color.RED.asRGB())));
            return false;
        }
        return true;
    }

}
