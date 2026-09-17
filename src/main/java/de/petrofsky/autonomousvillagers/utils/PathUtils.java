package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PathUtils {

    protected static boolean isBreakable(ShortPath shortPath,
            Node origin, BlockPos target, HashSet<Block> breakableBlocks, HashSet<TagKey<Block>> breakableTags) {
        BlockState targetState = shortPath.getState(target);
        return origin.getActionType(target) == null
                && shortPath.getFullBlock(target)
                && (breakableBlocks.contains(targetState.getBlock())
                || targetState.getTags().anyMatch(breakableTags::contains));
    }

    protected static boolean requiresBreak(ShortPath shortPath, Node originNode, BlockPos target) {
        NodeActionType type = originNode.getActionType(target);
        return ((type == null || type == NodeActionType.NONE) && ! shortPath.hasEmptyCollusion(target)
                && !isWater(shortPath, target)
                || type == NodeActionType.PLACED);
    }

    protected static boolean isPlaceable(ShortPath shortPath, Node origin, BlockPos target, long blocksLeft) {
        return origin.getActionType(target) == null && blocksLeft > 0 &&
               countSolidNeighbor(shortPath, origin, target) > 0;
    }

    protected static boolean requiresPlacement(ShortPath shortPath, Node originNode, BlockPos target) {
        NodeActionType type = originNode.getActionType(target);
        return ((type == null || type == NodeActionType.NONE) && shortPath.hasEmptyCollusion(target)
                && !isWater(shortPath, target))
                || type == NodeActionType.BROKEN;
    }

    protected static int countSolidNeighbor(ShortPath shortPath, Node origin, BlockPos blockPos) {
        List<BlockPos> solidNeighbors = new ArrayList<>(List.of(blockPos.below(), blockPos.north(),
                blockPos.east(), blockPos.south(), blockPos.west()));
        solidNeighbors.removeIf(neighbor -> !shortPath.getFullBlock(neighbor)
                && origin.getActionType(neighbor) != NodeActionType.PLACED);
        return solidNeighbors.size();
    }

    protected static int countCollusionFreeNeighbor(ShortPath shortPath, Node origin, BlockPos blockPos) {
        List<BlockPos> solidNeighbors = new ArrayList<>(List.of(blockPos.north(),
                blockPos.east(), blockPos.south(), blockPos.west()));
        solidNeighbors.removeIf(neighbor -> !shortPath.hasEmptyCollusion(neighbor)
                && origin.getActionType(neighbor) != NodeActionType.BROKEN);
        return solidNeighbors.size();
    }

    protected static boolean isSolid(Level level, Node origin, BlockPos blockPos) {
        return BlockDataUtils.isSolid(level, blockPos)
               || (origin.getActionType(blockPos) != null && origin.getActionType(blockPos) != NodeActionType.PLACED);
    }

    protected static boolean isOperableDoor(ShortPath shortPath, BlockPos blockPos) {
        BlockState blockState = shortPath.getState(blockPos);
        if (blockState.getBlock() instanceof DoorBlock door) return door.type().canOpenByHand();
        if (blockState.getBlock() instanceof FenceGateBlock) return true;
        return false;
    }

    protected static boolean isOpen(ShortPath shortPath, Node origin, BlockPos pos) {
        if (origin.getActionType(pos) == NodeActionType.OPENED) return true;
        BlockState state =  shortPath.getState(pos);
        return state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN);
    }

    protected static boolean isLowerHalf(ShortPath shortPath, BlockPos pos) {
        BlockState state = shortPath.getState(pos);
        return !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }
    protected  static boolean isHazardClose(ShortPath shortPath, BlockPos blockPos) {
        List<BlockPos> positions = List.of(blockPos, blockPos.east(), blockPos.south(),
                blockPos.west(), blockPos.north(), blockPos.above(), blockPos.below());
        return positions.stream().anyMatch(pos -> isHazard(shortPath, pos));
    }

    protected static boolean isHazard(ShortPath shortPath, BlockPos pos) {
        BlockState state = shortPath.getState(pos);

        if (state.getFluidState().is(Tags.Fluids.LAVA)) {
            return true;
        }

        if (state.is(BlockTags.FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)) {
            return true;
        }

        return false;
    }

    protected static boolean isWater(ShortPath shortPath, BlockPos pos) {
        BlockState state = shortPath.getState(pos);

        return state.getFluidState().is(Tags.Fluids.WATER);
    }
}
