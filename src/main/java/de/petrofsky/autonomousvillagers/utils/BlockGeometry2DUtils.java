package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;

import java.util.*;

public class BlockGeometry2DUtils {

    private static List<Point2D> getRectangle(
            Point2D point1, Point2D point2, boolean borderExclusive, boolean shuffle) {
        int maxU = Math.max(point1.u(), point2.u()), maxV = Math.max(point1.v(), point2.v());
        int minU = Math.min(point1.u(), point2.u()), minV = Math.min(point1.v(), point2.v());

        if(borderExclusive) {
            if (maxU - minU < 2 || maxV - minV < 2) return List.of();
            maxU--; maxV--; minU++; minV++;
        }

        List<Point2D> pointResult = new ArrayList<>();
        for ( int coordinate1 = minU; coordinate1 <= maxU; coordinate1++) {
            for ( int coordinate2 = minV; coordinate2 <= maxV; coordinate2++) {
                pointResult.add(new Point2D(coordinate1, coordinate2));
            }
        }
        if (shuffle) {
            Collections.shuffle(pointResult);
        }
        return pointResult;
    }

    private static List<Point2D> getRectangle(
            Point2D center, int halfWidth, int halfHeight, boolean borderExclusive, boolean shuffle) {
        if(borderExclusive) {
            halfWidth--; halfHeight--;
        }

        if (halfWidth < 0 || halfHeight < 0) {
            return List.of();
        }

        int minU = center.u() - halfWidth, minV = center.v() - halfHeight;
        int maxU = center.u() + halfWidth, maxV = center.v() + halfHeight;
        int pointSize = (maxU - minU) * (maxV - minV) + 1;
        List<Point2D> pointResult = new ArrayList<>(pointSize);
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                pointResult.add(new Point2D(u,v));
            }
        }

        if (shuffle) {
            Collections.shuffle(pointResult);
        }
        return pointResult;
    }

    public static List<BlockPos> getRectangleXZ(BlockPos pos1, BlockPos pos2, int y) {
        return getRectangleXZ(pos1, pos2, y,false);
    }

    public static List<BlockPos> getRectangleXZ(BlockPos pos1, BlockPos pos2, int y, boolean borderExclusive) {
        return getRectangleXZ(pos1, pos2, y,borderExclusive, false);
    }

    public static List<BlockPos> getRectangleXZ(
            BlockPos pos1, BlockPos pos2, int y, boolean borderExclusive, boolean shuffle) {
        return getRectangle(new Point2D(pos1.getX(), pos1.getZ()), new Point2D(pos2.getX(), pos2.getZ()),
                borderExclusive, shuffle).stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getRectangleXZ(
            BlockPos center, int halfWidth, int halfHeight, int y) {
        return getRectangleXZ(center, halfWidth, halfHeight, y, false);
    }

    public static List<BlockPos> getRectangleXZ(
            BlockPos center, int halfWidth, int halfHeight, int y, boolean borderExclusive) {
        return getRectangleXZ(center, halfWidth, halfHeight, y, borderExclusive, false);
    }

    public static List<BlockPos> getRectangleXZ(
            BlockPos center, int halfWidth, int halfHeight, int y, boolean borderExclusive, boolean shuffle) {
        return getRectangle(new Point2D(center.getX(), center.getZ()), halfWidth, halfHeight,
                borderExclusive, shuffle).stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getRectangleXY(BlockPos pos1, BlockPos pos2, int z) {
        return getRectangleXY(pos1, pos2, z, false);
    }

    public static List<BlockPos> getRectangleXY(BlockPos pos1, BlockPos pos2, int z, boolean borderExclusive) {
        return getRectangleXY(pos1, pos2, z, borderExclusive, false);
    }

    public static List<BlockPos> getRectangleXY(
            BlockPos pos1, BlockPos pos2, int z, boolean borderExclusive, boolean shuffle) {
        return getRectangle(new Point2D(pos1.getX(), pos1.getY()), new Point2D(pos2.getX(), pos2.getY()),
                borderExclusive, shuffle).stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getRectangleXY(
            BlockPos center, int halfWidth, int halfHeight, int z) {
        return getRectangleXY(center, halfWidth, halfHeight, z, false);
    }

    public static List<BlockPos> getRectangleXY(
            BlockPos center, int halfWidth, int halfHeight, int z, boolean borderExclusive) {
        return getRectangleXY(center, halfWidth, halfHeight, z, borderExclusive, false);
    }

    public static List<BlockPos> getRectangleXY(
            BlockPos center, int halfWidth, int halfHeight, int z, boolean borderExclusive, boolean shuffle) {
        return getRectangle(new Point2D(center.getX(), center.getY()), halfWidth, halfHeight,
                borderExclusive, shuffle).stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getRectangleYZ(BlockPos pos1, BlockPos pos2, int x) {
        return getRectangleYZ(pos1, pos2, x,false);
    }

    public static List<BlockPos> getRectangleYZ(BlockPos pos1, BlockPos pos2, int x, boolean borderExclusive) {
        return getRectangleYZ(pos1, pos2, x, borderExclusive, false);
    }

    public static List<BlockPos> getRectangleYZ(
            BlockPos pos1, BlockPos pos2, int x, boolean borderExclusive, boolean shuffle) {
        return getRectangle(new Point2D(pos1.getY(), pos1.getZ()), new Point2D(pos2.getY(), pos2.getZ()),
                borderExclusive,shuffle).stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    public static List<BlockPos> getRectangleYZ(
            BlockPos center, int halfWidth, int halfHeight, int x) {
        return getRectangleYZ(center, halfWidth, halfHeight, x, false);
    }

    public static List<BlockPos> getRectangleYZ(
            BlockPos center, int halfWidth, int halfHeight, int x, boolean borderExclusive) {
        return getRectangleYZ(center, halfWidth, halfHeight, x, borderExclusive, false);
    }

    public static List<BlockPos> getRectangleYZ(
            BlockPos center, int halfWidth, int halfHeight, int x, boolean borderExclusive, boolean shuffle) {
        return getRectangle(new Point2D(center.getY(), center.getZ()), halfWidth, halfHeight,
                borderExclusive, shuffle).stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getRectangleBorder(Point2D point1, Point2D point2) {
        int max1 = Math.max(point1.u(), point2.u()), max2 = Math.max(point1.v(), point2.v());
        int min1 = Math.min(point1.u(), point2.u()), min2 = Math.min(point1.v(), point2.v());

        boolean coordinates1Equal = min1 == max1;
        boolean coordinates2Equal = min2 == max2;

        List<Point2D> pointResult = new ArrayList<>();
        for ( int coordinate1 = min1; coordinate1 <= max1; coordinate1++) {
           pointResult.add(new Point2D(coordinate1, min2));
           if (!coordinates2Equal)
               pointResult.add(new Point2D(coordinate1, max2));
        }

        if ( max2 - min2 > 1) {
            for ( int coordinate2 = min2+1; coordinate2 <= max2-1; coordinate2++) {
                pointResult.add(new Point2D(min1, coordinate2));
                if(!coordinates1Equal)
                    pointResult.add(new Point2D(max1, coordinate2));
            }
        }
        return pointResult;
    }

    public static List<BlockPos> getRectangleBorderXZ(BlockPos pos1, BlockPos pos2, int y) {
        return getRectangleBorder(new Point2D(pos1.getX(), pos1.getZ()), new Point2D(pos2.getX(), pos2.getZ()))
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getRectangleBorderXY(BlockPos pos1, BlockPos pos2, int z) {
        return getRectangleBorder(new Point2D(pos1.getX(), pos1.getY()), new Point2D(pos2.getX(), pos2.getY()))
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getRectangleBorderYZ(BlockPos pos1, BlockPos pos2, int x) {
        return getRectangleBorder(new Point2D(pos1.getY(), pos1.getZ()), new Point2D(pos2.getY(), pos2.getZ()))
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getSortedRectangle(
            Point2D point1, Point2D point2, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        int maxU = Math.max(point1.u(), point2.u()), maxV = Math.max(point1.v(), point2.v());
        int minU = Math.min(point1.u(), point2.u()), minV = Math.min(point1.v(), point2.v());
        long sumU = minU + maxU, sumV = minV + maxV;

        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                if (borderExclusive && (u == minU || u == maxU || v == minV || v == maxV)) {
                    continue;
                }
                long distanceU = 2L * u - sumU;
                long distanceV = 2L * v - sumV;
                long distance = distanceU * distanceU + distanceV * distanceV;

                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(u, v));

            }
        }

        return flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    public static List<BlockPos> getSortedRectangleXZ(
            BlockPos pos1, BlockPos pos2, int y, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangle(new Point2D(pos1.getX(), pos1.getZ()), new Point2D(pos2.getX(), pos2.getZ()),
                borderExclusive, shuffle, descOrder).stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getSortedRectangleXY(
            BlockPos pos1, BlockPos pos2, int z, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangle(new Point2D(pos1.getX(), pos1.getY()), new Point2D(pos2.getX(), pos2.getY()),
                borderExclusive, shuffle, descOrder).stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getSortedRectangleYZ(
            BlockPos pos1, BlockPos pos2, int x, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangle(new Point2D(pos1.getY(), pos1.getZ()), new Point2D(pos2.getY(), pos2.getZ()),
                borderExclusive, shuffle, descOrder).stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getSortedRectangle(
            Point2D center, int halfWidth, int halfHeight, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        if(borderExclusive) {
            halfWidth--; halfHeight--;
        }

        if (halfWidth < 0 || halfHeight < 0) {
            return List.of();
        }

        int minU = center.u() - halfWidth, minV = center.v() - halfHeight;
        int maxU = center.u() + halfWidth, maxV = center.v() + halfHeight;
        long sumU = minU + maxU, sumV = minV + maxV;

        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                long distanceU = 2L * u - sumU;
                long distanceV = 2L * v - sumV;
                long distance = distanceU * distanceU + distanceV * distanceV;
                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(u, v));

            }
        }
        return flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    public static List<BlockPos> getSortedRectangleXZ(
            BlockPos center, int halfWidth, int halfHeight, int y, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangle(new Point2D(center.getX(), center.getZ()), halfWidth, halfHeight,
                borderExclusive, shuffle, descOrder).stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getSortedRectangleXY(
            BlockPos center, int halfWidth, int halfHeight, int z, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangle(new Point2D(center.getX(), center.getY()), halfWidth, halfHeight,
                borderExclusive, shuffle, descOrder).stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getSortedRectangleYZ(
            BlockPos center, int halfWidth, int halfHeight, int x, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangle(new Point2D(center.getY(), center.getZ()), halfWidth, halfHeight,
                borderExclusive, shuffle, descOrder).stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getRectangleBetween(Point2D point1, Point2D point2, Point2D point3,
            Point2D point4,  boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        int maxOuterU = Math.max(Math.max(point1.u(), point2.u()), Math.max(point3.u(), point4.u()));
        int maxInnerU = Math.min(Math.max(point1.u(), point2.u()), Math.max(point3.u(), point4.u()));
        int maxOuterV = Math.max(Math.max(point1.v(), point2.v()), Math.max(point3.v(), point4.v()));
        int maxInnerV = Math.min(Math.max(point1.v(), point2.v()), Math.max(point3.v(), point4.v()));
        int minOuterU = Math.min(Math.min(point1.u(), point2.u()), Math.min(point3.u(), point4.u()));
        int minInnerU = Math.max(Math.min(point1.u(), point2.u()), Math.min(point3.u(), point4.u()));
        int minOuterV = Math.min(Math.min(point1.v(), point2.v()), Math.min(point3.v(), point4.v()));
        int minInnerV = Math.max(Math.min(point1.v(), point2.v()), Math.min(point3.v(), point4.v()));

        List<Point2D> pointResult = new ArrayList<>();

        for (int u = minOuterU; u <= maxOuterU; u++) {
            for (int v = minOuterV; v <= maxOuterV; v++) {
                if (outerBorderExclusive && (u == minOuterU || u == maxOuterU || v == minOuterV || v == maxOuterV)) {
                    continue;
                }
                if (innerBorderExclusive &&
                        (u >= minInnerU && u <= maxInnerU && v >= minInnerV && v <= maxInnerV)) continue;
                if (!innerBorderExclusive &&
                        (u > minInnerU && u < maxInnerU && v > minInnerV && v < maxInnerV)) continue;

                pointResult.add(new Point2D(u, v));
            }
        }
        if (shuffle) Collections.shuffle(pointResult);
        return pointResult;
    }

    public static List<BlockPos> getRectangleBetweenXZ(
            BlockPos pos1, BlockPos pos2, BlockPos pos3, BlockPos pos4, int y,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getRectangleBetween(
                new Point2D(pos1.getX(), pos1.getZ()), new Point2D(pos2.getX(), pos2.getZ()),
                new Point2D(pos3.getX(), pos3.getZ()), new Point2D(pos4.getX(), pos4.getZ()),
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getRectangleBetweenXY(
            BlockPos pos1, BlockPos pos2, BlockPos pos3, BlockPos pos4, int z,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getRectangleBetween(
                new Point2D(pos1.getX(), pos1.getY()), new Point2D(pos2.getX(), pos2.getY()),
                new Point2D(pos3.getX(), pos3.getY()), new Point2D(pos4.getX(), pos4.getY()),
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getRectangleBetweenYZ(
            BlockPos pos1, BlockPos pos2, BlockPos pos3, BlockPos pos4, int x,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getRectangleBetween(
                new Point2D(pos1.getY(), pos1.getZ()), new Point2D(pos2.getY(), pos2.getZ()),
                new Point2D(pos3.getY(), pos3.getZ()), new Point2D(pos4.getY(), pos4.getZ()),
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getRectangleBetween(Point2D center, int halfWidth1, int halfHeight1, int halfWidth2,
            int halfHeight2,  boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        int outerWidth = Math.max(halfWidth1, halfWidth2);
        int innerWidth = Math.min(halfWidth1, halfWidth2);
        int outerHeight = Math.max(halfHeight1, halfHeight2);
        int innerHeight = Math.min(halfHeight1, halfHeight2);

        int maxOuterU = center.u() + outerWidth, maxInnerU = center.u() + innerWidth;
        int maxOuterV = center.v() + outerHeight, maxInnerV = center.v() + innerHeight;
        int minOuterU = center.u() - outerWidth, minInnerU = center.u() - innerWidth;
        int minOuterV = center.v() - outerHeight, minInnerV = center.v() - innerHeight;

        if (outerWidth < 0 || outerHeight < 0) return List.of();

        List<Point2D> pointResult = new ArrayList<>();

        for (int u = minOuterU; u <= maxOuterU; u++) {
            for (int v = minOuterV; v <= maxOuterV; v++) {
                if (outerBorderExclusive && (u == minOuterU || u == maxOuterU || v == minOuterV || v == maxOuterV)) {
                    continue;
                }
                if (innerBorderExclusive &&
                        (u >= minInnerU && u <= maxInnerU && v >= minInnerV && v <= maxInnerV)) continue;
                if (!innerBorderExclusive &&
                        (u > minInnerU && u < maxInnerU && v > minInnerV && v < maxInnerV)) continue;

                pointResult.add(new Point2D(u, v));
            }
        }
        if (shuffle) Collections.shuffle(pointResult);
        return pointResult;
    }

    public static List<BlockPos> getRectangleBetweenXZ(
            BlockPos center, int halfWidth1, int halfHeight1, int halfWidth2, int halfHeight2, int y,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getRectangleBetween(
                new Point2D(center.getX(), center.getZ()), halfWidth1, halfHeight1, halfWidth2, halfHeight2,
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getRectangleBetweenXY(
            BlockPos center, int halfWidth1, int halfHeight1, int halfWidth2, int halfHeight2, int z,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getRectangleBetween(
                new Point2D(center.getX(), center.getY()), halfWidth1, halfHeight1, halfWidth2, halfHeight2,
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getRectangleBetweenYZ(
            BlockPos center, int halfWidth1, int halfHeight1, int halfWidth2, int halfHeight2, int x,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getRectangleBetween(
                new Point2D(center.getY(), center.getZ()), halfWidth1, halfHeight1, halfWidth2, halfHeight2,
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getSortedRectangleBetween(Point2D point1, Point2D point2, Point2D point3,
            Point2D point4,  boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle,
                                                           boolean descOrder) {
        int maxOuterU = Math.max(Math.max(point1.u(), point2.u()), Math.max(point3.u(), point4.u()));
        int maxInnerU = Math.min(Math.max(point1.u(), point2.u()), Math.max(point3.u(), point4.u()));
        int maxOuterV = Math.max(Math.max(point1.v(), point2.v()), Math.max(point3.v(), point4.v()));
        int maxInnerV = Math.min(Math.max(point1.v(), point2.v()), Math.max(point3.v(), point4.v()));
        int minOuterU = Math.min(Math.min(point1.u(), point2.u()), Math.min(point3.u(), point4.u()));
        int minInnerU = Math.max(Math.min(point1.u(), point2.u()), Math.min(point3.u(), point4.u()));
        int minOuterV = Math.min(Math.min(point1.v(), point2.v()), Math.min(point3.v(), point4.v()));
        int minInnerV = Math.max(Math.min(point1.v(), point2.v()), Math.min(point3.v(), point4.v()));

        long sumU = (long) minOuterU + maxOuterU, sumV = (long) minOuterV + maxOuterV;

        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        for (int u = minOuterU; u <= maxOuterU; u++) {
            for (int v = minOuterV; v <= maxOuterV; v++) {
                if (outerBorderExclusive && (u == minOuterU || u == maxOuterU || v == minOuterV || v == maxOuterV)) {
                    continue;
                }
                if (innerBorderExclusive &&
                        (u >= minInnerU && u <= maxInnerU && v >= minInnerV && v <= maxInnerV)) continue;
                if (!innerBorderExclusive &&
                        (u > minInnerU && u < maxInnerU && v > minInnerV && v < maxInnerV)) continue;

                long distanceU = 2L * u - sumU;
                long distanceV = 2L * v - sumV;
                long distance = distanceU * distanceU + distanceV * distanceV;
                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(u, v));
            }
        }
        return flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    public static List<BlockPos> getSortedRectangleBetweenXZ(
            BlockPos pos1, BlockPos pos2, BlockPos pos3, BlockPos pos4, int y,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangleBetween(
                new Point2D(pos1.getX(), pos1.getZ()), new Point2D(pos2.getX(), pos2.getZ()),
                new Point2D(pos3.getX(), pos3.getZ()), new Point2D(pos4.getX(), pos4.getZ()),
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getSortedRectangleBetweenXY(
            BlockPos pos1, BlockPos pos2, BlockPos pos3, BlockPos pos4, int z,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangleBetween(
                new Point2D(pos1.getX(), pos1.getY()), new Point2D(pos2.getX(), pos2.getY()),
                new Point2D(pos3.getX(), pos3.getY()), new Point2D(pos4.getX(), pos4.getY()),
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getSortedRectangleBetweenYZ(
            BlockPos pos1, BlockPos pos2, BlockPos pos3, BlockPos pos4, int x,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangleBetween(
                new Point2D(pos1.getY(), pos1.getZ()), new Point2D(pos2.getY(), pos2.getZ()),
                new Point2D(pos3.getY(), pos3.getZ()), new Point2D(pos4.getY(), pos4.getZ()),
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getSortedRectangleBetween(Point2D center, int halfWidth1, int halfHeight1,
            int halfWidth2, int halfHeight2,  boolean innerBorderExclusive, boolean outerBorderExclusive,
                                                           boolean shuffle, boolean descOrder) {
        halfWidth1 = Math.abs(halfWidth1);
        halfWidth2 = Math.abs(halfWidth2);
        halfHeight1 = Math.abs(halfHeight1);
        halfHeight2 = Math.abs(halfHeight2);

        int outerWidth = Math.max(halfWidth1, halfWidth2);
        int innerWidth = Math.min(halfWidth1, halfWidth2);
        int outerHeight = Math.max(halfHeight1, halfHeight2);
        int innerHeight = Math.min(halfHeight1, halfHeight2);

        int maxOuterU = center.u() + outerWidth, maxInnerU = center.u() + innerWidth;
        int maxOuterV = center.v() + outerHeight, maxInnerV = center.v() + innerHeight;
        int minOuterU = center.u() - outerWidth, minInnerU = center.u() - innerWidth;
        int minOuterV = center.v() - outerHeight, minInnerV = center.v() - innerHeight;

        long sumU = (long) minOuterU + maxOuterU, sumV = (long) minOuterV + maxOuterV;

        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        for (int u = minOuterU; u <= maxOuterU; u++) {
            for (int v = minOuterV; v <= maxOuterV; v++) {
                if (outerBorderExclusive && (u == minOuterU || u == maxOuterU || v == minOuterV || v == maxOuterV)) {
                    continue;
                }
                if (innerBorderExclusive &&
                        (u >= minInnerU && u <= maxInnerU && v >= minInnerV && v <= maxInnerV)) continue;
                if (!innerBorderExclusive &&
                        (u > minInnerU && u < maxInnerU && v > minInnerV && v < maxInnerV)) continue;

                long distanceU = 2L * u - sumU;
                long distanceV = 2L * v - sumV;
                long distance = distanceU * distanceU + distanceV * distanceV;
                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(u, v));
            }
        }
        return flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    public static List<BlockPos> getSortedRectangleBetweenXZ(
            BlockPos center, int halfWidth1, int halfHeight1, int halfWidth2, int halfHeight2, int y,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangleBetween(
                new Point2D(center.getX(), center.getZ()), halfWidth1, halfHeight1, halfWidth2, halfHeight2,
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getSortedRectangleBetweenXY(
            BlockPos center, int halfWidth1, int halfHeight1, int halfWidth2, int halfHeight2, int z,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangleBetween(
                new Point2D(center.getX(), center.getY()), halfWidth1, halfHeight1, halfWidth2, halfHeight2,
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getSortedRectangleBetweenYZ(
            BlockPos center, int halfWidth1, int halfHeight1, int halfWidth2, int halfHeight2, int x,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedRectangleBetween(
                new Point2D(center.getY(), center.getZ()), halfWidth1, halfHeight1, halfWidth2, halfHeight2,
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getCircle(Point2D center, int radius, boolean borderExclusive, boolean shuffle) {
        double radiusSquared = radius * radius;
        List<Point2D> pointResult = new ArrayList<>();

        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                if((borderExclusive && (u * u) + (v * v) < radiusSquared)
                        || (!borderExclusive && (u * u) + (v * v) <= radiusSquared) ) {
                    pointResult.add(new Point2D(center.u() + u, center.v() + v));
                }
            }
        }
        if(shuffle) {
            Collections.shuffle(pointResult);
        }
        return pointResult;
    }

    public static List<BlockPos> getCircleXZ(BlockPos center, int radius, int y) {
        return getCircleXZ(center, radius, y, false);
    }

    public static List<BlockPos> getCircleXZ(BlockPos center, int radius, int y, boolean borderExclusive) {
        return getCircleXZ(center, radius, y, borderExclusive, false);
    }

    public static List<BlockPos> getCircleXZ(
            BlockPos center, int radius, int y, boolean borderExclusive, boolean shuffle) {
        return getCircle(new Point2D(center.getX(), center.getZ()), radius, borderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getCircleXY(BlockPos center, int radius, int z) {
        return getCircleXY(center, radius, z, false);
    }

    public static List<BlockPos> getCircleXY(BlockPos center, int radius, int z, boolean borderExclusive) {
        return getCircleXY(center, radius, z, borderExclusive, false);
    }

    public static List<BlockPos> getCircleXY(
            BlockPos center, int radius, int z, boolean borderExclusive, boolean shuffle) {
        return getCircle(new Point2D(center.getX(), center.getY()), radius, borderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getCircleYZ(BlockPos center, int radius, int x) {
        return getCircleYZ(center, radius, x, false);
    }

    public static List<BlockPos> getCircleYZ(BlockPos center, int radius, int x, boolean borderExclusive) {
        return getCircleYZ(center, radius, x, borderExclusive, false);
    }

    public static List<BlockPos> getCircleYZ(
            BlockPos center, int radius, int x, boolean borderExclusive, boolean shuffle) {
        return getCircle(new Point2D(center.getY(), center.getZ()), radius, borderExclusive, shuffle)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getCircleBorder(Point2D center, int radius) {
        double innerBound = (radius - 0.5) * (radius - 0.5);
        double outerBound = (radius + 0.5) * (radius + 0.5);
        List<Point2D> pointResult = new ArrayList<>();

        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                int distSquared = (u * u) + (v * v);
                if (distSquared >= innerBound && distSquared <= outerBound) {
                    pointResult.add(new Point2D(center.u() + u, center.v() + v));
                }
            }
        }
        return pointResult;
    }

    public static List<BlockPos> getCircleBorderXZ(BlockPos center, int radius, int y) {
        return getCircleBorder(new Point2D(center.getX(), center.getZ()), radius).stream()
                .map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getCircleBorderXY(BlockPos center, int radius, int z) {
        return getCircleBorder(new Point2D(center.getX(), center.getY()), radius).stream()
                .map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getCircleBorderYZ(BlockPos center, int radius, int x) {
        return getCircleBorder(new Point2D(center.getY(), center.getZ()), radius).stream()
                .map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getSortedCircle(
            Point2D center, int radius, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        long radiusSquared = (long) radius * radius;
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                long distance = (long) u * u + (long) v * v;

                if ((borderExclusive && distance >= radiusSquared) ||
                        (!borderExclusive && distance > radiusSquared) ) {
                    continue;
                }

                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(center.u() + u, center.v() + v));
            }
        }

        return flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    public static List<BlockPos> getSortedCircleXZ(
            BlockPos center, int radius, int y, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedCircle(new Point2D(center.getX(), center.getZ()), radius, borderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getSortedCircleXY(
            BlockPos center, int radius, int z, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedCircle(new Point2D(center.getX(), center.getY()), radius, borderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getSortedCircleYZ(
            BlockPos center, int radius, int x, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedCircle(new Point2D(center.getY(), center.getZ()), radius, borderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getCircleBetween(Point2D center, int radius1, int radius2,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        int innerRadius = Math.min(radius1, radius2);
        int outerRadius = Math.max(radius1, radius2);
        long innerRadiusSquared = (long) innerRadius * innerRadius;
        long outerRadiusSquared = (long) outerRadius * outerRadius;
        List<Point2D> pointResult = new ArrayList<>();

        for (int u = -outerRadius; u <= outerRadius; u++) {
            for (int v = -outerRadius; v <= outerRadius; v++) {
                long distance = ((long) u * u) + ((long) v * v);

                if((outerBorderExclusive && distance >= outerRadiusSquared)
                        || (! outerBorderExclusive && distance > outerRadiusSquared)
                        || (innerBorderExclusive && distance <= innerRadiusSquared)
                        || (!innerBorderExclusive && distance < innerRadiusSquared)) continue;
                pointResult.add(new Point2D(center.u() + u, center.v() + v));
            }
        }
        if(shuffle) {
            Collections.shuffle(pointResult);
        }
        return pointResult;
    }

    public static List<BlockPos> getCircleBetweenXZ(
            BlockPos center, int radius1, int radius2, int y,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getCircleBetween(new Point2D(center.getX(), center.getZ()), radius1, radius2,
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getCircleBetweenXY(
            BlockPos center, int radius1, int radius2, int z,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getCircleBetween(new Point2D(center.getX(), center.getY()), radius1, radius2,
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getCircleBetweenYZ(
            BlockPos center, int radius1, int radius2, int x,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        return getCircleBetween(new Point2D(center.getY(), center.getZ()), radius1, radius2,
                innerBorderExclusive, outerBorderExclusive, shuffle)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> getSortedCircleBetween(Point2D center, int radius1, int radius2,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        int innerRadius = Math.min(radius1, radius2);
        int outerRadius = Math.max(radius1, radius2);
        long innerRadiusSquared = (long) innerRadius * innerRadius;
        long outerRadiusSquared = (long) outerRadius * outerRadius;
        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        for (int u = -outerRadius; u <= outerRadius; u++) {
            for (int v = -outerRadius; v <= outerRadius; v++) {
                long distance = ((long) u * u) + ((long) v * v);

                if((outerBorderExclusive && distance >= outerRadiusSquared)
                        || (! outerBorderExclusive && distance > outerRadiusSquared)
                        || (innerBorderExclusive && distance <= innerRadiusSquared)
                        || (!innerBorderExclusive && distance < innerRadiusSquared)) continue;
                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(center.u() + u, center.v() + v));
            }
        }
        return flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    public static List<BlockPos> getSortedCircleBetweenXZ(
            BlockPos center, int radius1, int radius2, int y,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedCircleBetween(new Point2D(center.getX(), center.getZ()), radius1, radius2,
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), y, point.v())).toList();
    }

    public static List<BlockPos> getSortedCircleBetweenXY(
            BlockPos center, int radius1, int radius2, int z,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedCircleBetween(new Point2D(center.getX(), center.getY()), radius1, radius2,
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(point.u(), point.v(), z)).toList();
    }

    public static List<BlockPos> getSortedCircleBetweenYZ(
            BlockPos center, int radius1, int radius2, int x,
            boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle, boolean descOrder) {
        return getSortedCircleBetween(new Point2D(center.getY(), center.getZ()), radius1, radius2,
                innerBorderExclusive, outerBorderExclusive, shuffle, descOrder)
                .stream().map(point -> new BlockPos(x, point.u(), point.v())).toList();
    }

    private static List<Point2D> flatTreeMapOfPoints(
            TreeMap<Long, List<Point2D>> pointMap, boolean shuffle, boolean descOrder) {
        Collection<List<Point2D>> valuesSorted = descOrder
                ? pointMap.descendingMap().values()
                : pointMap.values();
        int totalPoints = 0;
        for (List<Point2D> list : pointMap.values()) {
            totalPoints += list.size();
        }
        List<Point2D> pointResult = new ArrayList<>(totalPoints);
        for (List<Point2D> sameDistancePoints : valuesSorted) {
            if (shuffle) {
                Collections.shuffle(sameDistancePoints);
            }
            pointResult.addAll(sameDistancePoints);
        }
        return pointResult;
    }

    public static BlockPos getLowestBlock(List<BlockPos> blocks) {
        if(blocks.isEmpty()) return null;
        BlockPos blockPos = blocks.getFirst();
        int y = blockPos.getY();
        for(BlockPos next : blocks) {
            if (next.getY() < y){
                blockPos = next;
                y = next.getY();
            }
        }
        return blockPos;
    }
}
