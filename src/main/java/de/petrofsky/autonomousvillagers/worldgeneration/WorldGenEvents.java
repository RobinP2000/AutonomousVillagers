package de.petrofsky.autonomousvillagers.worldgeneration;

import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import de.petrofsky.autonomousvillagers.blocks.ForesterBlock;
import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = AutonomousVillagers.MODID)
public class WorldGenEvents {

    private static final List<ResourceKey<Structure>> VILLAGE_KEYS = List.of(
            BuiltinStructures.VILLAGE_PLAINS,
            BuiltinStructures.VILLAGE_DESERT,
            BuiltinStructures.VILLAGE_SAVANNA,
            BuiltinStructures.VILLAGE_SNOWY,
            BuiltinStructures.VILLAGE_TAIGA
    );

    private static final Set<BlockPos> PENDING_COMPOSTERS = ConcurrentHashMap.newKeySet();


    private static final Set<Long>     SEEN_VILLAGE_CENTERS    = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> PENDING_VILLAGE_ANCHORS = ConcurrentHashMap.newKeySet();

    private static final int[][] CARDINAL_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;


        LevelChunk chunk  = (LevelChunk) event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        BlockPos checkPos = new BlockPos(chunkPos.getMiddleBlockX(), 64, chunkPos.getMiddleBlockZ());
        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        Structure foundStructure = null;
        for (ResourceKey<Structure> key : VILLAGE_KEYS) {
            Structure s = structureRegistry.get(key);
            if (s == null) continue;
            if (level.structureManager().getAllStructuresAt(checkPos).containsKey(s)) {
                foundStructure = s;
                break;
            }
        }
        if (foundStructure == null) return;

        level.getServer().execute(() -> {

            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    for (int y = surfaceY - 5; y <= surfaceY + 5; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getBlockState(pos).is(Blocks.COMPOSTER)) {
                            PENDING_COMPOSTERS.add(pos);
                        }
                    }
                }
            }
        });

        StructureStart start = level.structureManager().getStructureAt(checkPos, foundStructure);
        if (!start.isValid()) return;

        int centerX = roundTo32(start.getBoundingBox().getCenter().getX());
        int centerZ = roundTo32(start.getBoundingBox().getCenter().getZ());
        long centerKey = ((long) centerX << 32) | (centerZ & 0xFFFFFFFFL);

        if (!SEEN_VILLAGE_CENTERS.add(centerKey)) return;

        level.getServer().execute(() -> {
            PENDING_VILLAGE_ANCHORS.add(new BlockPos(centerX, 0, centerZ));
        });
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;


        if (!PENDING_COMPOSTERS.isEmpty()) {
            Iterator<BlockPos> iterator = PENDING_COMPOSTERS.iterator();
            while (iterator.hasNext()) {
                BlockPos composterPos = iterator.next();

                if (areSurroundingChunksLoaded(level, composterPos, 9)) {
                    placeChestNearComposter(level, composterPos);
                    iterator.remove();
                }
            }
        }


        if (!PENDING_VILLAGE_ANCHORS.isEmpty()) {
            Iterator<BlockPos> it = PENDING_VILLAGE_ANCHORS.iterator();
            if (it.hasNext()) {
                BlockPos anchor = it.next();
                if (areSurroundingChunksLoaded(level, anchor, 40)) {
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            anchor.getX(), anchor.getZ());
                    placeForesterBlocks(level, anchor.atY(surfaceY));
                    it.remove();
                }
            }
        }
    }



    private static void placeForesterBlocks(ServerLevel level, BlockPos anchor) {

        if (hasForesterBlockNearby(level, anchor, 50)) return;

        Random rng = new Random(level.getSeed() ^ anchor.asLong());
        int targetCount = 1 + rng.nextInt(2);

        List<BlockPos> pathBlocks = findVillagePathBlocks(level, anchor, 35);


        Map<BlockPos, int[]> candidates = new LinkedHashMap<>();

        for (BlockPos pathPos : pathBlocks) {
            for (int[] dir : CARDINAL_DIRS) {
                for (int dist = 3; dist <= 18; dist++) {
                    int wx = pathPos.getX() + dir[0] * dist;
                    int wz = pathPos.getZ() + dir[1] * dist;
                    if (!level.hasChunk(wx >> 4, wz >> 4)) break;

                    int surfaceY = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                    BlockPos candidate = new BlockPos(wx, surfaceY, wz);

                    if (isValidForesterSpot(level, candidate)
                            && hasClearAreaInDirection(level, candidate, dir, 12, 12)) {
                        candidates.put(candidate, dir);
                        break;
                    }
                }
            }
        }


        if (candidates.isEmpty()) {
            for (int r = 20; r <= 40 && candidates.size() < 8; r += 4) {
                for (int[] dir : CARDINAL_DIRS) {
                    int wx = anchor.getX() + dir[0] * r;
                    int wz = anchor.getZ() + dir[1] * r;
                    if (!level.hasChunk(wx >> 4, wz >> 4)) continue;

                    int surfaceY = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                    BlockPos candidate = new BlockPos(wx, surfaceY, wz);

                    if (isValidForesterSpot(level, candidate)
                            && hasClearAreaInDirection(level, candidate, dir, 12, 12)) {
                        candidates.put(candidate, dir);
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        List<Map.Entry<BlockPos, int[]>> candidateList = new ArrayList<>(candidates.entrySet());
        Collections.shuffle(candidateList, rng);

        List<Map.Entry<BlockPos, int[]>> chosen = new ArrayList<>();
        for (Map.Entry<BlockPos, int[]> entry : candidateList) {
            boolean tooClose = chosen.stream().anyMatch(c -> c.getKey().distSqr(entry.getKey()) < 12 * 12);
            if (!tooClose) {
                chosen.add(entry);
                if (chosen.size() >= targetCount) break;
            }
        }

        for (Map.Entry<BlockPos, int[]> entry : chosen) {
            BlockPos pos = entry.getKey();
            int[] dir    = entry.getValue();
            level.setBlock(pos, ForesterBlock.WOOD_CHOP_BLOCK.get().defaultBlockState(), 3);

            if (level.getBlockEntity(pos) instanceof ForesterBlockEntity be) {
                be.setField(dir, 12, 12);
                System.out.println("[Forester] setField aufgerufen: dir="
                        + Arrays.toString(dir) + " bei " + pos);
            } else {
                System.out.println("[Forester] FEHLER: Keine BE nach setBlock bei " + pos);
            }
            placeChestWithSapling(level, pos, dir);
        }
    }

    private static List<BlockPos> findVillagePathBlocks(ServerLevel level, BlockPos anchor, int radius) {
        List<BlockPos> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int wx = anchor.getX() + dx;
                int wz = anchor.getZ() + dz;
                if (!level.hasChunk(wx >> 4, wz >> 4)) continue;

                int surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                for (int dy = -3; dy <= 1; dy++) {
                    BlockPos p = new BlockPos(wx, surfaceY + dy, wz);
                    if (isVillagePathBlock(level.getBlockState(p))) {
                        result.add(p);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private static boolean isVillagePathBlock(BlockState state) {
        return state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE_SLAB);
    }


    private static boolean hasClearAreaInDirection(ServerLevel level, BlockPos pos,
                                                   int[] dir, int width, int depth) {
        int perpX = dir[1];
        int perpZ = dir[0];
        int half  = width / 2;

        for (int fwd = 1; fwd <= depth; fwd++) {
            for (int side = -half; side <= half; side++) {
                int wx = pos.getX() + dir[0] * fwd + perpX * side;
                int wz = pos.getZ() + dir[1] * fwd + perpZ * side;
                if (!level.hasChunk(wx >> 4, wz >> 4)) return false;

                int surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);

                for (int dy = -3; dy <= 10; dy++) {
                    if (isVillageBuildingBlock(
                            level.getBlockState(new BlockPos(wx, surfaceY + dy, wz))))
                        return false;
                }

                for (int dy = -2; dy <= 2; dy++) {
                    BlockState s = level.getBlockState(new BlockPos(wx, surfaceY + dy, wz));
                    if (s.is(Blocks.FARMLAND)
                            || s.is(BlockTags.CROPS)
                            || s.is(Blocks.PUMPKIN)
                            || s.is(Blocks.MELON))
                        return false;
                }
            }
        }
        return true;
    }

    private static boolean hasForesterBlockNearby(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!level.hasChunk((center.getX() + dx) >> 4, (center.getZ() + dz) >> 4)) continue;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        center.getX() + dx, center.getZ() + dz);
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = new BlockPos(center.getX() + dx, surfaceY + dy, center.getZ() + dz);
                    if (level.getBlockState(p).is(ForesterBlock.WOOD_CHOP_BLOCK.get())) return true;
                }
            }
        }
        return false;
    }

    private static boolean isValidForesterSpot(ServerLevel level, BlockPos pos) {
        if (!isNaturalGround(level.getBlockState(pos.below()))) return false;
        if (!level.getBlockState(pos).canBeReplaced())           return false;
        if (!level.getBlockState(pos.above()).isAir())           return false;
        return true;
    }

    private static boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE);
    }

    private static boolean isVillageBuildingBlock(BlockState state) {
        if (state.is(BlockTags.PLANKS))           return true;
        if (state.is(BlockTags.WOODEN_STAIRS))    return true;
        if (state.is(BlockTags.WOODEN_SLABS))     return true;
        if (state.is(BlockTags.WOODEN_FENCES))    return true;
        if (state.is(BlockTags.WOODEN_DOORS))     return true;
        if (state.is(BlockTags.WOODEN_TRAPDOORS)) return true;
        if (state.is(BlockTags.BEDS))             return true;

        return state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.COBBLESTONE_STAIRS)
                || state.is(Blocks.COBBLESTONE_SLAB)
                || state.is(Blocks.COBBLESTONE_WALL)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.GLASS)
                || state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.COMPOSTER)
                || state.is(Blocks.GRINDSTONE)
                || state.is(Blocks.BARREL)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.SMOKER)
                || state.is(Blocks.LECTERN)
                || state.is(Blocks.BOOKSHELF)
                || state.is(Blocks.CARTOGRAPHY_TABLE)
                || state.is(Blocks.FLETCHING_TABLE)
                || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.STONECUTTER)
                || state.is(Blocks.CAULDRON)
                || state.is(Blocks.BREWING_STAND);
    }


    private static int roundTo32(int value) {
        return Math.round(value / 32.0f) * 32;
    }

    private static boolean areSurroundingChunksLoaded(ServerLevel level, BlockPos pos, int radius) {
        int minChunkX = (pos.getX() - radius) >> 4;
        int maxChunkX = (pos.getX() + radius) >> 4;
        int minChunkZ = (pos.getZ() - radius) >> 4;
        int maxChunkZ = (pos.getZ() + radius) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void placeChestNearComposter(ServerLevel level, BlockPos composterPos) {
        double shortestDistance = Double.MAX_VALUE;
        BlockPos offsetPos = null;
        boolean hasChest = false;

        firstFor: for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                for (int y = -2; y <= 2; y++) {
                    if (x == 0 && z == 0) continue;
                    BlockPos candidatePos = composterPos.offset(x, y, z);

                    if (level.getBlockState(candidatePos).is(Blocks.CHEST)) {
                        hasChest = true;
                        break firstFor;
                    }

                    if (x > 4 || x < -4 || z > 4 || z < -4) continue;

                    double candidateDistance = composterPos.distSqr(candidatePos);
                    if (shortestDistance > candidateDistance) {
                        BlockState target = level.getBlockState(candidatePos);
                        BlockState below = level.getBlockState(candidatePos.below());
                        BlockState above = level.getBlockState(candidatePos.above());

                        if (target.canBeReplaced() && below.isSolid() && above.isAir()) {
                            shortestDistance = candidateDistance;
                            offsetPos = candidatePos;
                        }
                    }
                }
            }
        }

        if (hasChest) return;

        if (offsetPos != null) {
            level.setBlock(offsetPos, Blocks.CHEST.defaultBlockState(), 3);
        }
    }

    private static void placeChestWithSapling(ServerLevel level, BlockPos foresterPos, int[] fieldDir) {

        int perpX = fieldDir[1];
        int perpZ = fieldDir[0];

        List<BlockPos> tryOrder = List.of(
                foresterPos.offset( perpX,  0,  perpZ),
                foresterPos.offset(-perpX,  0, -perpZ),
                foresterPos.offset( fieldDir[0], 0,  fieldDir[1]),
                foresterPos.offset(-fieldDir[0], 0, -fieldDir[1])
        );

        for (BlockPos candidate : tryOrder) {
            BlockState below  = level.getBlockState(candidate.below());
            BlockState target = level.getBlockState(candidate);
            BlockState above  = level.getBlockState(candidate.above());

            if (below.isSolid() && target.canBeReplaced() && above.isAir()) {
                level.setBlock(candidate, Blocks.CHEST.defaultBlockState(), 3);

                if (level.getBlockEntity(candidate) instanceof ChestBlockEntity chest) {
                    ItemStack sapling = new ItemStack(getSaplingForLevel(level, foresterPos));
                    chest.setItem(0, sapling);
                }
                return;
            }
        }
    }

    private static Item getSaplingForLevel(ServerLevel level, BlockPos pos) {
        ResourceLocation biome = level.getBiome(pos).unwrapKey()
                .map(k -> k.location())
                .orElse(null);

        if (biome == null) return Items.OAK_SAPLING;

        String path = biome.getPath();
        if (path.contains("desert"))                            return Items.ACACIA_SAPLING;
        if (path.contains("savanna"))                          return Items.ACACIA_SAPLING;
        if (path.contains("snowy") || path.contains("taiga")) return Items.SPRUCE_SAPLING;
        if (path.contains("jungle"))                           return Items.JUNGLE_SAPLING;
        if (path.contains("birch"))                            return Items.BIRCH_SAPLING;
        if (path.contains("dark_forest"))                      return Items.DARK_OAK_SAPLING;
        return Items.OAK_SAPLING;
    }
}