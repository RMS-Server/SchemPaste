package net.rms.schempaste.core;

import net.minecraft.nbt.NbtCompound;
import net.rms.schempaste.io.NbtReaders;
import net.rms.schempaste.schematic.MetadataOnly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SchematicIndex {
    private final Path syncmaticsDir;
    private final Map<String, Path> idToPath = new HashMap<>();
    private final Map<String, String> nameToId = new HashMap<>();
    private final Set<String> duplicateNames = new HashSet<>();
    private final Map<String, String> idToName = new HashMap<>();

    public SchematicIndex(Path syncmaticsDir) {
        this.syncmaticsDir = syncmaticsDir;
    }

    private static String stripExt(String s) {
        int i = s.lastIndexOf('.');
        return i >= 0 ? s.substring(0, i) : s;
    }

    public void scan() throws IOException {
        idToPath.clear();
        nameToId.clear();
        duplicateNames.clear();
        idToName.clear();

        if (!Files.isDirectory(syncmaticsDir)) {
            return;
        }

        try (var stream = Files.list(syncmaticsDir)) {
            stream.filter(p -> {
                        String s = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return s.endsWith(".litematic");
                    })
                    .forEach(p -> {
                        String base = stripExt(p.getFileName().toString());
                        idToPath.put(base, p);
                        try {
                            NbtCompound root = NbtReaders.readCompressedAuto(p);
                            String name = MetadataOnly.fromRoot(root).name;
                            if (name != null && !name.isEmpty()) {
                                String prev = nameToId.putIfAbsent(name, base);
                                if (prev != null && !Objects.equals(prev, base)) {
                                    duplicateNames.add(name);
                                }
                                idToName.put(base, name);
                            } else {
                                idToName.put(base, "");
                            }
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    public Optional<String> findIdByName(String name) {
        if (duplicateNames.contains(name)) {
            throw new IllegalStateException("Duplicate schematic metadata.name detected: " + name);
        }
        return Optional.ofNullable(nameToId.get(name));
    }

    public Optional<Path> pathById(String id) {
        return Optional.ofNullable(idToPath.get(id));
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Path> e : idToPath.entrySet()) {
            String id = e.getKey();
            String name = idToName.getOrDefault(id, "");
            out.add(new Entry(id, name, e.getValue()));
        }
        out.sort(Comparator.comparing((Entry en) -> en.name == null ? "" : en.name)
                .thenComparing(en -> en.id));
        return out;
    }

    public static class Entry {
        public final String id;
        public final String name;
        public final Path path;

        public Entry(String id, String name, Path path) {
            this.id = id;
            this.name = name;
            this.path = path;
        }
    }
}
