package de.petrofsky.autonomousvillagers.utils.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Pure 2D circle/disk point generation, working entirely in the abstract
 * {@link Point2D} {@code (u, v)} coordinate system (no Minecraft types involved).
 * All methods brute-force a square bounding box around the center and keep only
 * the points that satisfy the relevant squared-distance condition — simple, and
 * fast enough for the small radii typically used for in-game shapes.
 *
 * <p>This class is package-private; the public entry point is
 * {@link ShapeUtils#circle(net.minecraft.core.BlockPos, int)} and the
 * {@link BlockCircle} builder it returns, which additionally projects these 2D
 * points into 3D world space via a {@link Plane}.</p>
 */
class Circle {

    /**
     * Generates every point inside a filled disk of the given radius around the
     * center.
     *
     * @param center          center of the disk.
     * @param radius          radius of the disk, in grid units.
     * @param borderExclusive if {@code true}, points exactly on the boundary
     *                        (squared distance equal to {@code radius * radius})
     *                        are omitted, leaving a disk with an open edge.
     * @param shuffle         if {@code true}, the resulting list is randomly shuffled.
     * @return all points within the disk.
     */
    protected static List<Point2D> getCircle(Point2D center, int radius, boolean borderExclusive, boolean shuffle) {
        // Note: computed with int arithmetic that's only widened to double at the
        // very end, unlike the long-based radiusSquared in the sorted/ring variants
        // below. Harmless for the small radii this class is meant for (village-AI
        // scale, not thousands of blocks), but a radius beyond roughly 46,000 would
        // silently overflow before ever reaching this double.
        double radiusSquared = radius * radius;
        List<Point2D> pointResult = new ArrayList<>();

        // Brute-force scan of the square bounding box; keep only points whose
        // squared distance from the center falls within the (inclusive or
        // exclusive) radius.
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

    /**
     * Generates only the one-unit-thick outline of a circle (not a filled disk),
     * i.e. the points that lie closest to the mathematically exact circle of the
     * given radius.
     *
     * <p>Since a discrete grid has no points sitting exactly on a continuous
     * circle in the general case, this uses the standard trick for rasterizing a
     * circle's outline: keep every integer point whose squared distance from the
     * center falls within {@code [(radius - 0.5)^2, (radius + 0.5)^2]} — i.e.
     * within half a unit of the true radius. That produces a clean, roughly
     * one-point-wide ring instead of either a solid disk or gaps.</p>
     *
     * @param center center of the circle.
     * @param radius radius of the outline.
     * @return the points forming the circle's outline.
     */
    protected static List<Point2D> getCircleBorder(Point2D center, int radius) {
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

    /**
     * Generates every point inside a filled disk, sorted by distance from the
     * center. Useful for progressive/animated effects that grow outward from (or
     * shrink inward toward) the center, e.g. an expanding shockwave or a spreading
     * clearing.
     *
     * <p>Points are grouped into a {@link TreeMap} keyed by their exact squared
     * distance, so all points at the same distance end up in the same "ring"
     * bucket; {@link GeometryMapping#flatTreeMapOfPoints} then flattens those
     * buckets, in distance order, into the final list.</p>
     *
     * @param center          center of the disk.
     * @param radius          radius of the disk.
     * @param borderExclusive if {@code true}, points exactly on the boundary are
     *                        omitted.
     * @param shuffle         if {@code true}, points that share the exact same
     *                        distance are shuffled among themselves (the overall
     *                        distance ordering is preserved).
     * @param descOrder       if {@code true}, sorts from farthest to closest
     *                        (outside-in); otherwise closest to farthest (inside-out).
     * @return all points within the disk, ordered by distance from the center.
     */
    protected static List<Point2D> getSortedCircle(
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

        return GeometryMapping.flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }

    /**
     * Generates every point that lies within a ring (annulus) between two radii —
     * i.e. inside the outer circle but outside the inner circle. Useful for
     * hollow shapes such as a moat or a ring-shaped clearing.
     *
     * @param center               center of the ring.
     * @param radius1              one of the two bounding radii (order doesn't
     *                             matter — the smaller becomes the inner radius,
     *                             the larger the outer radius).
     * @param radius2              the other bounding radius.
     * @param innerBorderExclusive if {@code true}, points exactly on the inner
     *                             boundary are excluded (shrinks the hole).
     * @param outerBorderExclusive if {@code true}, points exactly on the outer
     *                             boundary are excluded (shrinks the ring inward).
     * @param shuffle              if {@code true}, the resulting list is randomly
     *                             shuffled.
     * @return the points forming the ring between the two radii.
     */
    protected static List<Point2D> getCircleBetween(Point2D center, int radius1, int radius2,
                                                  boolean innerBorderExclusive, boolean outerBorderExclusive, boolean shuffle) {
        int innerRadius = Math.min(radius1, radius2);
        int outerRadius = Math.max(radius1, radius2);
        long innerRadiusSquared = (long) innerRadius * innerRadius;
        long outerRadiusSquared = (long) outerRadius * outerRadius;
        List<Point2D> pointResult = new ArrayList<>();

        // Scan the bounding box of the OUTER circle only; every point is then
        // tested against both the inner and outer boundary in one combined
        // condition (see below) instead of computing two separate point sets and
        // subtracting them.
        for (int u = -outerRadius; u <= outerRadius; u++) {
            for (int v = -outerRadius; v <= outerRadius; v++) {
                long distance = ((long) u * u) + ((long) v * v);

                // Four separate exclusion conditions, OR'd together: skip the
                // point if it falls outside the outer boundary (respecting
                // outerBorderExclusive) OR inside the inner boundary (respecting
                // innerBorderExclusive). What survives is exactly the ring.
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

    /**
     * Like {@link #getCircleBetween}, but returns the ring's points sorted by
     * distance from the center instead of in scan order.
     *
     * @param center               center of the ring.
     * @param radius1              one of the two bounding radii (order doesn't matter).
     * @param radius2              the other bounding radius.
     * @param innerBorderExclusive if {@code true}, excludes the inner boundary.
     * @param outerBorderExclusive if {@code true}, excludes the outer boundary.
     * @param shuffle              if {@code true}, shuffles points that share the
     *                             exact same distance.
     * @param descOrder            if {@code true}, sorts outside-in; otherwise
     *                             inside-out.
     * @return the ring's points, ordered by distance from the center.
     */
    protected static List<Point2D> getSortedCircleBetween(Point2D center, int radius1, int radius2,
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
        return GeometryMapping.flatTreeMapOfPoints(pointsByDistance, shuffle, descOrder);
    }
}
