package de.petrofsky.autonomousvillagers.utils.geometry;

/**
 * An immutable 2D point in an abstract, plane-agnostic coordinate system.
 *
 * <p>The axis names {@code u} and {@code v} are deliberately generic rather than
 * "x"/"y": every shape-generation algorithm in this package ({@link Circle},
 * {@link Rectangle}) works purely in this flat 2D {@code (u, v)} space. It is only
 * {@link Plane} that decides, at the very end, how {@code u} and {@code v} map onto
 * two of the three real Minecraft world axes (X, Y, Z), with the third axis held
 * constant. That separation lets the same circle/rectangle math be reused for a
 * flat area on the ground (the XZ plane) just as well as for a vertical wall
 * pattern (the XY or YZ plane) — see {@link Plane} for the full mapping.</p>
 *
 * @param u first coordinate (maps to a world axis depending on the chosen {@link Plane}).
 * @param v second coordinate (maps to a world axis depending on the chosen {@link Plane}).
 */
public record Point2D(int u, int v) {}
