package de.petrofsky.autonomousvillagers.utils.geometry;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Fluent builder for generating a rectangle (filled, hollow, or outline-only) of
 * {@link BlockPos} positions, defined by a center position plus a half-width and
 * half-height extending outward from it. Created via
 * {@link ShapeUtils#rectangle(BlockPos, int, int)}. See {@link BlockRectangle} for
 * the two-opposite-corners variant of the same idea, and {@link BlockCircle} for
 * the circular equivalent.
 */
public class BlockRectangleCentered {
    private Plane plane = Plane.XZ;
    private int thirdCoordinate = 0;
    private boolean innerBorderExclusive = false;
    private boolean outerBorderExclusive = false;
    private boolean shuffle = false;
    private boolean sorted = false;
    private boolean between = false;
    private SortType sortType;
    private final BlockPos center;
    private final int halfHeight1;
    private int halfHeight2;
    private final int halfWidth1;
    private int halfWidth2;

    protected BlockRectangleCentered(BlockPos center, int halfWidth, int halfHeight) {
        this.center = center;
        this.halfHeight1 = halfHeight;
        this.halfWidth1 = halfWidth;
    }

    /**
     * Selects the plane the rectangle is generated on, and the fixed value for the
     * one world axis that plane does not vary.
     *
     * @param plane           the plane to project onto (default {@link Plane#XZ}).
     * @param thirdCoordinate the constant value for the axis the plane holds fixed.
     * @return {@code this}, for method chaining.
     */
    public BlockRectangleCentered on(Plane plane, int thirdCoordinate) {
        this.plane = plane;
        this.thirdCoordinate = thirdCoordinate;
        return this;
    }

    /** Equivalent to {@code sorted(SortType.ASC)} — points closest to the center first. */
    public BlockRectangleCentered sorted() {
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
    public BlockRectangleCentered sorted(SortType sortType) {
        this.sorted = true;
        this.sortType = sortType;
        return this;
    }

    /** Excludes positions sitting on the outer edge. */
    public BlockRectangleCentered outerBorderExclusive() {
        this.outerBorderExclusive = true;
        return this;
    }

    /** Excludes positions sitting on the inner cutout's edge
     * (only relevant together with {@link #between(int, int)}). */
    public BlockRectangleCentered innerBorderExclusive() {
        this.innerBorderExclusive = true;
        return this;
    }

    /** Randomly shuffles the resulting positions (or, combined with
     * {@link #sorted()}, shuffles same-distance ties only). */
    public BlockRectangleCentered shuffle() {
        this.shuffle = true;
        return this;
    }

    /**
     * Turns the rectangle into a hollow shape (e.g. a thick square wall or a
     * moat): only positions inside the outer rectangle (defined by the
     * constructor's {@code halfWidth}/{@code halfHeight}) but outside the inner
     * rectangle defined by this call's half-dimensions are returned.
     *
     * @param halfWidth2  the second bounding half-width.
     * @param halfHeight2 the second bounding half-height.
     * @return {@code this}, for method chaining.
     */
    public BlockRectangleCentered between(int halfWidth2, int halfHeight2) {
        this.halfWidth2 = halfWidth2;
        this.halfHeight2 = halfHeight2;
        this.between = true;
        return this;
    }


    /**
     * Runs the configured search and returns the resulting positions.
     *
     * @return the list of {@link BlockPos} positions matching this rectangle's configuration.
     */
    public List<BlockPos> toList() {
        Point2D center = this.plane.fromBlockPos(this.center);

        List<Point2D> result;
        if(this.between) {
            if(this.sorted) {
                result = Rectangle.getSortedRectangleBetween(center,
                        this.halfWidth1, this.halfHeight1, this.halfWidth2, this.halfHeight2,
                        this.innerBorderExclusive, this.outerBorderExclusive, this.shuffle,
                        this.sortType == SortType.DESC);
            } else {
                result = Rectangle.getRectangleBetween(center,
                        this.halfWidth1, this.halfHeight1, this.halfWidth2, this.halfHeight2,
                        this.innerBorderExclusive, this.outerBorderExclusive, this.shuffle);
            }
        } else {
            if(this.sorted) {
                result = Rectangle.getSortedRectangle(center,
                        this.halfWidth1, this.halfHeight1, this.outerBorderExclusive,
                        this.shuffle, this.sortType == SortType.DESC);
            } else {
                result = Rectangle.getRectangle(center, this.halfWidth1, this.halfHeight1,
                        this.outerBorderExclusive, this.shuffle);
            }
        }


        // Project every 2D result point back into a 3D world position, reusing
        // the plane's constant "third coordinate" for all of them.
        return result.stream().map(point2D -> this.plane
                .toBlockPos(point2D, this.thirdCoordinate)).toList();
    }
}
