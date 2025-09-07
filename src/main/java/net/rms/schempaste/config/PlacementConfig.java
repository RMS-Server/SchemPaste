package net.rms.schempaste.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class PlacementConfig {
    public static final String FILE_NAME = "placements.json";
    
    @SerializedName("placements")
    public List<Placement> placements = new ArrayList<>();
    
    public static PlacementConfig load(Path configDir) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return new PlacementConfig();
        }
        try (Reader reader = Files.newBufferedReader(file); JsonReader jr = new JsonReader(reader)) {
            Gson gson = new GsonBuilder().create();
            return Objects.requireNonNullElse(gson.fromJson(jr, PlacementConfig.class), new PlacementConfig());
        }
    }
    
    public enum Rotation {NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90}
    
    public enum Mirror {NONE, LEFT_RIGHT, FRONT_BACK}
    
    public static class Placement {
        public String id;
        @SerializedName("file_name")
        public String fileName;
        public String hash;
        public Origin origin;
        public Rotation rotation = Rotation.NONE;
        public Mirror mirror = Mirror.NONE;
        public Owner owner;
        public Owner lastModifiedBy;
        @SerializedName("subregionData")
        public List<Subregion> subregions = new ArrayList<>();
    }
    
    public static class Origin {
        public int[] position;
        public String dimension;
    }
    
    public static class Owner {
        public UUID uuid;
        public String name;
    }
    
    public static class Subregion {
        public int[] position;
        public String name;
        public Rotation rotation = Rotation.NONE;
        public Mirror mirror = Mirror.NONE;
    }
}

