package net.rms.schempaste.litematica;

public class PackedBitArray {
    private final int bits;
    private final long[] data;

    public PackedBitArray(int bits, long[] data) {
        this.bits = bits;
        this.data = data;
    }

    public int get(int index) {
        int bitIndex = index * bits;
        int startLong = bitIndex >>> 6;
        int startOffset = bitIndex & 63;
        long mask = (1L << bits) - 1L;
        long value;
        if (startOffset + bits <= 64) {
            value = (data[startLong] >>> startOffset) & mask;
        } else {
            int low = 64 - startOffset;
            long part1 = data[startLong] >>> startOffset;
            long part2 = data[startLong + 1] & ((1L << (bits - low)) - 1);
            value = (part1 | (part2 << low)) & mask;
        }
        return (int) value;
    }
}

