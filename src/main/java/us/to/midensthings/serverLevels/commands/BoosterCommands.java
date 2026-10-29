package us.to.midensthings.serverLevels.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import us.to.midensthings.serverLevels.ServerLevels;
import us.to.midensthings.serverLevels.data.BoosterManager;
import us.to.midensthings.serverLevels.util.DurationParser;
import us.to.midensthings.serverLevels.util.Messages;

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
                            Messages.send(sender, "invalid-system");
                            return Command.SINGLE_SUCCESS;
                        }
                        if (multiplier <= 0) {
                            Messages.send(sender, "booster-invalid-multiplier");
                            return Command.SINGLE_SUCCESS;
                        }
                        Duration duration = DurationParser.parse(durationInput);
                        if (duration == null || duration.toSeconds() < 1) {
                            Messages.send(sender, "booster-invalid-duration");
                            return Command.SINGLE_SUCCESS;
                        }

                        BoosterManager.AddedBooster added = plugin.getBoosterManager().addBooster(levelSystem, multiplier, duration);
                        Object[] placeholders = {"multiplier", DOUBLE_FORMAT.format(multiplier), "system", levelSystem,
                                "duration", DurationParser.format(duration), "starts_in", DurationParser.format(added.startsIn()),
                                "count", added.queuePosition()};
                        if (added.queuePosition() == 0) {
                            Messages.send(sender, "booster-started", placeholders);
                        } else {
                            Messages.send(sender, added.queuePosition() == 1 ? "booster-queued-one" : "booster-queued", placeholders);
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
            Messages.send(sender, "invalid-system");
            return;
        }

        List<BoosterManager.BoosterInfo> boosters = plugin.getBoosterManager().getBoosters(levelSystem);
        if (boosters.isEmpty()) {
            Messages.send(sender, "boosters-none", "system", levelSystem);
            return;
        }

        Messages.send(sender, "boosters-list-header", "system", levelSystem);
        for (int i = 0; i < boosters.size(); i++) {
            BoosterManager.BoosterInfo booster = boosters.get(i);
            String remaining = DurationParser.format(booster.remaining());
            Messages.send(sender, booster.active() ? "boosters-list-active" : "boosters-list-queued", "position", i + 1, "multiplier", DOUBLE_FORMAT.format(booster.multiplier()),
                    "system", levelSystem, "remaining", remaining, "duration", remaining);
        }
        Messages.send(sender, "boosters-list-footer", "system", levelSystem);
    }

    private static void cancelBooster(CommandContext<CommandSourceStack> ctx, String levelSystem, int position) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getLevelSystemRegistry().getLevelSystem(levelSystem) == null) {
            Messages.send(sender, "invalid-system");
            return;
        }

        // Positions start at 1 to match /sl booster list
        BoosterManager.BoosterInfo cancelled = plugin.getBoosterManager().cancelBooster(levelSystem, position - 1);
        if (cancelled == null) {
            Messages.send(sender, "booster-not-found", "position", position, "system", levelSystem);
            return;
        }
        boolean nextStarted = cancelled.active() && !plugin.getBoosterManager().getBoosters(levelSystem).isEmpty();
        Messages.send(sender, nextStarted ? "booster-cancelled-next-started" : "booster-cancelled",
                "multiplier", DOUBLE_FORMAT.format(cancelled.multiplier()), "system", levelSystem);
    }

    private static void cancelAllBoosters(CommandContext<CommandSourceStack> ctx, String levelSystem) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getLevelSystemRegistry().getLevelSystem(levelSystem) == null) {
            Messages.send(sender, "invalid-system");
            return;
        }

        int cancelled = plugin.getBoosterManager().cancelAll(levelSystem);
        if (cancelled == 0) {
            Messages.send(sender, "boosters-none", "system", levelSystem);
            return;
        }
        Messages.send(sender, cancelled == 1 ? "boosters-cancelled-one" : "boosters-cancelled", "count", cancelled, "system", levelSystem);
    }
}
