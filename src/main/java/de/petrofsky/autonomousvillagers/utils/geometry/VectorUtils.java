package de.petrofsky.autonomousvillagers.utils.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * Small helpers for computing direction/displacement vectors between two
 * {@link BlockPos} positions ({@code end - start}), either as a full 3D vector or
 * projected onto one of the three axis-aligned planes (with that plane's constant
 * axis simply zeroed out rather than dropped, since {@link Vec3i} — unlike
 * {@link Point2D} — always has three components).
 *
 * <p>Unlike {@link Plane}, which is used for generating shapes and can be pointed
 * at any plane, the three 2D variants here are fixed, one method per plane, since a
 * direction vector is typically needed for one known purpose (e.g. horizontal
 * movement always uses XZ) rather than a generically parameterized one.</p>
 */
public class VectorUtils {

    /**
     * Direction from {@code start} to {@code end}, projected onto the YZ plane
     * (X component zeroed out).
     *
     * @param start the starting position.
     * @param end   the target position.
     * @return the displacement vector, with X always 0.
     */
    public static Vec3i getVectorYZ(BlockPos start, BlockPos end) {
        return new Vec3i(0,
                end.getY()-start.getY(),
                end.getZ() - start.getZ());
    }

    /**
     * Direction from {@code start} to {@code end}, projected onto the XZ plane
     * (Y component zeroed out) — i.e. the purely horizontal direction, ignoring
     * height.
     *
     * @param start the starting position.
     * @param end   the target position.
     * @return the displacement vector, with Y always 0.
     */
    public static Vec3i getVectorXZ(BlockPos start, BlockPos end) {
        return new Vec3i(end.getX() - start.getX(),
                0,
                end.getZ() - start.getZ());
    }

    /**
     * Direction from {@code start} to {@code end}, projected onto the XY plane
     * (Z component zeroed out).
     *
     * @param start the starting position.
     * @param end   the target position.
     * @return the displacement vector, with Z always 0.
     */
    public static Vec3i getVectorXY(BlockPos start, BlockPos end) {
        return new Vec3i(end.getX() - start.getX(),
                end.getY()-start.getY(), 0);
    }

    /**
     * Full, unprojected direction from {@code start} to {@code end}.
     *
     * @param start the starting position.
     * @param end   the target position.
     * @return the full 3D displacement vector.
     */
    public static Vec3i getVector3D(BlockPos start, BlockPos end) {
        return new Vec3i(end.getX() - start.getX(),
                end.getY()-start.getY(),
                end.getZ() - start.getZ());
    }
}
