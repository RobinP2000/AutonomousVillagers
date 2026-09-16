package de.petrofsky.autonomousvillagers.villagers.farmer;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class CreateFarmlandBehavior extends Behavior<Villager> {

    private BlockPos targetBlock = null;
    private BlockPos walkTargetPos = null;
    private int workTicks = 0;
    private boolean actionCompleted = false;

    public CreateFarmlandBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT
        ), 150, 300);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager farmer) {
        GlobalPos jobSitePos = farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (jobSitePos == null || jobSitePos.dimension() != level.dimension()) return false;

        List<BlockPos> fieldBlocks = getFieldBlocks(jobSitePos.pos());
        BlockPos villagerPos = farmer.blockPosition();

        BlockPos closestValidBlock = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos pos : fieldBlocks) {
            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)) {


                if (hasAirAbove(level, pos) && hasWaterNearby(level, pos)) {

                    double dist = pos.distSqr(villagerPos);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closestValidBlock = pos;
                    }
                }
            }
        }

        if (closestValidBlock != null) {
            this.targetBlock = closestValidBlock;
            return true;
        }

        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager farmer, long gameTime) {
        this.workTicks = 0;
        this.actionCompleted = false;

        farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(targetBlock, 0.5f, 1));
    }
    @Override
    protected boolean canStillUse(ServerLevel level, Villager farmer, long gameTime) {
        if (actionCompleted || targetBlock == null) return false;


        BlockState state = level.getBlockState(targetBlock);
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK);
    }

    @Override
    protected void tick(ServerLevel level, Villager farmer, long gameTime) {
        if (targetBlock == null) return;

        if (farmer.blockPosition().closerThan(targetBlock, 2.5D)) {

            if (workTicks == 0) {
                farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                farmer.getNavigation().stop();
            }

            farmer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetBlock));

            workTicks++;

            if (workTicks == 5) {
                farmer.swing(InteractionHand.MAIN_HAND);
            }


            if (workTicks >= 15) {
                level.setBlockAndUpdate(targetBlock, Blocks.FARMLAND.defaultBlockState());
                level.playSound(null, targetBlock, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.actionCompleted = true;
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager farmer, long gameTime) {
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.targetBlock = null;
        this.walkTargetPos = null;
    }


    private boolean hasAirAbove(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.above(1)).isAir() &&
                level.getBlockState(pos.above(2)).isAir() &&
                level.getBlockState(pos.above(3)).isAir();
    }

    private boolean hasWaterNearby(ServerLevel level, BlockPos pos) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 1; y++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    if (level.getFluidState(checkPos).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<BlockPos> getFieldBlocks(BlockPos composter) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = -2; y <= 2; y++) {
                    blocks.add(composter.offset(x, y, z));
                }
            }
        }
        return blocks;
    }

    private BlockPos getClosestAdjacentBlock(ServerLevel level, BlockPos target, BlockPos villagerPos) {
        BlockPos best = null;
        double minDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adj = target.relative(dir);
            if (level.getBlockState(adj).isAir() && level.getBlockState(adj.above()).isAir()) {
                double dist = adj.distSqr(villagerPos);
                if (dist < minDistance) {
                    minDistance = dist;
                    best = adj;
                }
            }
        }
        return best != null ? best : target;
    }
}