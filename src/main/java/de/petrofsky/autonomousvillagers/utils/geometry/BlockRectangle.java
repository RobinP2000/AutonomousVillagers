package de.petrofsky.autonomousvillagers.utils.geometry;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Fluent builder for generating a rectangle (filled, hollow, or outline-only) of
 * {@link BlockPos} positions, defined by two opposite corner positions. Created
 * via {@link ShapeUtils#rectangle(BlockPos, BlockPos)}; configure it with the
 * {@code on(...)}, {@code sorted()}, {@code between(...)}, etc. methods below,
 * then call {@link #toList()} to run the search.
 *
 * <p>Like {@link BlockCircle}, this is a thin 3D wrapper around the plane-agnostic
 * 2D rectangle math in {@link Rectangle}: {@link #toList()} projects both corners
 * down onto the chosen {@link Plane} (default {@link Plane#XZ}), delegates to the
 * matching {@link Rectangle} method, then projects the resulting 2D points back
 * into 3D. See {@link BlockRectangleCentered} for the center + half-width/height
 * variant of the same idea.</p>
 */
public class BlockRectangle {
    private Plane plane = Plane.XZ;
    private int thirdCoordinate = 0;
    private boolean innerBorderExclusive = false;
    private boolean outerBorderExclusive = false;
    private boolean between = false;
    private boolean shuffle = false;
    private boolean sorted = false;
    private boolean edgeOnly = false;
    private SortType sortType;
    private final BlockPos pos1;
    private final BlockPos pos2;
    private BlockPos pos3;
    private BlockPos pos4;

    protected BlockRectangle(BlockPos pos1, BlockPos pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    /**
     * Selects the plane the rectangle is generated on, and the fixed value for the
     * one world axis that plane does not vary.
     *
     * @param plane           the plane to project onto (default {@link Plane#XZ}).
     * @param thirdCoordinate the constant value for the axis the plane holds fixed.
     * @return {@code this}, for method chaining.
     */
    public BlockRectangle on(Plane plane, int thirdCoordinate) {
        this.plane = plane;
        this.thirdCoordinate = thirdCoordinate;
        return this;
    }

    /** Equivalent to {@code sorted(SortType.ASC)} — points closest to the center first. */
    public BlockRectangle sorted() {
        return sorted(SortType.ASC);
    }

    /**
     * Sorts the resulting positions by distance from the rectangle's center
     * instead of leaving them in scan order.
     *
     * @param sortType {@link SortType#ASC} for closest-first, {@link SortType#DESC}
     *                 for farthest-first.
     * @return {@code this}, for method chaining.
     */
    public BlockRectangle sorted(SortType sortType) {
        this.sorted = true;
        this.sortType = sortType;
        return this;
    }

    /** Excludes positions sitting on the outer edge. */
    public BlockRectangle outerBorderExclusive() {
        this.outerBorderExclusive = true;
        return this;
    }

    /** Excludes positions sitting on the inner cutout's edge (only relevant
     * together with {@link #between(BlockPos, BlockPos)}). */
    public BlockRectangle innerBorderExclusive() {
        this.innerBorderExclusive = true;
        return this;
    }

    /** Randomly shuffles the resulting positions (or, combined with
     * {@link #sorted()}, shuffles same-distance ties only). */
    public BlockRectangle shuffle() {
        this.shuffle = true;
        return this;
    }

    /** Restricts the result to only the one-block-thick outline of the rectangle, instead of a filled area. */
    public BlockRectangle edgeOnly() {
        this.edgeOnly = true;
        return this;
    }

    /**
     * Turns the rectangle into a hollow shape (e.g. a thick wall or a square
     * moat): only positions inside the outer rectangle (defined by the
     * constructor's {@code pos1}/{@code pos2}) but outside the inner rectangle
     * defined by {@code pos3}/{@code pos4} are returned.
     *
     * @param pos3 one corner of the inner cutout rectangle.
     * @param pos4 the opposite corner of the inner cutout rectangle.
     * @return {@code this}, for method chaining.
     */
    public BlockRectangle between(BlockPos pos3, BlockPos pos4) {
        this.pos3 = pos3;
        this.pos4 = pos4;
        this.between = true;
        return this;
    }

    /**
     * Runs the configured search and returns the resulting positions.
     *
     * @return the list of {@link BlockPos} positions matching this rectangle's configuration.
     */
    public List<BlockPos> toList() {
        Point2D pos1 = this.plane.fromBlockPos(this.pos1);
        Point2D pos2 = this.plane.fromBlockPos(this.pos2);

        List<Point2D> result;
        if(this.edgeOnly) {
            // edgeOnly takes precedence over between/sorted: an outline doesn't
            // need a separate "hollow" or "sorted" variant.
            result = Rectangle.getRectangleBorder(pos1, pos2);
        } else if(this.between) {
            Point2D pos3 = this.plane.fromBlockPos(this.pos3);
            Point2D pos4 = this.plane.fromBlockPos(this.pos4);

            if(this.sorted) {
                result = Rectangle.getSortedRectangleBetween(pos1, pos2, pos3, pos4,
                        this.innerBorderExclusive, this.outerBorderExclusive, this.shuffle,
                        this.sortType == SortType.DESC);
            } else {
                result = Rectangle.getRectangleBetween(pos1, pos2, pos3, pos4,
                        this.innerBorderExclusive, this.outerBorderExclusive, this.shuffle);
            }
        } else {
            if(this.sorted) {
                result = Rectangle.getSortedRectangle(pos1, pos2, this.outerBorderExclusive,
                        this.shuffle, this.sortType == SortType.DESC);
            } else {
                result = Rectangle.getRectangle(pos1, pos2, this.outerBorderExclusive, this.shuffle);
            }
        }

        // Project every 2D result point back into a 3D world position, reusing
        // the plane's constant "third coordinate" for all of them.
        return result.stream().map(point2D -> this.plane
                .toBlockPos(point2D, this.thirdCoordinate)).toList();
    }
}
