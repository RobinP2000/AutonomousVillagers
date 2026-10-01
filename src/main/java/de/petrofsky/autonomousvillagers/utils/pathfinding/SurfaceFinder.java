package de.petrofsky.autonomousvillagers.utils.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.*;

/**
 * {@code SurfaceFinder} searches the world ({@link Level}) around an origin position
 * ({@code origin}) for walkable, free surface positions (solid ground below, one air
 * block at the position itself, another free block of headroom above) — or, if
 * configured via {@link #find(Block)} / {@link #find(TagKey)}, for occurrences of a
 * specific block or block tag (e.g. to be mined).
 *
 * <p><b>Important:</b> "surface" does not necessarily mean the highest, sky-facing
 * terrain surface. The algorithm has no concept of a global world surface; it only
 * searches locally, starting from {@code origin}. It therefore works just as well
 * inside a cave, inside a building, or on a plateau — anywhere the local pattern
 * "ground – air – air" holds.</p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Starting from {@code origin}, a 3D breadth-first search (flood fill over the
 *   26 neighbours of a 3x3x3 cube) is performed.</li>
 *   <li>Per (X,Z) column, only a single Y height is ever remembered as "visited"
 *   (see {@link #visitedOffsetXZ}) — the algorithm implicitly assumes there is only
 *   one relevant walkable height per column (whichever the BFS reaches first). This
 *   keeps the result grid two-dimensional even though the search itself moves
 *   freely in 3D (e.g. around a cliff or up/down a staircase).</li>
 *   <li>For every neighbour position it is checked whether it is walkable (solid
 *   ground, no collision at the position itself, one block of headroom) and whether
 *   it satisfies the optional filters ({@link #ground(Block)}, {@link #blocked(Block)},
 *   {@link #between(BlockPos, int, int)}, {@link #minOriginDistance(int)}).</li>
 *   <li>Optionally, instead of walkable surfaces, the search can look for a specific
 *   block or block tag ({@link #find(Block)} / {@link #find(TagKey)}); in that mode
 *   the positions of the target block itself are returned as results (e.g. for a
 *   mining AI), rather than walkable neighbour positions.</li>
 *   <li>{@link #scan(boolean)} runs the search to completion and returns either all
 *   found positions, or — for {@code singleRandom == true} — a single, randomly
 *   picked position (without having to copy every hit into a separate list first).</li>
 * </ol>
 *
 * <h2>Performance</h2>
 * <p>This class is optimized for very frequent calls and even works without lagging
 * at 60 calls per tick:</p>
 * <ul>
 *   <li>All working arrays ({@link #QUEUE_CACHE}, {@link #VISITED_CACHE},
 *   {@link #BLOCKED_CACHE}) are allocated once per thread and reused/reset from
 *   then on, avoiding GC pressure from constantly re-allocating large arrays.</li>
 *   <li>Positions are kept in the queue not as {@link BlockPos} objects but as
 *   compactly packed {@code long} values ({@link BlockPos#asLong}).</li>
 *   <li>{@link #fastGetState(int, int, int)} caches the last chunk used, since most
 *   consecutive lookups in a local search fall within the same chunk, letting the
 *   (comparatively expensive) chunk lookup be skipped most of the time.</li>
 *   <li>There is only a single, reused {@link BlockPos.MutableBlockPos}
 *   ({@link #currentPos}) for all block lookups — this means practically no object
 *   allocation happens during the search itself.</li>
 * </ul>
 *
 * <h2>Limits &amp; assumptions</h2>
 * <ul>
 *   <li>The static thread caches are fixed at size 4000. Since
 *   {@code maxPositions = (2 * totalRadius + 1)²} must not exceed 4000, the maximum
 *   supported {@code totalRadius} is 31 (63² = 3969, 64² = 4096). A larger radius
 *   results in an {@link ArrayIndexOutOfBoundsException}.</li>
 *   <li>A {@code SurfaceFinder} instance is not meant to be used concurrently by
 *   multiple threads (the shared {@link #currentPos} and chunk-cache fields are
 *   mutated). Since the working arrays are {@code ThreadLocal}, though, every thread
 *   has its own buffers anyway — parallel searches from different threads are fine,
 *   as long as the same instance isn't shared between them.</li>
 *   <li>The sentinel values {@link #EMPTY_VALUE} (-100) and {@link #UNAVAILABLE_VALUE}
 *   (-101) assume real world Y coordinates are always greater than -100. That holds
 *   for all vanilla Minecraft dimensions (Overworld starts at Y = -64).</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Inner result radius of 8, outer traversal radius of 12 blocks, only standing on
 * // grass, at least 2 blocks away from water.
 * List<BlockPos> freePositions = new SurfaceFinder(level, origin, 8, 12)
 *         .ground(Blocks.GRASS_BLOCK)
 *         .blocked(Blocks.WATER)
 *         .distanceToBlocked(2)
 *         .scan(false);
 * }</pre>
 */
public class SurfaceFinder {

    /**
     * Reusable buffer for the BFS queue (allocated once per thread, see the class
     * comment "Performance"). Each entry is a position packed with
     * {@link BlockPos#asLong(int, int, int)}.
     * The fixed size of 4000 implicitly caps the maximum supported search radius.
     */
    private static final ThreadLocal<long[]> QUEUE_CACHE = ThreadLocal.withInitial(() -> new long[4000]);

    /**
     * Reusable buffer: stores, per (X,Z) column (relative to the origin, index via
     * {@link #getIndex(int, int)}), the Y height found there, or one of the sentinel
     * values {@link #EMPTY_VALUE} / {@link #UNAVAILABLE_VALUE}.
     */
    private static final ThreadLocal<int[]> VISITED_CACHE = ThreadLocal.withInitial(() -> new int[4000]);

    /**
     * Reusable buffer: marks, per (X,Z) column, whether it lies within the block
     * distance ({@link #distanceBlocked}) of a block defined as "blocked" (see
     * {@link #blocked(Block)}, {@link #markBlocked(int, int)}).
     */
    private static final ThreadLocal<boolean[]> BLOCKED_CACHE = ThreadLocal.withInitial(() -> new boolean[4000]);

    /**
     * Cache of the circular offset masks used by {@link #markBlocked(int, int)} to
     * mark the exclusion zone around a blocking block. Keyed by the block distance
     * ({@link #distanceBlocked}). Since the mask only depends on the distance (not
     * on the actual position), it is reused statically across all instances and
     * calls instead of being recomputed every time.
     */
    private static final Map<Integer, int[]> BLOCKED_MASKS = new HashMap<>();

    /** Sentinel value in {@link #visitedOffsetXZ}: this column has not been visited yet. */
    private static final int EMPTY_VALUE = -100;

    /**
     * Sentinel value in {@link #visitedOffsetXZ}: this column has been visited (and
     * is thus "used up" for the BFS), but does not count as a valid hit — e.g.
     * because it is blocked, lies outside the inner search radius, fails the
     * "between" filter, or (in find mode) is simply a walkable pass-through position
     * without a target block.
     */
    private static final int UNAVAILABLE_VALUE = -101;

    /** The world/dimension the search is performed in. */
    private final Level level;

    // ---- "between" filter: a valid result must lie within a ring
    // [distance1, distance2] around a second reference position (see
    // between(BlockPos, int, int)). Independent of the origin. ----
    private int betweenX, betweenZ, betweenMinSquared, betweenMaxSquared;
    private boolean checkBetween = false;

    // ---- "ground" filter: allowed ground blocks (what a valid position may stand
    // on). As long as checkGround == false (no call to ground(...)), any solid
    // ground is allowed. ----
    private Block[] ground = new Block[1];
    private int groundCount = 0;
    private boolean checkGround = false;

    // ---- "find" mode (block variant): concrete blocks whose occurrences are being
    // searched for (instead of walkable surfaces), see find(Block). ----
    private Block[] blockTargets = new Block[1];
    private int blockCount = 0;

    // ---- Chunk cache for fastGetState(int, int, int): avoids repeated chunk
    // lookups as long as consecutive queries stay within the same chunk. ----
    private ChunkAccess lastChunk;
    private int lastChunkX = Integer.MAX_VALUE;
    private int lastChunkZ = Integer.MAX_VALUE;


    // ---- "find" mode (tag variant): block tags whose occurrences are being
    // searched for, see find(TagKey). ----
    @SuppressWarnings("unchecked")
    private TagKey<Block>[] tagTargets = new TagKey[1];
    private int tagCount = 0;

    // ---- Controls "find" mode as a whole: instead of walkable neighbour
    // positions, positions of the given target block/tag itself are returned as
    // hits (e.g. to hand off to a mining AI). ----
    private boolean checkFound = false;
    private TagKey<Block> findTag;
    private Block findBlock;

    // ---- "blocked" filter: blocks/tags that count as obstacles, plus their block
    // distance (buffer zone around the obstacle that is also marked blocked), see
    // blocked(...) and distanceToBlocked(int). ----
    private int distanceBlocked = 2;
    private boolean checkBlocking = false;

    // ---- Origin of the search, plus the minimum distance from it (default: 1,
    // i.e. the origin position itself is never returned as a hit), see
    // minOriginDistance(int). ----
    private final int originX, originZ;
    private long minOriginDistanceSquared = 1;

    // ---- Radii: "radius" (and radiusSquared) is the outer, hard boundary up to
    // which the BFS is allowed to traverse at all (this also determines the size of
    // the working arrays). "searchRadiusSquared" is the inner radius within which
    // found positions actually count as hits. The gap between the two lets the
    // algorithm route around obstacles further out while still reaching positions
    // inside the inner area. ----
    private final int radius;
    private final int radiusSquared;
    private final int searchRadiusSquared;

    // ---- Side length (maxRange) and total cell count (maxPositions) of the square
    // grid enclosing the search area around the origin (side length
    // 2 * radius + 1, centered on the origin). ----
    private final int maxRange;
    private final int maxPositions;

    // ---- BFS queue: array-based queue. queue[queueTail..queueHead) are the
    // positions still to be processed (as packed longs). queueTail is the read
    // pointer, queueHead the write pointer/current size. ----
    private final long[] queue;
    private int queueTail = 0;
    private int queueHead = 1;

    // ---- Result/visited grid (see VISITED_CACHE / BLOCKED_CACHE), plus the number
    // of hits counted so far during the search (may include positions later
    // blocked retroactively, see scan()). ----
    private final int[] visitedOffsetXZ;
    private final boolean[] blocked;
    private int resultLength = 0;

    @SuppressWarnings("unchecked")
    private TagKey<Block>[] breakableTags = new TagKey[1];
    private int breakableTagCount = 0;
    private boolean hasBreakableBlocks = false;

    // ---- The single, reused MutableBlockPos used for all block lookups (avoids
    // object allocation during the search). ----
    private final BlockPos.MutableBlockPos currentPos;

    /**
     * Creates a new {@code SurfaceFinder} for a search starting at {@code origin}.
     * The constructor only initializes the working arrays and places the origin
     * position as the first (already present) entry of the queue; the actual search
     * only starts with {@link #scan(boolean)}.
     *
     * @param level        world to search in.
     * @param origin       starting position of the search (e.g. a villager's current
     *                     position). The origin position itself is NOT returned as a
     *                     hit by default (see {@link #minOriginDistance(int)},
     *                     default 1).
     * @param searchRadius inner radius (in blocks): only positions within this
     *                     radius of {@code origin} are counted as actual hits.
     * @param totalRadius  outer radius (in blocks) up to which the BFS is allowed to
     *                     traverse overall. Should be {@code >= searchRadius} so the
     *                     search can route around obstacles and still reach
     *                     positions within {@code searchRadius}. Also determines the
     *                     size of the internal grid ({@code (2 * totalRadius + 1)²},
     *                     see the class comment on working-array size limits —
     *                     {@code totalRadius <= 31} is the maximum supported).
     */
    public SurfaceFinder(Level level, BlockPos origin, int searchRadius, int totalRadius) {
        this.level = level;
        this.originX = origin.getX();
        this.originZ = origin.getZ();
        this.radius = totalRadius;
        this.radiusSquared = this.radius * this.radius;
        this.searchRadiusSquared = searchRadius * searchRadius;
        this.maxRange = (radius * 2) + 1;
        this.maxPositions = maxRange * maxRange;
        this.queue = QUEUE_CACHE.get();
        this.visitedOffsetXZ = VISITED_CACHE.get();
        this.blocked = BLOCKED_CACHE.get();
        // Reset the working arrays from the thread-local cache: since they are
        // reused across search runs, they may still contain data from a previous
        // search. Only the actually needed range (0..maxPositions) is reset, not
        // the whole 4000-element array.
        java.util.Arrays.fill(this.visitedOffsetXZ, 0, this.maxPositions, EMPTY_VALUE);
        java.util.Arrays.fill(this.blocked, 0, this.maxPositions, false);
        this.currentPos = new BlockPos.MutableBlockPos(originX,
                origin.getY(), originZ);
        // Origin position as the first (and initially only) entry in the queue.
        this.queue[0] = BlockPos.asLong(originX, origin.getY(), originZ);
    }

    /**
     * Adds a block as an obstacle: positions with this block count as blocked
     * themselves, and so do all positions within the block distance
     * ({@link #distanceToBlocked(int)}) around them (see {@link #markBlocked(int, int)}).
     * Can be called multiple times to register several block types as obstacles.
     *
     * @param block the block to treat as an obstacle (e.g. {@code Blocks.LAVA}).
     * @return {@code this}, for method chaining (builder pattern).
     */
    public SurfaceFinder blocked(Block block) {
        this.blockTargets = Arrays.copyOf(this.blockTargets, this.blockTargets.length + 1);
        this.blockTargets[this.blockCount++] = block;
        this.checkBlocking = true;
        return this;
    }

    /**
     * Like {@link #blocked(Block)}, but accepts a block tag instead of a single
     * block (e.g. to mark a whole group of blocks, such as all fire variants, as
     * obstacles at once).
     *
     * @param tag the block tag to treat as an obstacle.
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder blocked(TagKey<Block> tag) {
        this.tagTargets = Arrays.copyOf(this.tagTargets, this.tagTargets.length + 1);
        this.tagTargets[this.tagCount++] = tag;
        this.checkBlocking = true;
        return this;
    }

    public SurfaceFinder breakable(TagKey<Block> tag) {
        this.breakableTags = Arrays.copyOf(this.breakableTags, this.breakableTags.length + 1);
        this.breakableTags[this.breakableTagCount++] = tag;
        this.hasBreakableBlocks = true;
        return this;
    }

    /**
     * Sets the block distance (buffer radius) around blocks marked as
     * {@link #blocked(Block) blocked}: all positions within this distance of an
     * obstacle block are marked blocked as well, not just the obstacle block
     * itself. Default value is 2.
     *
     * @param distance block distance in blocks (circular, see {@link #getBlockedMask(int)}).
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder distanceToBlocked(int distance) {
        this.distanceBlocked = distance;
        return this;
    }

    /**
     * Adds an allowed ground block: once {@code ground(...)} has been called at
     * least once, only positions standing directly on one of the registered blocks
     * are valid (e.g. only on grass, not on sand). Without a call, any solid
     * (non-air) ground is allowed.
     *
     * @param block allowed ground block.
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder ground(Block block) {
        this.ground = Arrays.copyOf(this.ground, this.ground.length + 1);
        this.ground[this.groundCount++] = block;
        this.checkGround = true;
        return this;
    }

    /**
     * Restricts valid hits to a ring around a second reference position
     * (independent of the origin {@code origin}): only positions whose distance to
     * {@code pos} lies between {@code distance1} and {@code distance2} (the order of
     * the two parameters doesn't matter, min/max are taken internally) count as
     * valid results.
     *
     * @param pos       reference position for the distance measurement.
     * @param distance1 first distance value (block distance).
     * @param distance2 second distance value (block distance).
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder between(BlockPos pos, int distance1, int distance2) {
        int min = Math.min(distance1, distance2);
        int max = Math.max(distance1, distance2);
        this.betweenX = pos.getX();
        this.betweenZ = pos.getZ();
        this.betweenMinSquared = min * min;
        this.betweenMaxSquared = max * max;
        this.checkBetween = true;
        return this;
    }

    /**
     * Enables "find" mode for a concrete block: instead of walkable surface
     * positions, positions where this block occurs within the inner search radius
     * are returned (e.g. so a mining AI can mine it). Found positions are not
     * traversed further (you cannot walk through the target block), but they do
     * contribute to the result themselves.
     *
     * @param block the block to search for.
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder find(Block block) {
        this.checkFound = true;
        this.findBlock = block;
        return this;
    }

    /**
     * Like {@link #find(Block)}, but searches for all blocks of a block tag instead
     * of a single block (e.g. "all ore blocks" instead of just "iron ore").
     *
     * @param tag the block tag to search for.
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder find(TagKey<Block> tag) {
        this.checkFound = true;
        this.findTag = tag;
        return this;
    }

    /**
     * Sets the minimum distance a valid hit must have from the origin position.
     * Default value is 1, meaning the origin position itself never counts as a hit
     * (which wouldn't happen anyway, since the BFS can never reach the origin's own
     * column again as a child position, see {@link #validPos}).
     *
     * @param distance minimum distance, in blocks, from the origin position.
     * @return {@code this}, for method chaining.
     */
    public SurfaceFinder minOriginDistance(int distance) {
        this.minOriginDistanceSquared = (long) distance * distance;
        return this;
    }

    /**
     * Returns the {@link BlockState} at the given position, taking advantage of a
     * single-entry chunk cache ({@link #lastChunk}): since a local search performs
     * many consecutive lookups within the same chunk, {@link Level#getChunk(int, int)}
     * is only called again when the chunk coordinates differ from the previous
     * lookup. This is usually noticeably cheaper than {@link Level#getBlockState(BlockPos)},
     * which has to resolve the owning chunk again on every call.
     *
     * <p>As a side effect, the shared {@link #currentPos} is set to the given
     * coordinates; subsequent code (e.g. in {@link #validPos}) can rely on
     * {@link #currentPos} holding the most recently queried position after this
     * call (e.g. for an immediately following {@code getCollisionShape(...)} query
     * at the same position).</p>
     *
     * @param x world X coordinate.
     * @param y world Y coordinate.
     * @param z world Z coordinate.
     * @return the {@link BlockState} at this position.
     */
    private BlockState fastGetState(int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
            this.lastChunkX = chunkX;
            this.lastChunkZ = chunkZ;
            this.lastChunk = this.level.getChunk(chunkX, chunkZ);
        }

        this.currentPos.set(x, y, z);
        return this.lastChunk.getBlockState(this.currentPos);
    }

    /**
     * Computes (and caches statically, see {@link #BLOCKED_MASKS}) the list of all
     * relative (dx, dz) offsets within a disk of radius {@code d} around the origin
     * (0,0). Used by {@link #markBlocked(int, int)} to efficiently mark every
     * column within the block distance around an obstacle block, without having to
     * iterate a square and check the circle condition on every call.
     *
     * <p>The result is a flat {@code int[]} in which every two consecutive values
     * form one (dx, dz) pair (index 0/1 = first pair, 2/3 = second pair, and so on),
     * to avoid allocating {@link BlockPos} or point objects.</p>
     *
     * @param distance radius of the disk (in blocks).
     * @return flat array of (dx, dz) offset pairs within this radius.
     */
    private int[] getBlockedMask(int distance) {
        return BLOCKED_MASKS.computeIfAbsent(distance, d -> {
            List<Integer> offsets = new ArrayList<>();
            int distanceSquared = d * d;
            for(int x = -d; x <= d; x++) {
                for(int z = -d; z <= d; z++) {
                    // Only keep offsets within the disk (not the whole square), for
                    // a round/natural-looking exclusion zone instead of a blocky one.
                    if((x * x + z * z) <= distanceSquared) {
                        offsets.add(x);
                        offsets.add(z);
                    }
                }
            }
            return offsets.stream().mapToInt(i -> i).toArray();
        });
    }


    /** Marks the (X,Z) column at {@code (posX, posZ)} as blocked in the {@link #blocked} grid. */
    private void setBlocked(int posX, int posZ) {
        this.blocked[getIndex(posX, posZ)] = true;
    }

    /** Stores the visited Y height (or a sentinel value) for the given grid index. */
    private void setVisitedY(int index, int posY) {
        this.visitedOffsetXZ[index] = posY;
    }

    /** Returns the stored Y height (or a sentinel value) for the given grid index. */
    private int getVisitedY(int index) {
        return this.visitedOffsetXZ[index];
    }


    /**
     * Converts world coordinates (X,Z) into a flat index into the square
     * {@link #visitedOffsetXZ}/{@link #blocked} grid. The grid is centered on the
     * origin ({@link #originX}, {@link #originZ}) and has side length {@link #maxRange}.
     *
     * @param posX world X coordinate.
     * @param posZ world Z coordinate.
     * @return flat grid index (row-major layout: {@code offsetX + offsetZ * maxRange}).
     */
    private int getIndex(int posX, int posZ) {
        int offsetX = posX - this.originX + this.radius;
        int offsetZ = posZ - this.originZ + this.radius;

        return offsetX + offsetZ * this.maxRange;
    }

    /** Converts a flat grid index (see {@link #getIndex}) back into the world X coordinate. */
    private int getXByIndex(int index) {
        int offsetX = index % this.maxRange;
        return offsetX - this.radius + this.originX;
    }

    /** Converts a flat grid index (see {@link #getIndex}) back into the world Z coordinate. */
    private int getZByIndex(int index) {
        int offsetZ = index / this.maxRange;
        return offsetZ - this.radius + this.originZ;
    }

    /**
     * Marks every column within the block distance ({@link #distanceBlocked})
     * around {@code (posX, posZ)} as blocked (see {@link #blocked} grid). Called
     * whenever the search finds a block registered as an {@link #blocked(Block)
     * obstacle}.
     *
     * <p>Since the circular mask ({@link #getBlockedMask(int)}) around
     * {@code (posX, posZ)} can include positions outside the outer search radius,
     * every masked position is additionally checked to still be within the search
     * radius ({@link #radiusSquared}) of the origin before it is marked in the grid
     * — otherwise it would fall outside the valid bounds of
     * {@link #visitedOffsetXZ}/{@link #blocked}, or simply wouldn't be needed there.</p>
     *
     * @param posX world X coordinate of the obstacle block.
     * @param posZ world Z coordinate of the obstacle block.
     */
    private void markBlocked(int posX, int posZ) {
        int[] mask = getBlockedMask(this.distanceBlocked);
        for (int i = 0; i < mask.length; i += 2) {
            int x = posX + mask[i];
            int z = posZ + mask[i + 1];

            int distanceOriginX = this.originX - x;
            int distanceOriginZ = this.originZ - z;
            long distanceOrigin = (long) distanceOriginX * distanceOriginX
                    + (long) distanceOriginZ * distanceOriginZ;
            if(distanceOrigin <= this.radiusSquared) {
                setBlocked(x, z);
            }
        }
    }

    private boolean isBreakable(BlockState state) {
        for (int i = 0; i < this.breakableTagCount; i++) {
            if (state.is(this.breakableTags[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the breadth-first search to completion and returns the found positions.
     *
     * <p>The first part of the method fully drains the queue {@link #queue}: for
     * every dequeued position, all 8 horizontal neighbour directions (diagonals
     * included) are examined; for each direction, a step down is tried first, then
     * level, then up ({@link #validPos} in that order), and the first valid variant
     * is added to the queue (see the {@code break} in the inner loop). This gives a
     * simple but effective "follow the surface" behaviour (downhill before level
     * before uphill).</p>
     *
     * <p>The second part extracts the result from the {@link #visitedOffsetXZ} grid.
     * Since a position that was still valid at the time it was discovered can later
     * have been marked blocked by {@link #markBlocked(int, int)} (if an obstacle was
     * only discovered later, at a neighbouring position), every position must be
     * additionally checked against the {@link #blocked} grid when it is read out.</p>
     *
     * @param singleRandom {@code true}: instead of all hits, only a single position,
     *                     chosen uniformly at random among all valid hits, is
     *                     returned (as a list with at most one element) — without
     *                     first having to copy every hit into a separate list.
     *                     {@code false}: all valid hits are returned.
     * @return list of found positions (empty if nothing was found).
     */
    public List<BlockPos> scan(boolean singleRandom) {
        // --- Part 1: fully drain the BFS queue. ---
        while (this.queueTail < this.queueHead) {
            long nextPos = this.queue[this.queueTail];
            int nextX = BlockPos.getX(nextPos);
            int nextY = BlockPos.getY(nextPos);
            int nextZ = BlockPos.getZ(nextPos);

            // Squared distance of the currently processed (parent) position to the
            // origin; passed to validPos() so it can enforce that child positions
            // lie strictly farther from the origin (monotonic outward expansion,
            // preventing cycles/backtracking).
            int distanceX = this.originX - nextX;
            int distanceZ = this.originZ - nextZ;
            long distance = (long) distanceX * distanceX + (long) distanceZ * distanceZ;

            // Walk all 8 horizontal neighbour directions (3x3 minus the centre).
            for(int offsetX = -1; offsetX <= 1; offsetX++) {
                for(int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    // For each horizontal direction, try a step down first, then
                    // level, then up. The first valid one wins (break) -> so each
                    // horizontal direction produces at most ONE child, preferring
                    // the smallest height change ("downhill" before "level" before
                    // "uphill").
                    for(int offsetY = -1; offsetY <= 1; offsetY++) {
                        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) continue;
                        if(validPos(nextX, nextY, nextZ, distance, offsetX, offsetY, offsetZ)) {
                            this.queue[this.queueHead] = BlockPos.asLong(nextX + offsetX,
                                    nextY + offsetY, nextZ + offsetZ);
                            this.queueHead++;
                            break;
                        }
                    }
                }
            }
            this.queueTail++;
        }

        // --- Part 2: extract the results from the visitedOffsetXZ grid. ---
        ArrayList<BlockPos> result = new ArrayList<>(singleRandom ? 1 : this.resultLength);
        int index = 0;
        int resultIndex = 0;
        if(singleRandom) {
            if(this.resultLength == 0) return result;
            // Pick a random target rank among all hits counted during the search
            // (resultLength). Since resultLength also counts positions later
            // blocked retroactively (see the comment on part 2 above), the target
            // rank is continuously corrected downward while scanning below,
            // whenever a (wrongly counted) blocked position turns up.
            int resultTargetIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(this.resultLength);
            while (index < this.maxPositions) {
                int y = this.visitedOffsetXZ[index];
                // y > EMPTY_VALUE (-100) filters out both unvisited columns
                // (EMPTY_VALUE) and visited-but-invalid columns (UNAVAILABLE_VALUE
                // = -101, which lies BELOW EMPTY_VALUE) — what's left are only
                // columns holding a genuine Y coordinate.
                if(y > EMPTY_VALUE) {
                    if(this.blocked[index]) {
                        // This position was blocked retroactively: it doesn't count
                        // as a hit. resultIndex is decremented to compensate for the
                        // earlier (too optimistic) counting into resultLength; the
                        // target rank is pulled down too if needed, so it doesn't
                        // point at a rank that effectively no longer exists.
                        resultIndex--;
                        resultTargetIndex = Math.min(resultIndex, resultTargetIndex);
                    } else {
                        if (resultIndex == resultTargetIndex) {
                            // The randomly chosen target rank has been reached -
                            // this is the position to return.
                            int x = getXByIndex(index);
                            int z = getZByIndex(index);
                            result.add(new BlockPos(x, y, z));
                            return result;
                        } else {
                            resultIndex++;
                        }
                    }
                }
                index++;
            }
            return result;
        } else {
            // Collect all valid hits. The loop condition resultIndex < resultLength
            // is an optimization: as long as no positions are blocked retroactively,
            // the loop can stop early once all originally counted hits have been
            // found. If a blocked position turns up, resultIndex is decremented
            // instead of incremented - this way the stop condition mathematically
            // can never trigger "too early", and in the worst case the whole grid
            // is (correctly) scanned.
            while (index < this.maxPositions && resultIndex < this.resultLength) {
                int y = this.visitedOffsetXZ[index];
                if(y > EMPTY_VALUE) {
                    if(this.blocked[index]) {
                        resultIndex--;
                    } else {
                        int x = getXByIndex(index);
                        int z = getZByIndex(index);
                        result.add(new BlockPos(x, y, z));
                        resultIndex++;
                    }
                }
                index++;
            }
            return result;
        }
    }

    /**
     * Checks whether the neighbour position {@code (parentX + offsetX,
     * parentY + offsetY, parentZ + offsetZ)} of an already-processed position
     * {@code (parentX, parentY, parentZ)} is valid, and if so, records it as
     * "visited" in {@link #visitedOffsetXZ} (including all side effects such as
     * {@link #markBlocked}, {@code resultLength++}, etc.). This is the actual core
     * of the algorithm: every filter and walkability condition is checked here.
     *
     * <p>A return value of {@code true} only means "this position is walkable and
     * should be traversed further" — not necessarily "this position is a valid
     * hit" (e.g. in find mode, or outside the inner search radius, a position is
     * traversed but marked as {@link #UNAVAILABLE_VALUE} and therefore does not
     * count toward the result, see the computation of {@code newChildY} further
     * down).</p>
     *
     * @param parentX         X coordinate of the starting position.
     * @param parentY         Y coordinate of the starting position.
     * @param parentZ         Z coordinate of the starting position.
     * @param parentDistance  squared distance of the starting position to the
     *                        origin (pre-computed so it doesn't have to be
     *                        recalculated here).
     * @param offsetX         X offset to the neighbour position (-1, 0, or 1).
     * @param offsetY         Y offset to the neighbour position (-1, 0, or 1).
     * @param offsetZ         Z offset to the neighbour position (-1, 0, or 1).
     * @return {@code true} if the neighbour position is walkable and should be
     *         added to the BFS queue; {@code false} otherwise.
     */
    private boolean validPos(int parentX, int parentY, int parentZ, long parentDistance,
                            int offsetX, int offsetY, int offsetZ) {
        int childX = parentX + offsetX;
        int childZ = parentZ + offsetZ;

        // 1) Outer search radius boundary: nothing beyond it is searched at all
        // (hard limit, also determines the grid size).
        int distanceOriginX = this.originX - childX;
        int distanceOriginZ = this.originZ - childZ;
        long distanceOrigin = (long) distanceOriginX * distanceOriginX + (long) distanceOriginZ * distanceOriginZ;

        if(distanceOrigin > this.radiusSquared) return false;

        // 2) Monotonic expansion: the child position must lie (horizontally)
        // strictly farther from the origin than the parent position. This prevents
        // the search from running back toward the origin or looping in circles,
        // and ensures every column is only ever considered once, "from the outside
        // in".
        if(distanceOrigin <= parentDistance) return false;

        // Does the position also lie within the inner (result) search radius and
        // beyond the minimum distance to the origin? Only then does it actually
        // count as a hit later (see newChildY further down).
        boolean inSearchRadius = distanceOrigin <= this.searchRadiusSquared
                && distanceOrigin >= this.minOriginDistanceSquared;

        int childIndex = getIndex(childX, childZ);

        // 3) Only one Y height is tracked per (X,Z) column: if this column has
        // already been visited (regardless of the outcome), it is not examined
        // again.
        if(getVisitedY(childIndex) != EMPTY_VALUE) return false;

        // 4) Optional "between" filter: distance to a second reference position
        // must lie within [betweenMinSquared, betweenMaxSquared]. Only actually
        // evaluated further down, when newChildY is determined.
        boolean isBetween = true;
        if(checkBetween) {
            int distanceBetweenX = this.betweenX - childX;
            int distanceBetweenZ = this.betweenZ - childZ;
            long distanceBetween = (long) distanceBetweenX * distanceBetweenX
                    + (long) distanceBetweenZ * distanceBetweenZ;
            isBetween = distanceBetween >= this.betweenMinSquared && distanceBetween <= this.betweenMaxSquared;
        }

        int childY = parentY + offsetY;

        this.currentPos.set(childX, childY, childZ);
        BlockState childState = fastGetState(this.currentPos.getX(),
                this.currentPos.getY(), this.currentPos.getZ());

        // 5) "find" mode: if the target block or target tag sits at this position,
        // the position itself is recorded as a hit (with its real Y height) — but
        // NOT traversed further (return false), since you cannot walk through the
        // target block (it's exactly the thing that, e.g., is meant to be mined).
        if(inSearchRadius && checkFound) {
            if((this.findBlock != null && childState.is(this.findBlock)) ||
                    (this.findTag != null && childState.is(this.findTag))) {
                setVisitedY(childIndex, childY);
                resultLength++;
                return false;
            }
        }


        // 6) "blocked" filter: if this is one of the blocks/tags registered as an
        // obstacle, not just this position but the entire exclusion zone
        // (distanceBlocked) around it is marked blocked (markBlocked) — even if
        // columns in that zone have already been (at this point, incorrectly)
        // counted as a valid hit; that gets corrected when the results are read
        // out in scan(). The current position itself is marked UNAVAILABLE and not
        // traversed further (the obstacle blocks the way).
        if(this.checkBlocking) {
            Block childBlock = childState.getBlock();
            for (int i = 0; i < this.blockCount; i++) {
                if (this.blockTargets[i] == childBlock) {
                    markBlocked(childX, childZ);
                    setVisitedY(childIndex, UNAVAILABLE_VALUE);
                    return false;
                }
            }
            for (int i = 0; i < this.tagCount; i++) {
                if (childState.is(this.tagTargets[i])) {
                    markBlocked(childX, childZ);
                    setVisitedY(childIndex, UNAVAILABLE_VALUE);
                    return false;
                }
            }
        }

        // 7) The position itself must be passable (air or no collision shape) -
        // you can't stand INSIDE a solid block.
        if(!childState.isAir() && !isBreakable(childState)
                && !childState.getCollisionShape(level, this.currentPos).isEmpty()) return false;

        this.currentPos.set(childX, childY -1, childZ);

        BlockState belowChildState = fastGetState(this.currentPos.getX(),
                this.currentPos.getY(), this.currentPos.getZ());

        // 8) Optional "ground" filter: the block DIRECTLY BELOW the position must
        // be one of the allowed ground blocks.
        if(this.checkGround) {
            boolean validGround = false;
            Block belowBlock = belowChildState.getBlock();
            for (int i = 0; i < this.groundCount; i++) {
                if (this.ground[i] == belowBlock) {
                    validGround = true;
                    break;
                }
            }
            if (!validGround) return false;
        }

        // 9) Independent of the ground filter: there must be solid ground at all
        // (no falling through empty air).
        if(belowChildState.isAir() || belowChildState.getCollisionShape(level, this.currentPos).isEmpty()) return false;

        this.currentPos.set(childX, childY + 1, childZ);
        BlockState aboveChildState = fastGetState(this.currentPos.getX(),
                this.currentPos.getY(), this.currentPos.getZ());

        // 10) Headroom: the block directly above the position must also be
        // passable (entities such as villagers are 2 blocks tall).
        if(!aboveChildState.isAir() && !isBreakable(aboveChildState) && !aboveChildState
                .getCollisionShape(level, this.currentPos).isEmpty()) return false;


        // Whether this position (now that it's confirmed walkable) should actually
        // count as a HIT: not in find mode (there, only the target blocks
        // themselves count, see point 5), and only if it lies within the inner
        // search radius and satisfies the between filter. Otherwise it is still
        // visited/traversed (for the ongoing path), but marked UNAVAILABLE_VALUE so
        // it doesn't flow into the result.
        int newChildY = checkFound || !inSearchRadius || !isBetween ? UNAVAILABLE_VALUE : childY;
        if(parentY == childY) {
            // Step at the same height: no extra headroom check needed, since
            // ground/air/headroom at the child position were already fully checked
            // above.
            setVisitedY(childIndex, newChildY);
            if(newChildY != UNAVAILABLE_VALUE) resultLength++;
            return true;
        } else if(parentY < childY) {
            // Step one level UP: additionally check whether there is enough space
            // above the parent position (at height childY + 1, i.e. the new "head
            // level") so you don't bump your head while stepping up.
            this.currentPos.set(parentX, childY + 1, parentZ);
            BlockState aboveAboveParentState = fastGetState(this.currentPos.getX(),
                    this.currentPos.getY(), this.currentPos.getZ());
            if(aboveAboveParentState.isAir() || isBreakable(aboveAboveParentState) || aboveAboveParentState
                    .getCollisionShape(level, this.currentPos).isEmpty()) {
                setVisitedY(childIndex, newChildY);
                if(newChildY != UNAVAILABLE_VALUE) resultLength++;
                return true;
            } else {
                return false;
            }
        } else {
            // Step one level DOWN: additionally check whether there is enough space
            // 2 blocks above the child position (i.e. at the old parent's head
            // level) so you don't bump your head on an overhang/ceiling while
            // stepping down.
            this.currentPos.set(childX, childY + 2, childZ);
            BlockState aboveAboveChildState = fastGetState(this.currentPos.getX(),
                    this.currentPos.getY(), this.currentPos.getZ());
            if(aboveAboveChildState.isAir() || isBreakable(aboveAboveChildState) || aboveAboveChildState
                    .getCollisionShape(level, this.currentPos).isEmpty()) {
                setVisitedY(childIndex, newChildY);
                if(newChildY != UNAVAILABLE_VALUE) resultLength++;
                return true;
            } else {
                return false;
            }
        }
    }
}
