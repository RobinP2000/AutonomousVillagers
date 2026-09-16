package de.petrofsky.autonomousvillagers.villagers.farmer;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class BreakBlockBehavior extends Behavior<Villager> {

    private BlockPos targetBlock = null;
    private int workTicks = 0;
    private boolean actionCompleted = false;

    public BreakBlockBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT
        ), 100, 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager farmer) {
        GlobalPos jobSitePos = farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (jobSitePos == null || jobSitePos.dimension() != level.dimension()) return false;

        List<BlockPos> fieldBlocks = getFieldBlocks(jobSitePos.pos());
        BlockPos villagerPos = farmer.blockPosition();

        BlockPos closestWeed = null;
        double minDistance = Double.MAX_VALUE;


        for (BlockPos pos : fieldBlocks) {
            BlockState state = level.getBlockState(pos);
            boolean isValidTarget = false;

            if ((state.getBlock() instanceof BushBlock && !(state.getBlock() instanceof CropBlock))
                    || state.is(Blocks.SNOW)  || state.is(Blocks.SNOW_BLOCK) ) {

                BlockState stateBelow = level.getBlockState(pos.below());
                if (stateBelow.is(Blocks.DIRT) || stateBelow.is(Blocks.GRASS_BLOCK)) {
                    isValidTarget = true;
                }
            }

            else if (state.is(Blocks.ICE)) {
                if (isSurroundedBySolidBlocks(level, pos)) {
                    isValidTarget = true;
                }
            }

            if (isValidTarget) {
                double dist = pos.distSqr(villagerPos);
                if (dist < minDistance) {
                    minDistance = dist;
                    closestWeed = pos;
                }
            }
        }

        if (closestWeed != null) {
            this.targetBlock = closestWeed;
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
        return (state.getBlock() instanceof BushBlock && !(state.getBlock() instanceof CropBlock))
                || state.is(Blocks.SNOW)  || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE);
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
                BlockState targetState = level.getBlockState(targetBlock);

                if (targetState.is(Blocks.ICE)) {
                    level.levelEvent(2001, targetBlock, Block.getId(targetState));

                    level.setBlock(targetBlock, Blocks.WATER.defaultBlockState(), 3);
                    level.playSound(null, targetBlock, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
                } else {

                    level.destroyBlock(targetBlock, true, farmer);
                }

                this.actionCompleted = true;
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager farmer, long gameTime) {
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.targetBlock = null;
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


    private boolean isSurroundedBySolidBlocks(ServerLevel level, BlockPos pos) {
        boolean north = level.getBlockState(pos.north()).isSolid();
        boolean south = level.getBlockState(pos.south()).isSolid();
        boolean east = level.getBlockState(pos.east()).isSolid();
        boolean west = level.getBlockState(pos.west()).isSolid();

        boolean below = level.getBlockState(pos.below()).isSolid();

        return north && south && east && west && below;
    }
}