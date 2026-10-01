package de.petrofsky.autonomousvillagers.utils;

import de.petrofsky.autonomousvillagers.utils.geometry.Plane;
import de.petrofsky.autonomousvillagers.utils.geometry.ShapeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BlockDataUtils {

    public static List<ItemEntity> getItemsAround(ServerLevel level, AABB boundingBox, double radius, Item item) {
        AABB searchArea = boundingBox.inflate(radius);
        return level.getEntitiesOfClass(ItemEntity.class, searchArea,
                itemEntity -> itemEntity.isAlive() && itemEntity.getItem().is(item));
    }

    public static List<ItemEntity> getItemsAround(ServerLevel level, AABB boundingBox, double radius, TagKey<Item> tag) {
        AABB searchArea = boundingBox.inflate(radius);
        return level.getEntitiesOfClass(ItemEntity.class, searchArea,
                itemEntity -> itemEntity.isAlive() && itemEntity.getItem().is(tag));
    }

    public static boolean hasBlocks(ServerLevel level, BlockPos pos, Block... block) {
        BlockState blockState = level.getBlockState(pos);
        return Arrays.stream(block).anyMatch(blockState::is);
    }

    @SafeVarargs
    public static boolean hasTags(ServerLevel level, BlockPos pos, TagKey<Block>... tags) {
        BlockState blockState = level.getBlockState(pos);
        return Arrays.stream(tags).anyMatch(blockState::is);
    }

    @SafeVarargs
    public static boolean hasTagsVertical(ServerLevel level, BlockPos pos,
                                          int height, boolean upwards, boolean all, TagKey<Block>... tags) {
        height = Math.max(1, height);
        int index = 0;
        while (index < height) {
            BlockState blockState = level.getBlockState(pos);
            boolean matchesAnyTag = false;
            for (TagKey<Block> tag : tags) {
                if (blockState.is(tag)) {
                    matchesAnyTag = true;
                    break;
                }
            }

            if(all && ! matchesAnyTag) {
                return false;
            }
            if(!all && matchesAnyTag) {
                return true;
            }
            pos = upwards ? pos.above() : pos.below();
            index++;
        }
        return all;
    }

    @SafeVarargs
    public static List<BlockPos> getWithAnyTags(ServerLevel level, List<BlockPos> posList, TagKey<Block>... tags) {
        return posList.stream().filter(blockPos -> hasTags(level, blockPos, tags)).toList();
    }

    public static List<BlockPos> getByAnyBlock(ServerLevel level, List<BlockPos> posList, Block... block) {
        return posList.stream().filter(blockPos -> hasBlocks(level, blockPos, block)).toList();
    }

    public static BlockPos getHighestPos(ServerLevel level, BlockPos pos) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }

    public static List<BlockPos> getHighestPos(ServerLevel level, List<BlockPos> posList, BlockPos origin, int distanceY ) {
        ArrayList<BlockPos> result = new ArrayList<>();
        for(BlockPos blockPos : posList) {
            BlockPos highestPos = getHighestPos(level, blockPos).below();

            if(result.contains(highestPos) || (distanceY > -1 && Math.abs(highestPos.getY() - origin.getY()) > distanceY)) {
                continue;
            }
           result.add(highestPos);

        }
        return result;
    }

    public static List<Map.Entry<BlockPos, BlockState>> findChests(
            ServerLevel level, BlockPos origin, boolean allowedBlockedChests) {
        List<BlockPos> targets = ShapeUtils.circle(origin, 4).on(Plane.XZ, origin.getY()).toList();
        targets = getHighestPos(level, targets, origin, 2);
        targets = getByAnyBlock(level, targets, Blocks.CHEST);

        ArrayList<BlockPos> connectedChests = new ArrayList<>();

        return targets.stream().map(blockPos ->
                Map.entry(blockPos, level.getBlockState(blockPos))).filter(entry -> {
            ChestBlock chestBlock = (ChestBlock) entry.getValue().getBlock();
            Container container = ChestBlock.getContainer(
                    chestBlock, entry.getValue(), level, entry.getKey(), allowedBlockedChests);
            if(container == null) {
                return false;
            }

            if(connectedChests.contains(entry.getKey())) {
                return false;
            }

            ChestType chestType = entry.getValue().getValue(ChestBlock.TYPE);
            if(chestType == ChestType.SINGLE) {
                return true;
            } else {
                Direction connectedDir = ChestBlock.getConnectedDirection(entry.getValue());
                connectedChests.add(entry.getKey().relative(connectedDir));
                return true;
            }
        }).toList();
    }

    public static BlockPos getFirstAirBlock(ServerLevel level, BlockPos start) {
        while (!hasTags(level, start, BlockTags.AIR)) {
            start = start.above();
        }
        return start;
    }

    public static BlockPos getWalkableNeighbor(ServerLevel level, BlockPos origin, BlockPos target) {
        List<BlockPos> neighbors = BlockGeometry3DUtils.getNeighborByTargetDistance(origin, target);
        return neighbors.stream().filter(neighbor -> {
            BlockState blockState = level.getBlockState(neighbor.below());
            boolean solid = blockState.isCollisionShapeFullBlock(level, neighbor);
            boolean walkable = hasTagsVertical(level, neighbor,
                    2, true, true, BlockTags.AIR);
            return solid && walkable;
        }).findFirst().orElse(origin);
    }

    public static boolean isSolid(Level level, BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.is(Blocks.DIRT_PATH) || blockState.isCollisionShapeFullBlock(level, blockPos);
    }

    public static boolean isCollisionFree(Level level, BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.getCollisionShape(level, blockPos).isEmpty();
    }

    public static boolean hasAnySolid(Level level, List<BlockPos> blockPosList) {
        return blockPosList.stream().anyMatch(blockPos -> isSolid(level, blockPos));
    }

    public static boolean hasSolidNeighborHorizontal(Level level, BlockPos blockPos) {
        List<BlockPos> neighbor = List.of(blockPos.north(), blockPos.east(), blockPos.south(), blockPos.west());
        return neighbor.stream().allMatch(neighborPos -> isSolid(level, neighborPos));
    }

    public static boolean isHole(Level level, BlockPos blockPos, boolean ignoreOrigin) {
        if(ignoreOrigin) {
            return  hasSolidNeighborHorizontal(level, blockPos.above());
        } else{
            return hasSolidNeighborHorizontal(level, blockPos.above())
                    && isSolid(level, blockPos);
        }
    }

    public static List<BlockPos> getNonHoles(Level level, List<BlockPos> positions, boolean ignoreOrigin) {
        return positions.stream().filter(blockPos -> !isHole(level, blockPos, ignoreOrigin)).toList();
    }

    public static List<BlockPos> getSolidBelow(Level level, List<BlockPos> positions) {
        return positions.stream().filter(blockPos -> isSolid(level, blockPos.below())).toList();
    }

    public static BlockPos getReachableBlocksOnRadius(ServerLevel level, BlockPos origin, BlockPos target, int radius) {

        List<BlockPos> positions = ShapeUtils.circle(target, radius).on(Plane.XZ, target.getY()).edgeOnly().toList();
        positions = getHighestPos(level, positions, origin, 1);
        positions = positions.stream().filter(blockPos ->
            !getWalkableNeighbor(level, blockPos, origin).equals(blockPos)).toList();
        return BlockGeometry3DUtils.getClosest(positions, origin);

    }

}
