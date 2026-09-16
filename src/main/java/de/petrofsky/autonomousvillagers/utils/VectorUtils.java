package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public class VectorUtils {

    public static Vec3i getVectorYZ(BlockPos start, BlockPos end) {
        return new Vec3i(0,
                end.getY()-start.getY(),
                end.getZ() - start.getZ());
    }

    public static Vec3i getVectorXZ(BlockPos start, BlockPos end) {
        return new Vec3i(end.getX() - start.getX(),
                0,
                end.getZ() - start.getZ());
    }

    public static Vec3i getVectorXY(BlockPos start, BlockPos end) {
        return new Vec3i(end.getX() - start.getX(),
                end.getY()-start.getY(), 0);
    }

    public static Vec3i getVector3D(BlockPos start, BlockPos end) {
        return new Vec3i(end.getX() - start.getX(),
                end.getY()-start.getY(),
                end.getZ() - start.getZ());
    }
}
