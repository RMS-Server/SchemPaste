package net.rms.schempaste.litematica;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.rms.schempaste.SchemPaste;
import net.rms.schempaste.io.NbtReaders;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class LitematicFile {
    public final String name;
    public final Map<String, Region> regions;

    public LitematicFile(String name, Map<String, Region> regions) {
        this.name = name;
        this.regions = regions;
    }

    public static LitematicFile load(Path path) throws IOException {
        // SchemPaste.LOGGER.info("Loading litematica file: {}", path.toString());
        NbtCompound root = NbtReaders.readCompressedAuto(path);
        // SchemPaste.LOGGER.debug("Root NBT keys: {}", root.getKeys());
        String name = Optional.ofNullable(root.getCompound("Metadata")).map(t -> t.getString("Name")).orElse("");
        // SchemPaste.LOGGER.info("Litematica name: '{}'", name);

        Map<String, Region> regions = new LinkedHashMap<>();
        NbtCompound regionsTag = root.getCompound("Regions");
        // SchemPaste.LOGGER.info("Regions NBT compound: {}", regionsTag != null ? "found" : "null");
        if (regionsTag != null) {
            // SchemPaste.LOGGER.info("Region keys: {}", regionsTag.getKeys());
            for (String regionName : regionsTag.getKeys()) {
                // SchemPaste.LOGGER.info("Processing region: {}", regionName);
                NbtCompound r = regionsTag.getCompound(regionName);
                // SchemPaste.LOGGER.debug("Region '{}' NBT keys: {}", regionName, r.getKeys());


                NbtCompound sizeCompound = r.getCompound("Size");
                NbtCompound posCompound = r.getCompound("Position");

                if (sizeCompound == null || posCompound == null) {
                    SchemPaste.LOGGER.warn("Region '{}' missing Size or Position compound", regionName);
                    continue;
                }

                Vec3i size = new Vec3i(
                        sizeCompound.getInt("x"),
                        sizeCompound.getInt("y"),
                        sizeCompound.getInt("z")
                );
                BlockPos relPos = new BlockPos(
                        posCompound.getInt("x"),
                        posCompound.getInt("y"),
                        posCompound.getInt("z")
                );
                // SchemPaste.LOGGER.info("Region '{}' size: {}x{}x{}, relPos: {}", regionName, size.getX(), size.getY(), size.getZ(), relPos);

                NbtList paletteTag = r.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
                long[] states = r.getLongArray("BlockStates");
                if (paletteTag == null || states == null) {
                    SchemPaste.LOGGER.warn("Region '{}' missing palette or states data", regionName);
                    continue;
                }
                // SchemPaste.LOGGER.info("Region '{}' palette size: {}, states length: {}", regionName, paletteTag.size(), states.length);
                List<BlockState> palette = BlockStatePaletteUtil.decode(paletteTag);
                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
                PackedBitArray storage = new PackedBitArray(bits, states);

                Map<BlockPos, NbtCompound> tiles = new HashMap<>();
                NbtList tilesTag = r.getList("TileEntities", NbtElement.COMPOUND_TYPE);
                if (tilesTag != null) {
                    for (int i = 0; i < tilesTag.size(); i++) {
                        NbtCompound t = tilesTag.getCompound(i);
                        BlockPos p = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
                        tiles.put(p, t);
                    }
                }


                List<NbtCompound> entities = new ArrayList<>();
                NbtList entTag = r.getList("Entities", NbtElement.COMPOUND_TYPE);
                if (entTag != null) {
                    for (int i = 0; i < entTag.size(); i++) {
                        entities.add(entTag.getCompound(i));
                    }
                }


                List<TickEntry> blockTicks = new ArrayList<>();
                NbtList bt = r.getList("PendingBlockTicks", NbtElement.COMPOUND_TYPE);
                if (bt != null) {
                    for (int i = 0; i < bt.size(); i++) {
                        NbtCompound t = bt.getCompound(i);
                        blockTicks.add(TickEntry.from(t, true));
                    }
                }
                List<TickEntry> fluidTicks = new ArrayList<>();
                NbtList ft = r.getList("PendingFluidTicks", NbtElement.COMPOUND_TYPE);
                if (ft != null) {
                    for (int i = 0; i < ft.size(); i++) {
                        NbtCompound t = ft.getCompound(i);
                        fluidTicks.add(TickEntry.from(t, false));
                    }
                }

                regions.put(regionName, new Region(regionName, size, relPos, palette, storage, tiles, entities, blockTicks, fluidTicks));
                // SchemPaste.LOGGER.info("Successfully loaded region: {}", regionName);
            }
        }
        // SchemPaste.LOGGER.info("Loaded {} regions total", regions.size());
        return new LitematicFile(name, regions);
    }

    public static class Region {
        public final String name;
        public final Vec3i size;
        public final BlockPos relativePos;
        public final List<BlockState> palette;
        public final PackedBitArray storage;
        public final Map<BlockPos, NbtCompound> tileEntities;
        public final List<NbtCompound> entities;
        public final List<TickEntry> blockTicks;
        public final List<TickEntry> fluidTicks;

        public Region(String name, Vec3i size, BlockPos relativePos, List<BlockState> palette, PackedBitArray storage, Map<BlockPos, NbtCompound> tileEntities, List<NbtCompound> entities, List<TickEntry> blockTicks, List<TickEntry> fluidTicks) {
            this.name = name;
            this.size = size;
            this.relativePos = relativePos;
            this.palette = palette;
            this.storage = storage;
            this.tileEntities = tileEntities;
            this.entities = entities;
            this.blockTicks = blockTicks;
            this.fluidTicks = fluidTicks;
        }

        public int volume() {
            return Math.abs(size.getX()) * Math.abs(size.getY()) * Math.abs(size.getZ());
        }

        public BlockState stateAt(int x, int y, int z) {

            int absX = Math.abs(size.getX());
            int absY = Math.abs(size.getY());
            int absZ = Math.abs(size.getZ());

            int index = (y * absX * absZ) + (z * absX) + x;
            int id = storage.get(index);
            if (id < 0 || id >= palette.size()) return palette.get(0);
            return palette.get(id);
        }
    }

    public static class TickEntry {
        public final int x, y, z;
        public final String id;
        public final int delay;
        public final boolean isBlock;

        public TickEntry(int x, int y, int z, String id, int delay, boolean isBlock) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.delay = delay;
            this.isBlock = isBlock;
        }

        public static TickEntry from(NbtCompound t, boolean isBlock) {
            int x = t.getInt("x");
            int y = t.getInt("y");
            int z = t.getInt("z");
            String key = t.getString(isBlock ? "Block" : "Fluid");
            int delay = t.contains("Delay") ? t.getInt("Delay") : 1;
            if (delay <= 0) delay = 1;
            return new TickEntry(x, y, z, key, delay, isBlock);
        }
    }
}
