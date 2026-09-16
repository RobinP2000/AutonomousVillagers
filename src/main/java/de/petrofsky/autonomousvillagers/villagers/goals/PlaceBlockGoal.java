package de.petrofsky.autonomousvillagers.villagers.goals;

import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.UnaryOperator;

public class PlaceBlockGoal extends Goal{

    private final Level level;
    private final BlockPos placePos;
    private int ticks = 20;
    private final Block block;
    private MoveToGoal moveToGoal;
    private boolean allowMovement = true;

    public PlaceBlockGoal(Villager villager, BlockPos placePos, Block block) {
        super(villager);
        this.placePos = placePos;
        this.level = villager.level();
        this.block = block;
    }

    public PlaceBlockGoal movement(UnaryOperator<MoveToGoal> configurer) {
        this.moveToGoal = configurer.apply(new MoveToGoal(getVillager(), getPlacePos()));
        return this;
    }

    public PlaceBlockGoal noMovement() {
        this.allowMovement = false;
        return this;
    }

    @Override
    protected void tick() {
        if(this.moveToGoal != null) {
            if(this.moveToGoal.isInProgress()) {
                this.moveToGoal.executeTick();
                return;
            } else if(this.moveToGoal.hasFailed()) {
                fail();
                return;
            }
            this.moveToGoal = null;
        }

        BlockState placePosState = getLevel().getBlockState(getPlacePos());
        if((!placePosState.isAir() && ! placePosState.getCollisionShape(getLevel(), getPlacePos()).isEmpty())
            || ! AbstractVillagerBehavior.blockInTouchRange(getVillager(), getPlacePos())
                || ! InventoryUtils.hasAny(getVillager(), getBlock().asItem())) {
            fail();
            System.out.println("Failed");
            return;
        }

        getVillager().getNavigation().stop();
        getVillager().getLookControl().setLookAt(getPlacePos().getX() + 0.5D,
                getPlacePos().getY() + 0.5D, getPlacePos().getZ() + 0.5D);

        if (this.ticks > 0) {
            this.ticks--;
            return;
        }

        getLevel().setBlock(getPlacePos(), getBlock().defaultBlockState(), 3);
        SoundType soundType = getBlock().defaultBlockState().getSoundType(getLevel(), getPlacePos(), getVillager());
        level.playSound(
                getVillager(),
                getPlacePos(),
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F
        );
        InventoryUtils.decrease(getVillager(), getBlock().asItem());
        success();
    }

    @Override
    public void init() {
        if(AbstractVillagerBehavior.blockInTouchRange(getVillager(), getPlacePos())) {
            return;
        }
        if(!this.allowMovement) {
            fail();
        } else if(this.moveToGoal == null) {
            this.moveToGoal = new MoveToGoal(getVillager(), getPlacePos()).begin();
        } else {
            this.moveToGoal.begin();
        }
    }

    public BlockPos getPlacePos() {
        return this.placePos;
    }

    public Block getBlock() {
        return block;
    }

    public Level getLevel() {
        return this.level;
    }
}
