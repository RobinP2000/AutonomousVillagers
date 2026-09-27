package de.petrofsky.autonomousvillagers.utils.geometry;

import net.minecraft.core.BlockPos;

/**
 * One of the three axis-aligned planes a flat 2D shape (see {@link Point2D},
 * {@link Circle}, {@link Rectangle}) can be projected onto in 3D Minecraft world
 * space, plus the inverse projection back to 2D.
 *
 * <p>Every plane fixes exactly one world axis to a constant value and maps the
 * generic {@link Point2D} axes {@code u} and {@code v} onto the two remaining,
 * varying world axes:</p>
 * <ul>
 *   <li>{@link #XZ} — the horizontal ground plane. {@code u} maps to X, {@code v}
 *   maps to Z, and the constant is the Y (height) coordinate. This is the default
 *   plane used by {@link BlockCircle} and {@link BlockRectangle}, since most
 *   shapes (fields, floors, clearings) are laid out flat on the ground.</li>
 *   <li>{@link #XY} — a vertical plane. {@code u} maps to X, {@code v} maps to Y
 *   (height), and the constant is the Z coordinate. Produces a wall-like shape
 *   running east-west, at a fixed north/south position.</li>
 *   <li>{@link #YZ} — a vertical plane. {@code u} maps to Y (height), {@code v}
 *   maps to Z, and the constant is the X coordinate. Produces a wall-like shape
 *   running north-south, at a fixed east/west position.</li>
 * </ul>
 *
 * <p>Implemented with the classic Java "constant-specific method body" pattern:
 * each enum constant supplies its own implementation of the two abstract methods
 * below, instead of a single method switching on the enum value.</p>
 */
public enum Plane {

    /** Horizontal ground plane: {@code u}/{@code v} map to X/Z, constant is Y (height). */
    XZ { public BlockPos toBlockPos(Point2D p, int c) { return new BlockPos(p.u(), c, p.v()); }
        public Point2D fromBlockPos(BlockPos b) { return new Point2D(b.getX(), b.getZ()); } },

    /** Vertical plane running east-west: {@code u}/{@code v} map to X/Y, constant is Z. */
    XY { public BlockPos toBlockPos(Point2D p, int c) { return new BlockPos(p.u(), p.v(), c); }
        public Point2D fromBlockPos(BlockPos b) { return new Point2D(b.getX(), b.getY()); } },

    /** Vertical plane running north-south: {@code u}/{@code v} map to Y/Z, constant is X. */
    YZ { public BlockPos toBlockPos(Point2D p, int c) { return new BlockPos(c, p.u(), p.v()); }
        public Point2D fromBlockPos(BlockPos b) { return new Point2D(b.getY(), b.getZ()); } };

    /**
     * Projects a flat 2D point into 3D world space on this plane.
     *
     * @param point    the 2D point to project ({@code u}/{@code v} coordinates).
     * @param constant the fixed value for whichever world axis this plane does not
     *                 vary (e.g. the height, for {@link #XZ}).
     * @return the corresponding 3D world position.
     */
    public abstract BlockPos toBlockPos(Point2D point, int constant);

    /**
     * Projects a 3D world position down onto this plane, discarding the axis this
     * plane holds constant.
     *
     * @param pos the 3D world position to project.
     * @return the corresponding flat 2D point ({@code u}/{@code v} coordinates).
     */
    public abstract Point2D fromBlockPos(BlockPos pos);
}
