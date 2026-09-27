package de.petrofsky.autonomousvillagers.utils.geometry;

import net.minecraft.core.BlockPos;

/**
 * Static entry point for this package's shape builders. Typically used with a
 * static import, e.g. {@code ShapeUtils.rectangle(pos1, pos2).sorted().toList()}.
 *
 * @see BlockRectangle
 * @see BlockRectangleCentered
 * @see BlockCircle
 */
public class ShapeUtils {

    /**
     * Starts building a rectangle defined by two opposite corner positions.
     *
     * @param pos1 one corner of the rectangle.
     * @param pos2 the opposite corner of the rectangle.
     * @return a new {@link BlockRectangle} builder.
     */
    public static BlockRectangle rectangle(BlockPos pos1, BlockPos pos2) {
        return new BlockRectangle(pos1, pos2);
    }

    /**
     * Starts building a rectangle defined by a center position and half-dimensions
     * extending outward from it.
     *
     * @param center     the center of the rectangle.
     * @param halfWidth  distance from the center to the left/right edges.
     * @param halfHeight distance from the center to the top/bottom edges.
     * @return a new {@link BlockRectangleCentered} builder.
     */
    public static BlockRectangleCentered rectangle(BlockPos center, int halfWidth, int halfHeight) {
        return new BlockRectangleCentered(center, halfWidth, halfHeight);
    }

    /**
     * Starts building a circle (or ring) around a center position.
     *
     * @param center the center of the circle.
     * @param radius the circle's radius.
     * @return a new {@link BlockCircle} builder.
     */
    public static BlockCircle circle(BlockPos center, int radius) {
        return new BlockCircle(center, radius);
    }
}
