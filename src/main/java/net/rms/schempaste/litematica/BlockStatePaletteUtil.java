package net.rms.schempaste.litematica;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public final class BlockStatePaletteUtil {
    private BlockStatePaletteUtil() {
    }

    public static List<BlockState> decode(NbtList paletteTag) {
        int size = paletteTag.size();
        List<BlockState> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            NbtCompound tag = paletteTag.getCompound(i);
            String name = tag.getString("Name");
            //#if MC < 12000
            Block block = net.minecraft.util.registry.Registry.BLOCK.get(new Identifier(name));
            //#else
            //$$ Block block = net.minecraft.registry.Registries.BLOCK.get(new Identifier(name));
            //#endif
            BlockState state = block.getDefaultState();
            NbtCompound props = tag.getCompound("Properties");
            if (props != null) {
                StateManager<Block, BlockState> sm = block.getStateManager();
                for (String key : props.getKeys()) {
                    Property<?> prop = sm.getProperty(key);
                    if (prop == null) continue;
                    String valueStr = props.getString(key);
                    state = with(state, prop, valueStr);
                }
            }
            list.add(state);
        }
        return list;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> prop, String value) {
        return prop.parse(value).map(v -> state.with(prop, v)).orElse(state);
    }
}

