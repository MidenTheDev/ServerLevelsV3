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
import net.kyori.adventure.text.minimessage.MiniMessage;
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

import java.math.RoundingMode;
import java.text.DecimalFormat;
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
                                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You must be a player to use this command!"));
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
            .build();




    // Number of segments in the progress bar, each one is 2.5% of a level
    private static final int PROGRESS_BAR_LENGTH = 40;

    /***
     * Sends the sender a progress bar showing how far the player is from their next level in the system
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

        // Progress is measured within the current level, from the exp needed to reach it to the exp needed for the next one
        double levelStartExp = system.getExpRequirement(level - 1);
        double nextLevelExp = system.getExpRequirement(level);
        double progress;
        if (maxLevel || nextLevelExp <= levelStartExp) {
            progress = 1;
        } else {
            progress = Math.clamp((exp - levelStartExp) / (nextLevelExp - levelStartExp), 0, 1);
        }
        // Round down so the bar never looks full before the player actually levels up
        int filledSegments = (int) Math.floor(progress * PROGRESS_BAR_LENGTH);
        int percent = (int) Math.floor(progress * 100);

        String bar = "<dark_gray>[</dark_gray><green>" + "|".repeat(filledSegments) + "</green>"
                + "<dark_gray>" + "|".repeat(PROGRESS_BAR_LENGTH - filledSegments) + "]</dark_gray>";

        // Player and system names can only contain letters, numbers and underscores, so they're safe to put in MiniMessage
        String title = (sender == p ? "Your" : p.getName() + "'s") + " " + system.getSystemName() + " progress";

        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>------ <gold>" + title + "</gold> ------"));
        if (maxLevel) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>Level " + level + "</yellow> " + bar + " <gold>MAX</gold>"));
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>Exp: <white>" + df.format(exp) + "</white> · Max level reached"));
        } else {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<yellow>Level " + level + "</yellow> " + bar + " <yellow>Level " + (level + 1) + "</yellow>"));
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>Exp: <white>" + df.format(exp) + "</white> / <white>" + df.format(nextLevelExp) + "</white>"
                            + " (<green>" + percent + "%</green>) · <white>" + df.format(nextLevelExp - exp) + "</white> to next level"));
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
