package net.rms.schempaste.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SchemPasteConfig {
    public static final String FILE_NAME = "schempaste.json";


    public int backgroundThreads = 2;
    public int mainThreadBudgetMs = 40;


    public String defaultReplace = "with_non_air";

    public String defaultLayerAxis = "y";
    public String defaultLayerMode = "all";
    public int defaultLayerSingle = 0;
    public int defaultLayerAbove = 0;
    public int defaultLayerBelow = 0;
    public int defaultLayerRangeMin = 0;
    public int defaultLayerRangeMax = 0;


    public boolean enableProgressMessages = true;
    public long progressUpdateIntervalMs = 2000;


    public boolean enableDynamicChunkLoading = true;
    public int maxLoadedChunks = 32;


    public boolean fixChestMirror = true;
    public boolean clearInventories = false;
    public boolean suppressNeighborUpdates = true;

    public static SchemPasteConfig load(Path configDir) throws IOException {
        Path path = configDir.resolve(FILE_NAME);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(path)) {
            SchemPasteConfig cfg = new SchemPasteConfig();
            try (Writer w = Files.newBufferedWriter(path)) {
                gson.toJson(cfg, w);
            }
            return cfg;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            SchemPasteConfig cfg = gson.fromJson(r, SchemPasteConfig.class);
            return cfg != null ? cfg : new SchemPasteConfig();
        }
    }
}
