package net.rms.schempaste;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.rms.schempaste.command.ListCommand;
import net.rms.schempaste.command.PasteCommand;
import net.rms.schempaste.config.SchemPasteConfig;
import net.rms.schempaste.core.SchematicIndex;
import net.rms.schempaste.paste.PasteEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class SchemPaste implements ModInitializer {
    public static final String MOD_ID = "schempaste";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final int TICK_WINDOW = 200;
    private static final double NS_PER_MS = 1_000_000.0;
    private static final double MS_PER_TICK_TARGET = 50.0;
    private static final long[] msptRing = new long[TICK_WINDOW];
    public static SchemPaste INSTANCE;
    private static int msptCount = 0;
    private static int msptIdx = 0;
    private static volatile long lastTickStartNs = 0L;
    private static volatile double lastTickMs = MS_PER_TICK_TARGET;

    public static double getAverageMspt() {
        int n = msptCount;
        if (n <= 0) return MS_PER_TICK_TARGET;
        long sum = 0L;
        for (int i = 0; i < n; i++) sum += msptRing[i];
        return sum / (double) n;
    }

    public static double getTps() {
        double mspt = getAverageMspt();
        if (mspt <= 0.0) return 20.0;
        double tps = 1000.0 / mspt;
        return Math.min(20.0, tps);
    }

    public static double getLastTickMs() {
        return lastTickMs;
    }

    @Override
    public void onInitialize() {
        INSTANCE = this;
        LOGGER.info("SchemPaste initializing");

        //#if MC<12000
        net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
        //#else
        //$$ net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
        //#endif

            Path runDir = java.nio.file.Paths.get("");
            Path configDir = runDir.resolve("config").resolve("syncmatica");
            Path syncmaticsDir = runDir.resolve("syncmatics");


            SchematicIndex index = new SchematicIndex(syncmaticsDir);
            PasteCommand.register(dispatcher, configDir, index);
            ListCommand.register(dispatcher, configDir, index);
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            lastTickStartNs = System.nanoTime();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getOverworld();
            PasteEngineHolder.ensure(server, this);
            if (PasteEngineHolder.engine != null) {
                PasteEngineHolder.engine.tick();
            } else {
                LOGGER.warn("PasteEngine is null during tick!");
            }


            long endNs = System.nanoTime();
            long startNs = lastTickStartNs;
            if (startNs > 0L && endNs > startNs) {
                long ms = Math.max(0L, Math.round((endNs - startNs) / NS_PER_MS));
                lastTickMs = (double) ms;
                msptRing[msptIdx] = ms;
                msptIdx = (msptIdx + 1) % TICK_WINDOW;
                if (msptCount < TICK_WINDOW) msptCount++;
            }
        });
    }

    public static class PasteEngineHolder {
        public static PasteEngine engine;
        public static SchemPasteConfig cfg;
        public static MinecraftServer lastServer;

        public static void ensure(MinecraftServer server, SchemPaste mod) {
            if (engine == null || lastServer != server) {
                LOGGER.info("Creating new advanced PasteEngine for server");
                Path runDir = java.nio.file.Paths.get("");
                Path configDir = runDir.resolve("config").resolve("syncmatica");
                try {
                    cfg = SchemPasteConfig.load(configDir);
                    LOGGER.info("Loaded SchemPasteConfig");
                } catch (Exception e) {
                    LOGGER.error("Failed to load SchemPasteConfig, using defaults", e);
                    cfg = new SchemPasteConfig();
                }
                engine = new PasteEngine(server, cfg);
                lastServer = server;
                LOGGER.info("Advanced PasteEngine created successfully");
            }
        }
    }
}
