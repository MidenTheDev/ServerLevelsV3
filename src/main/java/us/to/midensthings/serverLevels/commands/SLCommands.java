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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.PlayerLoader;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;
import us.to.midensthings.serverLevels.util.Messages;
import us.to.midensthings.serverLevels.util.NamespacedKeys;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public class SLCommands {

    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    public LiteralCommandNode<CommandSourceStack> rootCommand = Commands.literal("sl")
            .then(Commands.literal("exp")
                    .then(Commands.literal("set").requires(source -> source.getSender().hasPermission("serverlevels.commands.set"))
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
                                                // sl exp set [player] [amount] [system]
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
                                                Messages.send(sender, "exp-set", "player", p.getName(), "amount", DoubleArgumentType.getDouble(ctx,"expAmount"), "system", levelSystem);

                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("add").requires(source -> source.getSender().hasPermission("serverlevels.commands.add"))
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl exp add [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.add")) {
                                                    Messages.send(sender, "no-permission");
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
                                                Messages.send(sender, "exp-added", "player", p.getName(), "amount", DoubleArgumentType.getDouble(ctx,"expAmount"), "system", levelSystem);
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("remove").requires(source -> source.getSender().hasPermission("serverlevels.commands.remove"))
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })


                                                    .executes(ctx -> {
                                                // sl exp remove [player] [amount] [system]

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
                                                Messages.send(sender, "exp-removed", "player", p.getName(), "amount", DoubleArgumentType.getDouble(ctx,"expAmount"), "system", levelSystem);
                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .then(Commands.literal("level")
                    .then(Commands.literal("set").requires(source -> source.getSender().hasPermission("serverlevels.commands.set"))
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })


                                                    .executes(ctx -> {
                                                // sl level set [player] [amount] [system]
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
                                                Messages.send(sender, "level-set", "player", p.getName(), "amount", IntegerArgumentType.getInteger(ctx,"levelAmount"), "system", levelSystem);
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("add").requires(source -> source.getSender().hasPermission("serverlevels.commands.add"))
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl level add [player] [amount] [system]
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
                                                Messages.send(sender, "level-added", "player", p.getName(), "amount", IntegerArgumentType.getInteger(ctx,"levelAmount"), "system", levelSystem);
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("remove").requires(source -> source.getSender().hasPermission("serverlevels.commands.remove"))
                            .then(Commands.argument("player", ArgumentTypes.player())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                                    builder.suggest(levelSystem.getSystemName());
                                                });
                                                return builder.buildFuture();
                                                    })



                                                    .executes(ctx -> {
                                                // sl level remove [player] [amount] [system]

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
                                                Messages.send(sender, "level-removed", "player", p.getName(), "amount", IntegerArgumentType.getInteger(ctx,"levelAmount"), "system", levelSystem);

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
                                    Messages.send(sender, "system-already-joined", "system", levelSystem);
                                }

                                Messages.send(sender, "system-added", "player", p.getName(), "system", levelSystem);
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

                                Messages.send(sender, "system-removed", "player", p.getName(), "system", levelSystem);
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
                                Messages.send(sender, "multiplier-set", "player", p.getName(), "multiplier", multiplier, "system", systemName);
                                return Command.SINGLE_SUCCESS;
                            })))))
            .then(Commands.literal("reload").requires(source -> source.getSender().hasPermission("serverlevels.commands.reload")).executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();

                Messages.send(sender, "reload-start");

                // Files and the database are reloaded in the background, so the server doesn't freeze while it runs
                plugin.reloadAsync().whenComplete((ignored, error) -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                        plugin.getLogger().log(Level.SEVERE, "Failed to reload Server Levels", cause);
                        Messages.send(sender, "reload-failed", "error", cause.getMessage());
                        return;
                    }
                    Messages.send(sender, "reload-done");
                });
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("progress").requires(source -> source.getSender().hasPermission("serverlevels.commands.progress"))
                    .then(Commands.argument("levelSystem",StringArgumentType.word())
                            .suggests((ctx,builder) -> {
                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                    builder.suggest(levelSystem.getSystemName());
                                });
                                return builder.buildFuture();
                            })

                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                if (!(sender instanceof Player)) {
                                    Messages.send(sender, "players-only");
                                    return Command.SINGLE_SUCCESS;
                                }

                                Player p = (Player) sender;
                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");
                                displayProgress(ctx, p, plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                            return Command.SINGLE_SUCCESS;


                            }).then(Commands.argument("player",ArgumentTypes.player())
                                    .requires(source -> source.getSender().hasPermission("serverlevels.commands.progress.other"))
                                    .executes(ctx -> {
                                        final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player",PlayerSelectorArgumentResolver.class);
                                        Player p = resolver.resolve(ctx.getSource()).getFirst();
                                        String levelSystem = StringArgumentType.getString(ctx, "levelSystem");
                                        displayProgress(ctx, p, plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                        return Command.SINGLE_SUCCESS;
                                    })

                            )))
            .then(BoosterCommands.boosterCommand())
            .then(Commands.literal("pause").requires(source -> source.getSender().hasPermission("serverlevels.commands.pause"))
                    .then(Commands.argument("player",ArgumentTypes.player()).then(Commands.argument("levelSystem",StringArgumentType.word())
                            .suggests((ctx,builder) -> {
                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                    builder.suggest(levelSystem.getSystemName());
                                });
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                // sl pause [player] [system]
                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player",PlayerSelectorArgumentResolver.class);
                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                setPlayerPaused(ctx, p, StringArgumentType.getString(ctx, "levelSystem"), true);
                                return Command.SINGLE_SUCCESS;
                            })

            )))
            .then(Commands.literal("unpause").requires(source -> source.getSender().hasPermission("serverlevels.commands.pause"))
                    .then(Commands.argument("player",ArgumentTypes.player()).then(Commands.argument("levelSystem",StringArgumentType.word())
                            .suggests((ctx,builder) -> {
                                plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                                    builder.suggest(levelSystem.getSystemName());
                                });
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                // sl unpause [player] [system]
                                final PlayerSelectorArgumentResolver resolver = ctx.getArgument("player",PlayerSelectorArgumentResolver.class);
                                Player p = resolver.resolve(ctx.getSource()).getFirst();
                                setPlayerPaused(ctx, p, StringArgumentType.getString(ctx, "levelSystem"), false);
                                return Command.SINGLE_SUCCESS;
                            })

            )))
            .build();


    /***
     * Pauses or unpauses the player's exp gains from gameplay in the system, and saves it right away
     */
    private void setPlayerPaused(CommandContext<CommandSourceStack> ctx, Player p, String levelSystem, boolean paused) {
        if (!checkValidCommand(ctx, p, plugin.getLevelSystemRegistry().getLevelSystem(levelSystem))) {
            return;
        }
        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, levelSystem);
        if (lp == null) {
            return;
        }

        CommandSender sender = ctx.getSource().getSender();
        if (lp.isPaused() == paused) {
            Messages.send(sender, paused ? "already-paused" : "already-unpaused", "player", p.getName(), "system", levelSystem);
            return;
        }

        lp.setPaused(paused);
        lp.save();
        Messages.send(sender, paused ? "paused" : "unpaused", "player", p.getName(), "system", levelSystem);
    }


    // Number of segments in the progress bar, each one is 2.5% of a level
    /***
     * Sends the sender a progress bar showing how far the player is from their next level in the system.
     * The layout is set in messages.yml
     */
    private void displayProgress(CommandContext<CommandSourceStack> ctx, Player p, LevelSystem system) {
        if (!checkValidCommand(ctx,p,system)) return;
        CommandSender sender = ctx.getSource().getSender();

        LeveledPlayer lp = getLeveledPlayerOrNotify(ctx, p, system.getSystemName());
        if (lp == null) {
            return;
        }

        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.DOWN);

        int level = lp.getLevel();
        double exp = lp.getExp();
        boolean maxLevel = level >= system.getMaxLevel();

        // Progress is measured within the current level, the same as the %serverlevels_<system>_progress% placeholder
        double nextLevelExp = lp.getNextLevelExp();
        double progress = lp.getLevelProgress();
        // Round down so the bar never looks full before the player actually levels up
        int segments = Math.max(1, Messages.getInt("progress-bar-segments", 40));
        String segment = Messages.getRaw("progress-bar-segment");
        int filledSegments = (int) Math.floor(progress * segments);
        int percent = (int) Math.floor(progress * 100);
        Component bar = Messages.get("progress-bar", "filled", segment.repeat(filledSegments), "empty", segment.repeat(segments - filledSegments));
        if (bar == null) {
            bar = Component.empty();
        }

        Object[] placeholders = {"player", p.getName(), "system", system.getSystemName(), "level", level, "next_level", level + 1,
                "exp", df.format(exp), "next_level_exp", df.format(nextLevelExp), "exp_to_next", df.format(nextLevelExp - exp),
                "percent", percent, "bar", bar};
        Messages.send(sender, sender == p ? "progress-title-self" : "progress-title-other", placeholders);
        if (maxLevel) {
            Messages.send(sender, "progress-bar-line-max", placeholders);
            Messages.send(sender, "progress-exp-max", placeholders);
        } else {
            Messages.send(sender, "progress-bar-line", placeholders);
            Messages.send(sender, "progress-exp", placeholders);
        }
        if (lp.isPaused()) {
            Messages.send(sender, sender == p ? "progress-paused-self" : "progress-paused-other", placeholders);
        }
    }

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
            Messages.send(ctx.getSource().getSender(), "not-in-system", "player", p.getName(), "system", levelSystem);
        }
        return lp;
    }

    private boolean checkValidCommand(CommandContext<CommandSourceStack> ctx,Player p, LevelSystem ls) {
        if (p == null) {
            // Player hasn't been on server before, won't be in database.
            Messages.send(ctx.getSource().getSender(), "invalid-player");
            return false;
        }
        if (ls == null) {
            // no level system found
            Messages.send(ctx.getSource().getSender(), "invalid-system");
            return false;
        }
        return true;
    }

}
