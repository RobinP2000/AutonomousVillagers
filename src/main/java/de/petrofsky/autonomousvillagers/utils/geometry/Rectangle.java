package de.petrofsky.autonomousvillagers.utils.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Pure 2D rectangle point generation, working entirely in the abstract
 * {@link Point2D} {@code (u, v)} coordinate system (no Minecraft types involved).
 * Mirrors {@link Circle}'s API shape (filled / border-only / distance-sorted /
 * "between two shapes" hollow variants), but for axis-aligned rectangles instead
 * of circles, in both a two-corners form and a center + half-width/half-height
 * form.
 *
 * <p>This class is package-private; the public entry points are
 * {@link ShapeUtils#rectangle(net.minecraft.core.BlockPos, net.minecraft.core.BlockPos)}
 * and {@link ShapeUtils#rectangle(net.minecraft.core.BlockPos, int, int)}, and the
 * {@link BlockRectangle} / {@link BlockRectangleCentered} builders they return,
 * which additionally project these 2D points into 3D world space via a
 * {@link Plane}.</p>
 */
class Rectangle {

    /**
     * Generates all 2D points within a rectangle defined by two corner points.
     *
     * @param point1          The first corner point of the rectangle.
     * @param point2          The opposite corner point of the rectangle.
     * @param borderExclusive If true, the outermost ring of points is omitted.
     * @param shuffle         If true, the resulting list of points is randomly shuffled.
     * @return A list of all 2D points within the specified rectangular area.
     */
    protected static List<Point2D> getRectangle(
            Point2D point1, Point2D point2, boolean borderExclusive, boolean shuffle) {

        // Determine the absolute boundaries (bounding box) of the rectangle
        int maxU = Math.max(point1.u(), point2.u()), maxV = Math.max(point1.v(), point2.v());
        int minU = Math.min(point1.u(), point2.u()), minV = Math.min(point1.v(), point2.v());

        // Shrink the bounding box by 1 block on all sides if the border should be excluded
        if(borderExclusive) {
            // If the rectangle is too small to have an inner area, return empty
            if (maxU - minU < 2 || maxV - minV < 2) return List.of();
            maxU--; maxV--; minU++; minV++;
        }

        List<Point2D> pointResult = new ArrayList<>();
        // Iterate through the grid and collect all coordinates
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

    /**
     * Generates all 2D points for a rectangle extending outward from a central point.
     *
     * @param center          The central point of the rectangle.
     * @param halfWidth       The distance from the center to the left/right edges.
     * @param halfHeight      The distance from the center to the top/bottom edges.
     * @param borderExclusive If true, the outermost ring of points is omitted.
     * @param shuffle         If true, the resulting list of points is randomly shuffled.
     * @return A list of all 2D points within the specified rectangular area.
     */
    protected static List<Point2D> getRectangle(
            Point2D center, int halfWidth, int halfHeight, boolean borderExclusive, boolean shuffle) {
        // Reduce the dimensions directly to exclude the outer border
        if(borderExclusive) {
            halfWidth--; halfHeight--;
        }

        // Return empty if dimensions collapse below 0
        if (halfWidth < 0 || halfHeight < 0) {
            return List.of();
        }

        // Calculate absolute min/max grid boundaries based on the center offset
        int minU = center.u() - halfWidth, minV = center.v() - halfHeight;
        int maxU = center.u() + halfWidth, maxV = center.v() + halfHeight;

        // Pre-calculate the exact list size to prevent dynamic array resizing overhead
        int pointSize = (maxU - minU + 1) * (maxV - minV + 1);

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

    /**
     * Generates only the 1-block thick outer boundary (frame) of a rectangle.
     *
     * @param point1 The first corner point.
     * @param point2 The opposite corner point.
     * @return A list of 2D points representing the perimeter of the rectangle.
     */
    protected static List<Point2D> getRectangleBorder(Point2D point1, Point2D point2) {
        int max1 = Math.max(point1.u(), point2.u()), max2 = Math.max(point1.v(), point2.v());
        int min1 = Math.min(point1.u(), point2.u()), min2 = Math.min(point1.v(), point2.v());

        // Check if the area is essentially a 1D line to avoid duplicate points
        boolean coordinates1Equal = min1 == max1;
        boolean coordinates2Equal = min2 == max2;

        List<Point2D> pointResult = new ArrayList<>();

        // Generate the top and bottom horizontal edges
        for ( int coordinate1 = min1; coordinate1 <= max1; coordinate1++) {
            pointResult.add(new Point2D(coordinate1, min2));

            // Only add the opposite edge if it isn't identical (prevents duplicating a 1-thick line)
            if (!coordinates2Equal)
                pointResult.add(new Point2D(coordinate1, max2));
        }

        // Generate the left and right vertical edges (excluding the corners already added above)
        if ( max2 - min2 > 1) {
            for ( int coordinate2 = min2+1; coordinate2 <= max2-1; coordinate2++) {
                pointResult.add(new Point2D(min1, coordinate2));
                if(!coordinates1Equal)
                    pointResult.add(new Point2D(max1, coordinate2));
            }
        }
        return pointResult;
    }

    /**
     * Generates a rectangle whose points are sorted sequentially based on their Euclidean distance
     * from the geometric center. This is highly useful for progressive animations
     * (e.g., a villager plowing a field from the center expanding outwards).
     *
     * @param point1          The first corner point.
     * @param point2          The opposite corner point.
     * @param borderExclusive If true, skips the outermost ring.
     * @param shuffle         If true, shuffles points that share the EXACT same distance to avoid linear artifacts.
     * @param descOrder       If true, sorts from the outside moving inwards (descending distance).
     * @return A sequentially sorted list of 2D points.
     */
    protected static List<Point2D> getSortedRectangle(
            Point2D point1, Point2D point2, boolean borderExclusive, boolean shuffle, boolean descOrder) {
        int maxU = Math.max(point1.u(), point2.u()), maxV = Math.max(point1.v(), point2.v());
        int minU = Math.min(point1.u(), point2.u()), minV = Math.min(point1.v(), point2.v());

        // Sums are used to calculate the relative center dynamically.
        // We multiply by 2 later to entirely avoid precision loss from floating point math.
        long sumU = minU + maxU, sumV = minV + maxV;

        // A TreeMap automatically sorts entries by their natural key order (which we set to distance squared).
        TreeMap<Long, List<Point2D>> pointsByDistance = new TreeMap<>();
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                if (borderExclusive && (u == minU || u == maxU || v == minV || v == maxV)) {
                    continue;
                }

                // Calculate directional distance scaled by 2 (distanceU = 2u - (minU + maxU))
                long distanceU = 2L * u - sumU;
                long distanceV = 2L * v - sumV;

                // Squared Euclidean distance is calculated without math.sqrt() to maintain high performance.
                long distance = distanceU * distanceU + distanceV * distanceV;

                // Group all points that share the exact same distance into a sublist under the same tree key.
                pointsByDistance
                        .computeIfAbsent(distance, key -> new ArrayList<>())
                        .add(new Point2D(u, v));

            }
        }

        // Flattens the sorted map structure back into a single ordered 1D list
        return GeometryMapping.flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    /**
     * Generates a center-based rectangle sorted by distance from its core.
     *
     * @param center          The central point.
     * @param halfWidth       The X/U radius.
     * @param halfHeight      The Y/V radius.
     * @param borderExclusive If true, excludes the outer edge.
     * @param shuffle         If true, shuffles equidistant blocks.
     * @param descOrder       If true, sorts from the outside in.
     * @return A sequentially sorted list of 2D points.
     */
    protected static List<Point2D> getSortedRectangle(
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
        return GeometryMapping.flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    /**
     * Generates points that reside strictly between an outer bounding rectangle and an inner cutout rectangle.
     * This creates hollow shapes like thick walls or square moats.
     *
     * @param point1               Corner 1 of the first rectangle.
     * @param point2               Opposite corner of the first rectangle.
     * @param point3               Corner 1 of the second rectangle.
     * @param point4               Opposite corner of the second rectangle.
     * @param innerBorderExclusive If true, treats the inner bounding box as smaller (expanding the hollow center).
     * @param outerBorderExclusive If true, treats the outer bounding box as smaller (shrinking the perimeter).
     * @param shuffle              If true, randomly shuffles the resulting list.
     * @return A list of points that exist between the two shapes.
     */
    protected static List<Point2D> getRectangleBetween(Point2D point1, Point2D point2, Point2D point3,
                                                     Point2D point4,  boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        // Dynamically compute the absolute global maxima to define the Outer Boundary Box
        int maxOuterU = Math.max(Math.max(point1.u(), point2.u()), Math.max(point3.u(), point4.u()));
        int minOuterU = Math.min(Math.min(point1.u(), point2.u()), Math.min(point3.u(), point4.u()));
        int maxOuterV = Math.max(Math.max(point1.v(), point2.v()), Math.max(point3.v(), point4.v()));
        int minOuterV = Math.min(Math.min(point1.v(), point2.v()), Math.min(point3.v(), point4.v()));

        // Dynamically compute the intermediate values to define the Inner Boundary Box (the hollow cutout)
        int maxInnerU = Math.min(Math.max(point1.u(), point2.u()), Math.max(point3.u(), point4.u()));
        int minInnerU = Math.max(Math.min(point1.u(), point2.u()), Math.min(point3.u(), point4.u()));
        int maxInnerV = Math.min(Math.max(point1.v(), point2.v()), Math.max(point3.v(), point4.v()));
        int minInnerV = Math.max(Math.min(point1.v(), point2.v()), Math.min(point3.v(), point4.v()));

        List<Point2D> pointResult = new ArrayList<>();

        // Iterate through the entire grid covered by the massive outer boundary box
        for (int u = minOuterU; u <= maxOuterU; u++) {
            for (int v = minOuterV; v <= maxOuterV; v++) {

                // Exclude the outermost perimeter if flagged
                if (outerBorderExclusive && (u == minOuterU || u == maxOuterU || v == minOuterV || v == maxOuterV)) {
                    continue;
                }

                // Cut out the inner rectangle. If inner is exclusive, we use >= <= to cut a larger hole.
                // If not exclusive, we use > < to cut a slightly smaller hole.
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

    /**
     * Generates a center-based hollow rectangle.
     *
     * @param center               The central point.
     * @param halfWidth1           X radius 1.
     * @param halfHeight1          Y radius 1.
     * @param halfWidth2           X radius 2.
     * @param halfHeight2          Y radius 2.
     * @param innerBorderExclusive Expand inner hole.
     * @param outerBorderExclusive Shrink outer border.
     * @param shuffle              Shuffle points.
     * @return A list of points forming the hollow shape.
     */
    protected static List<Point2D> getRectangleBetween(Point2D center, int halfWidth1, int halfHeight1, int halfWidth2,
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

    /**
     * Generates a distance-sorted hollow rectangle using four defining boundary points.
     *
     * @param point1               Corner 1 of the first rectangle.
     * @param point2               Opposite corner of the first rectangle.
     * @param point3               Corner 1 of the second rectangle.
     * @param point4               Opposite corner of the second rectangle.
     * @param innerBorderExclusive Expand inner hole.
     * @param outerBorderExclusive Shrink outer border.
     * @param shuffle              Shuffle items equidistant to center.
     * @param descOrder            Sort outside-in.
     * @return An ordered list of hollow shape points.
     */
    protected static List<Point2D> getSortedRectangleBetween(Point2D point1, Point2D point2, Point2D point3,
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
        return GeometryMapping.flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    /**
     * Generates a distance-sorted hollow rectangle radiating from a center.
     *
     * @param center               The center point.
     * @param halfWidth1           X radius 1.
     * @param halfHeight1          Y radius 1.
     * @param halfWidth2           X radius 2.
     * @param halfHeight2          Y radius 2.
     * @param innerBorderExclusive Expand inner hole.
     * @param outerBorderExclusive Shrink outer border.
     * @param shuffle              Shuffle equidistant items.
     * @param descOrder            Sort descending.
     * @return Ordered list of 2D points.
     */
    protected static List<Point2D> getSortedRectangleBetween(Point2D center, int halfWidth1, int halfHeight1,
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
        return GeometryMapping.flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

}
