package net.rms.schempaste.util;

import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;

import java.util.HashSet;
import java.util.Set;

public class ChunkBoundsUtils {


    public static Set<ChunkPos> getIntersectingChunks(Vec3i regionSize, BlockPos origin, BlockRotation rotation, BlockMirror mirror) {
        Set<ChunkPos> chunks = new HashSet<>();


        BlockPos[] corners = getRegionCorners(regionSize, origin, rotation, mirror);


        BlockPos min = corners[0];
        BlockPos max = corners[0];

        for (BlockPos corner : corners) {
            min = PositionUtils.getMinCorner(min, corner);
            max = PositionUtils.getMaxCorner(max, corner);
        }


        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        return chunks;
    }


    public static ChunkIntersection getChunkIntersection(Vec3i regionSize, BlockPos origin, BlockRotation rotation, BlockMirror mirror, ChunkPos chunkPos) {

        BlockPos[] corners = getRegionCorners(regionSize, origin, rotation, mirror);

        BlockPos regionMin = corners[0];
        BlockPos regionMax = corners[0];

        for (BlockPos corner : corners) {
            regionMin = PositionUtils.getMinCorner(regionMin, corner);
            regionMax = PositionUtils.getMaxCorner(regionMax, corner);
        }


        int chunkMinX = chunkPos.x << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkPos.z << 4;
        int chunkMaxZ = chunkMinZ + 15;


        int intersectMinX = Math.max(regionMin.getX(), chunkMinX);
        int intersectMaxX = Math.min(regionMax.getX(), chunkMaxX);
        int intersectMinZ = Math.max(regionMin.getZ(), chunkMinZ);
        int intersectMaxZ = Math.min(regionMax.getZ(), chunkMaxZ);


        if (intersectMinX > intersectMaxX || intersectMinZ > intersectMaxZ) {
            return null;
        }

        return new ChunkIntersection(intersectMinX, intersectMaxX, regionMin.getY(), regionMax.getY(), intersectMinZ, intersectMaxZ);
    }

    private static BlockPos[] getRegionCorners(Vec3i regionSize, BlockPos origin, BlockRotation rotation, BlockMirror mirror) {
        Vec3i absSize = new Vec3i(Math.abs(regionSize.getX()), Math.abs(regionSize.getY()), Math.abs(regionSize.getZ()));

        BlockPos[] corners = new BlockPos[8];
        corners[0] = new BlockPos(0, 0, 0);
        corners[1] = new BlockPos(absSize.getX() - 1, 0, 0);
        corners[2] = new BlockPos(0, 0, absSize.getZ() - 1);
        corners[3] = new BlockPos(absSize.getX() - 1, 0, absSize.getZ() - 1);
        corners[4] = new BlockPos(0, absSize.getY() - 1, 0);
        corners[5] = new BlockPos(absSize.getX() - 1, absSize.getY() - 1, 0);
        corners[6] = new BlockPos(0, absSize.getY() - 1, absSize.getZ() - 1);
        corners[7] = new BlockPos(absSize.getX() - 1, absSize.getY() - 1, absSize.getZ() - 1);


        for (int i = 0; i < corners.length; i++) {
            corners[i] = PositionUtils.getTransformedBlockPos(corners[i], mirror, rotation).add(origin);
        }

        return corners;
    }

    public static class ChunkIntersection {
        public final int minX, maxX, minY, maxY, minZ, maxZ;

        public ChunkIntersection(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        public int getBlockCount() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }
}
