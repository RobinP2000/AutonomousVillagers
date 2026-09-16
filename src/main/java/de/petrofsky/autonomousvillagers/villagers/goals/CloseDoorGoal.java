package de.petrofsky.autonomousvillagers.villagers.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

public class CloseDoorGoal extends Goal {

    private final BlockPos doorPos;
    private final Level level;
    private int ticks = 20;
    private boolean paused = false;
    private boolean failed = false;
    private boolean inProgress = true;

    public CloseDoorGoal(Villager villager, BlockPos doorPos) {
        super(villager);
        this.doorPos = doorPos;
        this.level = villager.level();
    }

    public BlockPos getDoorPos() {
        return doorPos;
    }

    @Override
    protected void tick() {
        if(ticks > 0) {
            getVillager().getNavigation().stop();
            getVillager().getLookControl().setLookAt(getDoorPos().getX() + 0.5D,
                    getDoorPos().getY() + 0.5D, getDoorPos().getZ() + 0.5D);
        } else {
            BlockState state = level.getBlockState(getDoorPos());
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(getVillager(), level, state, getDoorPos(), false);
            } else if (state.getBlock() instanceof FenceGateBlock) {
                level.setBlock(getDoorPos(), state.setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
                level.playSound(null, getDoorPos(), SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(getVillager(), GameEvent.BLOCK_OPEN, getDoorPos());
            }
            stop();
        }
        ticks--;
    }

    @Override
    public void init() {

    }

    @Override
    public void pause() {
        if(!isStarted() || isStopped()) return;
        this.paused = true;
    }

    @Override
    public void unpause() {
        if(!isStarted() || isStopped()) return;
        this.paused = false;
    }

    @Override
    public boolean isInProgress() {
        return this.inProgress && !isStopped() && !hasFailed();
    }

    @Override
    public boolean hasFailed() {
        return this.failed;
    }

    @Override
    public boolean isPaused() {
        return this.paused;
    }
}
