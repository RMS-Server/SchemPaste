package net.rms.schempaste.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction.Axis;

public class LayerRange {
    private final LayerMode mode;
    private final Axis axis;
    private final int layerSingle;
    private final int layerAbove;
    private final int layerBelow;
    private final int layerRangeMin;
    private final int layerRangeMax;

    public LayerRange(LayerMode mode, Axis axis,
                      int single, int above, int below, int min, int max) {
        this.mode = mode == null ? LayerMode.ALL : mode;
        this.axis = axis == null ? Axis.Y : axis;
        this.layerSingle = single;
        this.layerAbove = above;
        this.layerBelow = below;
        if (min <= max) {
            this.layerRangeMin = min;
            this.layerRangeMax = max;
        } else {
            this.layerRangeMin = max;
            this.layerRangeMax = min;
        }
    }

    public boolean isPositionWithinRange(BlockPos pos) {
        return isPositionWithinRange(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isPositionWithinRange(int x, int y, int z) {
        switch (mode) {
            case ALL:
                return true;
            case SINGLE_LAYER:
                return valueForAxis(x, y, z) == layerSingle;
            case ALL_ABOVE:
                return valueForAxis(x, y, z) >= layerAbove;
            case ALL_BELOW:
                return valueForAxis(x, y, z) <= layerBelow;
            case LAYER_RANGE:
            default:
                int v = valueForAxis(x, y, z);
                return v >= layerRangeMin && v <= layerRangeMax;
        }
    }

    private int valueForAxis(int x, int y, int z) {
        switch (axis) {
            case X:
                return x;
            case Y:
                return y;
            case Z:
                return z;
            default:
                return y;
        }
    }

    public boolean isValid() {
        if (mode == LayerMode.LAYER_RANGE) return layerRangeMin <= layerRangeMax;
        return true;
    }

    public LayerMode getMode() {
        return mode;
    }

    public Axis getAxis() {
        return axis;
    }

    public int getLayerSingle() {
        return layerSingle;
    }

    public int getLayerAbove() {
        return layerAbove;
    }

    public int getLayerBelow() {
        return layerBelow;
    }

    public int getLayerRangeMin() {
        return layerRangeMin;
    }

    public int getLayerRangeMax() {
        return layerRangeMax;
    }

    public enum LayerMode {ALL, SINGLE_LAYER, ALL_ABOVE, ALL_BELOW, LAYER_RANGE}
}
