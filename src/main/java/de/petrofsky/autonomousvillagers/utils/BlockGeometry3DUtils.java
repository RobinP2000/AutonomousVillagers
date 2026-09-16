package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BlockGeometry3DUtils {

    public static List<BlockPos> getSphereBetween(
            BlockPos center, int radiusXZ1, int radiusXZ2, int radiusY1, int radiusY2,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {

        int innerRadiusXZ = Math.min(radiusXZ1, radiusXZ2);
        int outerRadiusXZ = Math.max(radiusXZ1, radiusXZ2);
        int innerRadiusY = Math.min(radiusY1, radiusY2);
        int outerRadiusY = Math.max(radiusY1, radiusY2);

        List<BlockPos> pointResult = new ArrayList<>();

        long outerRySq = (long) outerRadiusY * outerRadiusY;
        long outerRxzSq = (long) outerRadiusXZ * outerRadiusXZ;
        long outerRHS = outerRxzSq * outerRySq;

        long innerRySq = (long) innerRadiusY * innerRadiusY;
        long innerRxzSq = (long) innerRadiusXZ * innerRadiusXZ;
        long innerRHS = innerRxzSq * innerRySq;

        for (int x = -outerRadiusXZ; x <= outerRadiusXZ; x++) {
            for (int y = -outerRadiusY; y <= outerRadiusY; y++) {
                for (int z = -outerRadiusXZ; z <= outerRadiusXZ; z++) {

                    long xzSq = (long) x * x + (long) z * z;
                    long ySq = (long) y * y;

                    boolean outsideOuter;
                    if (Math.abs(x) > outerRadiusXZ || Math.abs(z) > outerRadiusXZ || Math.abs(y) > outerRadiusY) {
                        outsideOuter = true;
                    } else {
                        long outerLHS = xzSq * outerRySq + ySq * outerRxzSq;
                        outsideOuter = outerBorderExclusive ? (outerLHS >= outerRHS) : (outerLHS > outerRHS);
                    }
                    if (outsideOuter) continue;

                    boolean insideInner = false;
                    if (Math.abs(x) <= innerRadiusXZ && Math.abs(z) <= innerRadiusXZ && Math.abs(y) <= innerRadiusY) {
                        long innerLHS = xzSq * innerRySq + ySq * innerRxzSq;
                        insideInner = innerBorderExclusive ? (innerLHS <= innerRHS) : (innerLHS < innerRHS);
                    }
                    if (insideInner) continue;

                    pointResult.add(center.offset(x, y, z));
                }
            }
        }

        if (shuffle) {
            Collections.shuffle(pointResult);
        }

        return pointResult;
    }

    public static List<BlockPos> getNeighborByTargetDistance(BlockPos origin, BlockPos target) {
        List<BlockPos> neighbor = Arrays.asList(origin.north(), origin.east(), origin.south(), origin.west());
        neighbor.sort(((pos1, pos2) -> Long
                .compareUnsigned(getDistanceSquared(target, pos1), getDistanceSquared(target, pos2))));
        return neighbor;
    }

    public static List<BlockPos> getNeighborExcept(BlockPos origin, BlockPos except) {
        ArrayList<BlockPos> neighbor = new ArrayList<>(List.of(origin.north(), origin.east(), origin.south(), origin.west()));
        neighbor.removeIf(blockPos -> blockPos.equals(except));
        return neighbor;
    }



    public static boolean withinDistance(BlockPos blockPos1, BlockPos blockPos2, int distance) {
        long maximumDistance = (long) distance * distance;
        return getDistanceSquared(blockPos1, blockPos2) < maximumDistance;
    }

    public static long getDistanceSquared(BlockPos blockPos1, BlockPos blockPos2) {
        long distanceX = (long) blockPos1.getX() - blockPos2.getX();
        long distanceY = (long) blockPos1.getY() - blockPos2.getY();
        long distanceZ = (long) blockPos1.getZ() - blockPos2.getZ();

        return distanceX * distanceX + distanceY * distanceY + distanceZ *distanceZ;
    }

    public static BlockPos getClosest(List<BlockPos> positions, BlockPos target)  {
        if(positions == null || positions.isEmpty()) {
            return null;
        }
        BlockPos current = positions.getFirst();
        for (BlockPos origin : positions) {
            if(getDistanceSquared(target, current) > getDistanceSquared(target, origin)) {
                current = origin;
            }
        }
        return current;
    }
}
