package de.petrofsky.autonomousvillagers.utils.geometry;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Fluent builder for generating a circle (or ring) of {@link BlockPos} positions
 * around a center block. Created via {@link ShapeUtils#circle(BlockPos, int)};
 * configure it with the {@code on(...)}, {@code sorted()}, {@code between(...)},
 * etc. methods below, then call {@link #toList()} to actually run the search and
 * get the resulting positions.
 *
 * <p>Internally this is a thin 3D wrapper around the plane-agnostic 2D circle math
 * in {@link Circle}: {@link #toList()} projects the center down onto the chosen
 * {@link Plane} (default {@link Plane#XZ}, i.e. a flat circle on the ground),
 * delegates to the matching {@link Circle} method, then projects every resulting
 * 2D point back up into a 3D {@link BlockPos} using the same plane and the fixed
 * "third coordinate" (e.g. the Y height, for the default XZ plane).</p>
 */
public class BlockCircle {

    private Plane plane = Plane.XZ;
    private int thirdCoordinate = 0;
    private boolean innerBorderExclusive = false;
    private boolean outerBorderExclusive = false;
    private boolean shuffle = false;
    private boolean sorted = false;
    private boolean edgeOnly = false;
    private boolean between = false;
    private SortType sortType;
    private final BlockPos center;
    private final int radius1;
    private int radius2;

    protected BlockCircle(BlockPos center, int radius) {
        this.center = center;
        this.radius1 = radius;
    }


    /**
     * Selects the plane the circle is generated on, and the fixed value for the
     * one world axis that plane does not vary.
     *
     * @param plane           the plane to project onto (default {@link Plane#XZ}).
     * @param thirdCoordinate the constant value for the axis the plane holds fixed
     *                        (e.g. the Y height for {@link Plane#XZ}).
     * @return {@code this}, for method chaining.
     */
    public BlockCircle on(Plane plane, int thirdCoordinate) {
        this.plane = plane;
        this.thirdCoordinate = thirdCoordinate;
        return this;
    }

    /** Equivalent to {@code sorted(SortType.ASC)} — closest points first. */
    public BlockCircle sorted() {
        return sorted(SortType.ASC);
    }

    /**
     * Sorts the resulting positions by distance from the center instead of
     * leaving them in scan order.
     *
     * @param sortType {@link SortType#ASC} for closest-first, {@link SortType#DESC}
     *                 for farthest-first.
     * @return {@code this}, for method chaining.
     */
    public BlockCircle sorted(SortType sortType) {
        this.sorted = true;
        this.sortType = sortType;
        return this;
    }


    /** Excludes positions sitting exactly on the outer radius. */
    public BlockCircle outerBorderExclusive() {
        this.outerBorderExclusive = true;
        return this;
    }

    /** Excludes positions sitting exactly on the inner radius (only relevant
     * together with {@link #between(int)}). */
    public BlockCircle innerBorderExclusive() {
        this.innerBorderExclusive = true;
        return this;
    }

    /** Randomly shuffles the resulting positions (or, combined with {@link #sorted()},
     * shuffles same-distance ties only). */
    public BlockCircle shuffle() {
        this.shuffle = true;
        return this;
    }

    /** Restricts the result to only the one-block-thick outline of the circle, instead of a filled disk. */
    public BlockCircle edgeOnly() {
        this.edgeOnly = true;
        return this;
    }

    /**
     * Turns the circle into a ring (annulus): only positions between the radius
     * given in the constructor and {@code radius2} are returned (whichever of the
     * two is smaller becomes the inner radius).
     *
     * @param radius2 the second bounding radius.
     * @return {@code this}, for method chaining.
     */
    public BlockCircle between(int radius2) {
        this.radius2 = radius2;
        this.between = true;
        return this;
    }

    /**
     * Runs the configured search and returns the resulting positions.
     *
     * @return the list of {@link BlockPos} positions matching this circle's configuration.
     */
    public List<BlockPos> toList() {
        Point2D center = this.plane.fromBlockPos(this.center);
        int radius1 = this.radius1;
        List<Point2D> result;
        if(this.edgeOnly) {
            // edgeOnly takes precedence over between/sorted: an outline doesn't
            // need a separate "ring" or "sorted" variant.
            result = Circle.getCircleBorder(center, radius1);
        } else if(this.between) {
            int radius2 = this.radius2;

            if(this.sorted) {
                result = Circle.getSortedCircleBetween(center, radius1, radius2,
                        this.innerBorderExclusive, this.outerBorderExclusive, this.shuffle,
                        this.sortType == SortType.DESC);
            } else {
                result = Circle.getCircleBetween(center, radius1, radius2,
                        this.innerBorderExclusive, this.outerBorderExclusive, this.shuffle);
            }
        } else {
            if(this.sorted) {
                result = Circle.getSortedCircle(center, radius1, this.outerBorderExclusive,
                        this.shuffle, this.sortType == SortType.DESC);
            } else {
                result = Circle.getCircle(center, radius1, this.outerBorderExclusive, this.shuffle);
            }
        }

        // Project every 2D result point back into a 3D world position, reusing
        // the plane's constant "third coordinate" for all of them.
        return result.stream().map(point2D -> this.plane
                .toBlockPos(point2D, this.thirdCoordinate)).toList();
    }
}
