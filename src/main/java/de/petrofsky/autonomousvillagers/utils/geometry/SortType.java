package de.petrofsky.autonomousvillagers.utils.geometry;

/**
 * Sort direction for the distance-sorted shape queries in this package (e.g.
 * {@link Circle#getSortedCircle} or {@link Rectangle#getSortedRectangle}).
 *
 * <p>{@code ASC} orders points from closest to farthest from the shape's center
 * (useful for effects that grow outward from the middle, e.g. a spreading fire or
 * a filling animation), while {@code DESC} orders them from farthest to closest
 * (e.g. for effects that converge inward toward the center).</p>
 */
public enum SortType {
    /** Farthest from the center first, closest last. */
    DESC,
    /** Closest to the center first, farthest last. */
    ASC;
}
