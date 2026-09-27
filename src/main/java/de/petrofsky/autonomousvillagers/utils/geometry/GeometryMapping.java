package de.petrofsky.autonomousvillagers.utils.geometry;

import java.util.*;

/**
 * Internal helper that turns a distance-bucketed {@link TreeMap} of points into a
 * single, flat, distance-ordered list. Shared by the "sorted" query variants of
 * both {@link Circle} and {@link Rectangle} (e.g. {@link Circle#getSortedCircle}),
 * which group points by their (squared) distance from the shape's center into
 * {@code TreeMap<Long, List<Point2D>>} buckets before handing them off here.
 */
class GeometryMapping {

    /**
     * Flattens a distance-keyed map of point buckets into a single ordered list.
     *
     * <p>The {@link TreeMap} keeps its buckets sorted by distance (the map key)
     * automatically; this method only decides the iteration direction and,
     * if requested, randomizes the order of points that sit at the exact same
     * distance (points within one bucket are otherwise in arbitrary insertion
     * order, which would produce visible, non-random artifacts — e.g. always
     * picking the same diagonal first — if left untouched).</p>
     *
     * @param pointMap  points grouped by (squared) distance from the shape's center.
     * @param shuffle   if {@code true}, randomizes the order of points within each
     *                  individual distance bucket (does not change the overall
     *                  distance ordering between buckets).
     * @param descOrder if {@code true}, buckets are emitted from the largest
     *                  distance down to the smallest (outside-in); otherwise from
     *                  smallest to largest (inside-out, the natural
     *                  {@link TreeMap} order).
     * @return a single flat list of all points, ordered by distance.
     */
    protected static List<Point2D> flatTreeMapOfPoints(
            TreeMap<Long, List<Point2D>> pointMap, boolean shuffle, boolean descOrder) {
        Collection<List<Point2D>> valuesSorted = descOrder
                ? pointMap.descendingMap().values()
                : pointMap.values();
        // Pre-compute the total number of points so the result list can be sized
        // exactly up front (iteration order doesn't matter here, only the sum).
        int totalPoints = 0;
        for (List<Point2D> list : pointMap.values()) {
            totalPoints += list.size();
        }
        List<Point2D> pointResult = new ArrayList<>(totalPoints);
        for (List<Point2D> sameDistancePoints : valuesSorted) {
            if (shuffle) {
                // Shuffle in place within this one bucket only, so points that are
                // equally far from the center come out in random order while the
                // distance ordering between buckets is preserved.
                Collections.shuffle(sameDistancePoints);
            }
            pointResult.addAll(sameDistancePoints);
        }
        return pointResult;
    }
}
