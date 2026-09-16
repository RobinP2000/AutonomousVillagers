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

public class OpenDoorGoal extends Goal {

    private final BlockPos doorPos;
    private final BlockPos startPos;
    private final Level level;
    private int ticks = 10;

    public OpenDoorGoal(Villager villager, BlockPos doorPos) {
        super(villager);
        this.doorPos = doorPos;
        this.startPos = getVillager().blockPosition();
        this.level = villager.level();
    }

    public BlockPos getDoorPos() {
        return doorPos;
    }

    public BlockPos getStartPos() {
        return startPos;
    }

    @Override
    protected void tick() {
        if(ticks > 0) {

        } else {
            BlockState state = level.getBlockState(getDoorPos());
            if (state.getBlock() instanceof DoorBlock door) {
                door.setOpen(getVillager(), level, state, getDoorPos(), true);
            } else if (state.getBlock() instanceof FenceGateBlock) {
                level.setBlock(getDoorPos(), state.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
                level.playSound(null, getDoorPos(), SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(getVillager(), GameEvent.BLOCK_OPEN, getDoorPos());
            }
            success();
            return;
        }
        ticks--;
    }

    @Override
    public void init() {
        getVillager().getNavigation().stop();
        getVillager().getLookControl().setLookAt(getDoorPos().getX() + 0.5D,
                getDoorPos().getY() + 0.5D, getDoorPos().getZ() + 0.5D);
    }
}
