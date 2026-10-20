package us.to.midensthings.serverLevels.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.command.CommandSender;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.BoosterManager;
import us.to.midensthings.serverLevels.util.DurationParser;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.List;

/***
 * The booster command tree, shared by /sl booster and /ls booster
 */
public final class BoosterCommands {

    private static final ServerLevels plugin = ServerLevels.getPlugin(ServerLevels.class);

    // Formats booster multipliers, e.g. 2 instead of 2.0. Commands run on the main thread, so sharing one is safe
    private static final DecimalFormat DOUBLE_FORMAT = new DecimalFormat("0.##");

    private BoosterCommands() {}

    /***
     * Builds a new booster command node: booster [system] add [multiplier] [duration], list, and cancel [position|all].
     * Builds a fresh node on every call, since a node can only be added to one command tree.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> boosterCommand() {
        return Commands.literal("booster").requires(source -> source.getSender().hasPermission("serverlevels.commands.booster"))
                .then(Commands.argument("levelSystem", StringArgumentType.word()).suggests((ctx,builder) -> {
                    plugin.getLevelSystemRegistry().getAllSystems().forEach(levelSystem -> {
                        builder.suggest(levelSystem.getSystemName());
                    });
                    return builder.buildFuture();
                    }).then(Commands.literal("add").then(Commands.argument("multiplier",DoubleArgumentType.doubleArg()).then(Commands.argument("duration",StringArgumentType.word()).executes(ctx -> {
                        // booster [system] add [multiplier] [duration]
                        CommandSender sender = ctx.getSource().getSender();
                        String levelSystem = StringArgumentType.getString(ctx, "levelSystem");
                        double multiplier = DoubleArgumentType.getDouble(ctx, "multiplier");
                        String durationInput = StringArgumentType.getString(ctx, "duration");

                        if (plugin.getLevelSystemRegistry().getLevelSystem(levelSystem) == null) {
                            sender.sendMessage(Component.text("Invalid Level System!").color(TextColor.color(Color.RED.asRGB())));
                            return Command.SINGLE_SUCCESS;
                        }
                        if (multiplier <= 0) {
                            sender.sendMessage(Component.text("The multiplier must be greater than 0!").color(TextColor.color(Color.RED.asRGB())));
                            return Command.SINGLE_SUCCESS;
                        }
                        Duration duration = DurationParser.parse(durationInput);
                        if (duration == null || duration.toSeconds() < 1) {
                            sender.sendMessage(Component.text("Invalid duration! Combine d, h, m and s, e.g. 1h30m or 45s").color(TextColor.color(Color.RED.asRGB())));
                            return Command.SINGLE_SUCCESS;
                        }

                        BoosterManager.AddedBooster added = plugin.getBoosterManager().addBooster(levelSystem, multiplier, duration);
                        String booster = DOUBLE_FORMAT.format(multiplier) + "x exp booster for " + levelSystem + " lasting " + DurationParser.format(duration);
                        if (added.queuePosition() == 0) {
                            sender.sendMessage(Component.text("Started a " + booster).color(TextColor.color(Color.GREEN.asRGB())));
                        } else {
                            sender.sendMessage(Component.text("Queued a " + booster + ". It starts in " + DurationParser.format(added.startsIn())
                                    + ", after " + added.queuePosition() + " other booster" + (added.queuePosition() == 1 ? "" : "s"))
                                    .color(TextColor.color(Color.GREEN.asRGB())));
                        }
                        return Command.SINGLE_SUCCESS;
                        }))))
                        // booster [system] list
                        .then(Commands.literal("list").executes(ctx -> {
                            listBoosters(ctx, StringArgumentType.getString(ctx, "levelSystem"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        // booster [system] cancel [position|all]
                        .then(Commands.literal("cancel")
                                .then(Commands.literal("all").executes(ctx -> {
                                    cancelAllBoosters(ctx, StringArgumentType.getString(ctx, "levelSystem"));
                                    return Command.SINGLE_SUCCESS;
                                }))
                                .then(Commands.argument("position", IntegerArgumentType.integer(1)).executes(ctx -> {
                                    cancelBooster(ctx, StringArgumentType.getString(ctx, "levelSystem"), IntegerArgumentType.getInteger(ctx, "position"));
                                    return Command.SINGLE_SUCCESS;
                                })))

                );
    }

    private static void listBoosters(CommandContext<CommandSourceStack> ctx, String levelSystem) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getLevelSystemRegistry().getLevelSystem(levelSystem) == null) {
            sender.sendMessage(Component.text("Invalid Level System!").color(TextColor.color(Color.RED.asRGB())));
            return;
        }

        List<BoosterManager.BoosterInfo> boosters = plugin.getBoosterManager().getBoosters(levelSystem);
        if (boosters.isEmpty()) {
            sender.sendMessage(Component.text("There are no exp boosters for " + levelSystem).color(TextColor.color(Color.GRAY.asRGB())));
            return;
        }

        sender.sendMessage(Component.text("Exp boosters for " + levelSystem + ":").color(TextColor.color(Color.GREEN.asRGB())));
        for (int i = 0; i < boosters.size(); i++) {
            BoosterManager.BoosterInfo booster = boosters.get(i);
            String line = (i + 1) + ". " + DOUBLE_FORMAT.format(booster.multiplier()) + "x, "
                    + (booster.active() ? DurationParser.format(booster.remaining()) + " left (active)" : "lasts " + DurationParser.format(booster.remaining()));
            sender.sendMessage(Component.text(line).color(TextColor.color((booster.active() ? Color.YELLOW : Color.WHITE).asRGB())));
        }
        sender.sendMessage(Component.text("Use /sl booster " + levelSystem + " cancel <position|all> to cancel boosters")
                .color(TextColor.color(Color.GRAY.asRGB())));
    }

    private static void cancelBooster(CommandContext<CommandSourceStack> ctx, String levelSystem, int position) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getLevelSystemRegistry().getLevelSystem(levelSystem) == null) {
            sender.sendMessage(Component.text("Invalid Level System!").color(TextColor.color(Color.RED.asRGB())));
            return;
        }

        // Positions start at 1 to match /sl booster list
        BoosterManager.BoosterInfo cancelled = plugin.getBoosterManager().cancelBooster(levelSystem, position - 1);
        if (cancelled == null) {
            sender.sendMessage(Component.text("There is no booster at position " + position + "! Use /sl booster " + levelSystem + " list to see the boosters")
                    .color(TextColor.color(Color.RED.asRGB())));
            return;
        }
        String message = "Cancelled the " + DOUBLE_FORMAT.format(cancelled.multiplier()) + "x exp booster for " + levelSystem;
        if (cancelled.active()) {
            List<BoosterManager.BoosterInfo> remaining = plugin.getBoosterManager().getBoosters(levelSystem);
            message += remaining.isEmpty() ? "" : ". The next booster has started";
        }
        sender.sendMessage(Component.text(message).color(TextColor.color(Color.GREEN.asRGB())));
    }

    private static void cancelAllBoosters(CommandContext<CommandSourceStack> ctx, String levelSystem) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getLevelSystemRegistry().getLevelSystem(levelSystem) == null) {
            sender.sendMessage(Component.text("Invalid Level System!").color(TextColor.color(Color.RED.asRGB())));
            return;
        }

        int cancelled = plugin.getBoosterManager().cancelAll(levelSystem);
        if (cancelled == 0) {
            sender.sendMessage(Component.text("There are no exp boosters for " + levelSystem).color(TextColor.color(Color.GRAY.asRGB())));
            return;
        }
        sender.sendMessage(Component.text("Cancelled " + cancelled + " exp booster" + (cancelled == 1 ? "" : "s") + " for " + levelSystem)
                .color(TextColor.color(Color.GREEN.asRGB())));
    }
}
