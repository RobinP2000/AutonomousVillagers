package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        return blockState.isCollisionShapeFullBlock(level, blockPos);
    }

    public static boolean isCollusionFree(Level level, BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.getCollisionShape(level, blockPos).isEmpty();
    }

    public static boolean isAnySolid(Level level, List<BlockPos> blockPosList) {
        return blockPosList.stream().anyMatch(blockPos -> isSolid(level, blockPos));
    }

    public static BlockPos getReachableBlocksOnRadius(ServerLevel level, BlockPos origin, BlockPos target, int radius) {
        List<BlockPos> positions = BlockGeometry2DUtils.getCircleBorderXZ(target, radius, target.getY());
        positions = getHighestPos(level, positions, origin, 1);
        positions = positions.stream().filter(blockPos ->
            !getWalkableNeighbor(level, blockPos, origin).equals(blockPos)).toList();
        return BlockGeometry3DUtils.getClosest(positions, origin);

    }

}
