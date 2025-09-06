package net.rms.schempaste.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Util;
import net.rms.schempaste.SchemPaste;
import net.rms.schempaste.config.PlacementConfig;
import net.rms.schempaste.core.SchematicIndex;
import net.rms.schempaste.litematica.LitematicFile;
import net.rms.schempaste.paste.PasteEngine;
import net.rms.schempaste.paste.ReplaceBehavior;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class PasteCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                java.nio.file.Path configDir,
                                SchematicIndex index) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("sp")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("paste")
                        .then(CommandManager.literal("stop")
                                .executes(PasteCommand::executeStopAll))
                        .then(CommandManager.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeByIndex(ctx, configDir, index, null))
                                .then(CommandManager.literal("addition")
                                        .then(CommandManager.argument("params", StringArgumentType.greedyString())
                                                .executes(ctx -> executeByIndex(ctx, configDir, index, StringArgumentType.getString(ctx, "params")))))));

        dispatcher.register(root);
    }


    private static int executeByIndex(CommandContext<ServerCommandSource> ctx,
                                      java.nio.file.Path configDir,
                                      SchematicIndex index,
                                      String params) {

        PlacementConfig config;
        try {
            config = PlacementConfig.load(configDir);
        } catch (IOException e) {
            error(ctx.getSource(), "Failed to load placements.json: " + e.getMessage());
            return 0;
        }

        int idx = ctx.getArgument("index", Integer.class);

        java.util.List<PlacementConfig.Placement> items = new java.util.ArrayList<>();
        for (PlacementConfig.Placement p : config.placements) {
            if (p != null && p.fileName != null && !p.fileName.isEmpty()) {
                items.add(p);
            }
        }

        if (idx < 1 || idx > items.size()) {
            error(ctx.getSource(), "Index out of range: " + idx + " (1-" + items.size() + ")");
            return 0;
        }

        PlacementConfig.Placement placement = items.get(idx - 1);
        String name = placement.fileName;

        try {
            index.scan();
        } catch (IOException e) {
            error(ctx.getSource(), "Failed to scan syncmatics: " + e.getMessage());
            return 0;
        }

        Optional<Path> pathOpt = (placement.hash == null) ? Optional.empty() : index.pathById(placement.hash);
        if (pathOpt.isEmpty()) {
            error(ctx.getSource(), "Projection file not found for selected item");
            return 0;
        }

        feedback(ctx.getSource(), "Queuing paste for '" + name + "'", false);

        final String paramsStr = params;
        Util.getMainWorkerExecutor().execute(() -> {
            try {
                LitematicFile lf = LitematicFile.load(pathOpt.get());
                ServerCommandSource src = ctx.getSource();
                net.minecraft.server.MinecraftServer server = src.getServer();
                String dim = placement.origin.dimension;
                net.minecraft.server.world.ServerWorld world = dimToWorld(server, dim);
                if (world == null) {
                    error(src, "Unknown dimension: " + dim);
                    return;
                }
                SchemPaste.PasteEngineHolder.ensure(server, SchemPaste.INSTANCE);
                PasteEngine engine = SchemPaste.PasteEngineHolder.engine;
                ReplaceBehavior replaceOverride = null;
                int rateOverride = -1;
                if (paramsStr != null && !paramsStr.isEmpty()) {
                    Parsed p = parseParams(paramsStr);
                    replaceOverride = p.replace;
                    rateOverride = p.rate;
                }
                engine.enqueuePaste(lf, placement, world, replaceOverride, rateOverride);
                feedback(src, "Paste queued: " + name, true);
            } catch (Exception e) {
                SchemPaste.LOGGER.error("Paste failed", e);
                error(ctx.getSource(), "Paste failed: " + e.getMessage());
            }
        });

        return 1;
    }


    private static Parsed parseParams(String params) {
        Parsed out = new Parsed();
        String[] toks = params.trim().split("\\s+");
        for (int i = 0; i < toks.length; i++) {
            String t = toks[i];
            String lower = t.toLowerCase();
            if (lower.equals("replace")) {
                String val = (i + 1 < toks.length) ? toks[++i].toLowerCase() : null;
                if (val != null) {
                    out.replace = ReplaceBehavior.fromString(val);
                }
            } else if (lower.equals("rate")) {
                String val = (i + 1 < toks.length) ? toks[++i] : null;
                if (val != null) {
                    try {
                        out.rate = Math.max(1, Integer.parseInt(val));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return out;
    }

    private static int executeStopAll(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource src = ctx.getSource();
            net.minecraft.server.MinecraftServer server = src.getServer();
            SchemPaste.PasteEngineHolder.ensure(server, SchemPaste.INSTANCE);
            if (SchemPaste.PasteEngineHolder.engine == null) {
                error(src, "Paste engine is not initialized");
                return 0;
            }
            PasteEngine engine = SchemPaste.PasteEngineHolder.engine;
            int cancelled = engine.cancelAllJobs();
            if (cancelled > 0) {
                feedback(src, "Stopped all paste jobs: " + cancelled + " cancelled", true);
            } else {
                feedback(src, "No active paste jobs", false);
            }
            return 1;
        } catch (Exception e) {
            error(ctx.getSource(), "Failed to stop: " + e.getMessage());
            return 0;
        }
    }

    private static net.minecraft.server.world.ServerWorld dimToWorld(net.minecraft.server.MinecraftServer server, String dim) {
        if (dim == null) return server.getOverworld();
        switch (dim) {
            case "minecraft:the_nether":
                return server.getWorld(net.minecraft.world.World.NETHER);
            case "minecraft:the_end":
                return server.getWorld(net.minecraft.world.World.END);
            case "minecraft:overworld":
            default:
                return server.getWorld(net.minecraft.world.World.OVERWORLD);
        }
    }

    private static int showStatus(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerCommandSource src = ctx.getSource();
            net.minecraft.server.MinecraftServer server = src.getServer();

            if (SchemPaste.PasteEngineHolder.engine == null) {
                feedback(src, "Paste engine is not initialized", false);
                return 1;
            }

            PasteEngine engine = SchemPaste.PasteEngineHolder.engine;
            String status = engine.getChunkManagerStatus();

            feedback(src, "=== SchemPaste Status ===", false);
            feedback(src, status, false);
            feedback(src, "Active jobs: " + engine.getActiveJobCount(), false);

        } catch (Exception e) {
            error(ctx.getSource(), "Failed to get status: " + e.getMessage());
            return 0;
        }

        return 1;
    }

    private static void feedback(ServerCommandSource src, String msg, boolean broadcast) {
        //#if MC < 12000
        src.sendFeedback(new net.minecraft.text.LiteralText(msg), broadcast);
        //#else
        //$$ src.sendFeedback(() -> net.minecraft.text.Text.literal(msg), broadcast);
        //#endif
    }

    private static void error(ServerCommandSource src, String msg) {
        //#if MC < 12000
        src.sendError(new net.minecraft.text.LiteralText(msg));
        //#else
        //$$ src.sendError(net.minecraft.text.Text.literal(msg));
        //#endif
    }

    private static class Parsed {
        ReplaceBehavior replace;
        int rate = -1;
    }
}
