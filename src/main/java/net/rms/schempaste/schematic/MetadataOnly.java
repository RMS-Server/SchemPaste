package net.rms.schempaste.schematic;

import net.minecraft.nbt.NbtCompound;

public final class MetadataOnly {
    public final String name;

    public MetadataOnly(String name) {
        this.name = name;
    }

    public static MetadataOnly fromRoot(NbtCompound root) {
        NbtCompound meta = root.getCompound("Metadata");
        String name = meta != null ? meta.getString("Name") : "";
        return new MetadataOnly(name);
    }
}

