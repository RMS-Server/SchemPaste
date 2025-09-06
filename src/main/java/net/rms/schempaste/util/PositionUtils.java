package net.rms.schempaste.util;

import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class PositionUtils {


    public static BlockPos getMinCorner(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
    }


    public static BlockPos getMaxCorner(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
    }


    public static Vec3i getRelativeEndPositionFromAreaSize(Vec3i areaSize) {
        return new Vec3i(
                areaSize.getX() - 1,
                areaSize.getY() - 1,
                areaSize.getZ() - 1
        );
    }


    public static BlockPos getTransformedBlockPos(BlockPos pos, BlockMirror mirror, BlockRotation rotation) {
        BlockPos transformed = pos;

        if (mirror != BlockMirror.NONE) {
            transformed = mirror(transformed, mirror);
        }

        if (rotation != BlockRotation.NONE) {
            transformed = rotate(transformed, rotation);
        }

        return transformed;
    }


    public static BlockPos getReverseTransformedBlockPos(BlockPos pos, BlockMirror mirror, BlockRotation rotation) {
        BlockPos transformed = pos;


        if (rotation != BlockRotation.NONE) {
            transformed = rotate(transformed, getInverseRotation(rotation));
        }


        if (mirror != BlockMirror.NONE) {
            transformed = mirror(transformed, mirror);
        }

        return transformed;
    }


    public static Vec3d getTransformedPosition(Vec3d pos, BlockMirror mirror, BlockRotation rotation) {
        Vec3d transformed = pos;

        if (mirror != BlockMirror.NONE) {
            transformed = mirrorVec3d(transformed, mirror);
        }

        if (rotation != BlockRotation.NONE) {
            transformed = rotateVec3d(transformed, rotation);
        }

        return transformed;
    }

    private static BlockPos mirror(BlockPos pos, BlockMirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
            case FRONT_BACK:
                return new BlockPos(pos.getX(), pos.getY(), -pos.getZ());
            default:
                return pos;
        }
    }

    private static BlockPos rotate(BlockPos pos, BlockRotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180:
                return new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default:
                return pos;
        }
    }

    private static Vec3d mirrorVec3d(Vec3d pos, BlockMirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return new Vec3d(-pos.x, pos.y, pos.z);
            case FRONT_BACK:
                return new Vec3d(pos.x, pos.y, -pos.z);
            default:
                return pos;
        }
    }

    private static Vec3d rotateVec3d(Vec3d pos, BlockRotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return new Vec3d(-pos.z, pos.y, pos.x);
            case CLOCKWISE_180:
                return new Vec3d(-pos.x, pos.y, -pos.z);
            case COUNTERCLOCKWISE_90:
                return new Vec3d(pos.z, pos.y, -pos.x);
            default:
                return pos;
        }
    }

    private static BlockRotation getInverseRotation(BlockRotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return BlockRotation.COUNTERCLOCKWISE_90;
            case CLOCKWISE_180:
                return BlockRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90:
                return BlockRotation.CLOCKWISE_90;
            default:
                return BlockRotation.NONE;
        }
    }
}