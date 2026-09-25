package us.to.midensthings.serverLevels.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.systems.LevelSystem;
import us.to.midensthings.serverLevels.systems.LeveledPlayer;

public class CommandCore {

    private final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    public LiteralCommandNode<CommandSourceStack> rootCommand = Commands.literal("sl")
            .then(Commands.literal("set")
                    .then(Commands.literal("exp")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).executes(ctx -> {
                                                // sl set exp [player] [amount] [system]

                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.set")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                Player p = Bukkit.getPlayer(StringArgumentType.getString(ctx, "player"));
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                                // Command Logic
                                                setPlayerExp(p,DoubleArgumentType.getDouble(ctx,"expAmount"),levelSystem);
                                                sender.sendMessage(
                                                        Component.text("Successfully set "+p.getName()+"'s exp to "+DoubleArgumentType.getDouble(ctx,"expAmount")+" in system "+levelSystem)
                                                        .color(TextColor.color(Color.GREEN.asRGB())));

                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).executes(ctx -> {
                                                // sl set level [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.set")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                Player p = Bukkit.getPlayer(StringArgumentType.getString(ctx, "player"));
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                                // Command Logic
                                                setPlayerLevel(p,IntegerArgumentType.getInteger(ctx,"levelAmount"),levelSystem);
                                                sender.sendMessage(
                                                        Component.text("Successfully set "+p.getName()+"'s level to "+IntegerArgumentType.getInteger(ctx,"levelAmount")+" in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .then(Commands.literal("add")
                    .then(Commands.literal("exp")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).executes(ctx -> {
                                                // sl add exp [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.add")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                Player p = Bukkit.getPlayer(StringArgumentType.getString(ctx, "player"));
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                                // Command Logic
                                                addPlayerExp(p,DoubleArgumentType.getDouble(ctx,"expAmount"),levelSystem);
                                                sender.sendMessage(
                                                        Component.text("Successfully gave "+p.getName()+" "+DoubleArgumentType.getDouble(ctx,"expAmount")+" exp in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).executes(ctx -> {
                                                // sl add level [player] [amount] [system]
                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.add")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                Player p = Bukkit.getPlayer(StringArgumentType.getString(ctx, "player"));
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                                // Command Logic
                                                addPlayerLevel(p,IntegerArgumentType.getInteger(ctx,"levelAmount"),levelSystem);
                                                sender.sendMessage(
                                                        Component.text("Successfully gave "+p.getName()+" "+IntegerArgumentType.getInteger(ctx,"levelAmount")+" levels in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .then(Commands.literal("remove")
                    .then(Commands.literal("exp")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("expAmount", DoubleArgumentType.doubleArg())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).executes(ctx -> {
                                                // sl remove exp [player] [amount] [system]

                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.remove")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                Player p = Bukkit.getPlayer(StringArgumentType.getString(ctx, "player"));
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                                // Command Logic
                                                removePlayerExp(p,DoubleArgumentType.getDouble(ctx,"expAmount"),levelSystem);
                                                sender.sendMessage(
                                                        Component.text("Successfully removed "+DoubleArgumentType.getDouble(ctx,"expAmount")+" exp from "+p.getName()+" in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));
                                                return Command.SINGLE_SUCCESS;
                                            })))))
                    .then(Commands.literal("level")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .then(Commands.argument("levelAmount", IntegerArgumentType.integer())
                                            .then(Commands.argument("levelSystem", StringArgumentType.word()).executes(ctx -> {
                                                // sl remove level [player] [amount] [system]

                                                CommandSender sender = ctx.getSource().getSender();
                                                if (!sender.hasPermission("serverlevels.commands.remove")) {
                                                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                Player p = Bukkit.getPlayer(StringArgumentType.getString(ctx, "player"));
                                                String levelSystem = StringArgumentType.getString(ctx, "levelSystem");

                                                checkValidCommand(ctx,p,plugin.getLevelSystemRegistry().getLevelSystem(levelSystem));

                                                // Command Logic
                                                removePlayerLevel(p,IntegerArgumentType.getInteger(ctx,"levelAmount"),levelSystem);
                                                sender.sendMessage(
                                                        Component.text("Successfully removed "+IntegerArgumentType.getInteger(ctx,"levelAmount")+" levels from "+p.getName()+" in system "+levelSystem)
                                                                .color(TextColor.color(Color.GREEN.asRGB())));

                                                return Command.SINGLE_SUCCESS;
                                            }))))))
            .build();

    public LiteralCommandNode<CommandSourceStack> reloadCommand = Commands.literal("slreload").executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                if (!sender.hasPermission("serverlevels.commands.reload")) {
                    sender.sendMessage(Component.text("You do not have permission to use this command!").color(TextColor.color(Color.RED.asRGB())));
                    return Command.SINGLE_SUCCESS;
                }
                sender.sendMessage(Component.text("Reloading Server Levels, server may lag").color(TextColor.color(Color.GREEN.asRGB())));

                plugin.reloadConfig();
                plugin.loadConfigs();

                plugin.getDatabaseManager().setupDatabase();

                plugin.getLevelSystemRegistry().clearRegistry();
                plugin.registerLevelSystems();
                sender.sendMessage(Component.text("Server Levels Reloaded").color(TextColor.color(Color.GREEN.asRGB())));
                return Command.SINGLE_SUCCESS;
            }).build();

    private void setPlayerExp(Player p, double newExp, String levelSystem) {
        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p, levelSystem);
        lp.setExp(newExp);
        lp.adjustLevel();
        plugin.getDatabaseManager().saveLeveledPlayer(lp);
    }

    private void setPlayerLevel(Player p, int newLevel, String levelSystem) {
        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p, levelSystem);
        lp.setLevel(newLevel);
        lp.adjustExp();
        plugin.getDatabaseManager().saveLeveledPlayer(lp);
    }

    private void addPlayerExp(Player p, double addedExp, String levelSystem) {
        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p, levelSystem);

        lp.incrementExp(addedExp);
        lp.adjustLevel();
        plugin.getDatabaseManager().saveLeveledPlayer(lp);
    }

    private void addPlayerLevel(Player p, int addedLevels, String levelSystem) {
        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p, levelSystem);

        int newLevel = lp.getLevel() + addedLevels;

        lp.setLevel(newLevel);
        lp.adjustExp();
        plugin.getDatabaseManager().saveLeveledPlayer(lp);
    }

    private void removePlayerExp(Player p, double removedExp, String levelSystem) {
        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p, levelSystem);

        lp.incrementExp(-removedExp);
        lp.adjustLevel();
        plugin.getDatabaseManager().saveLeveledPlayer(lp);
    }

    private void removePlayerLevel(Player p, int removedLevels, String levelSystem) {
        LeveledPlayer lp = plugin.getDatabaseManager().getLeveledPlayer(p, levelSystem);

        int newLevel = lp.getLevel() - removedLevels;

        lp.setLevel(newLevel);
        lp.adjustExp();
        plugin.getDatabaseManager().saveLeveledPlayer(lp);
    }

    private boolean checkValidCommand(CommandContext<CommandSourceStack> ctx,Player p, LevelSystem ls) {
        if (p == null || !p.hasPlayedBefore()) {
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
