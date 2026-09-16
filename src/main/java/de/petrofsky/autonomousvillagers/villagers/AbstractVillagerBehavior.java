package de.petrofsky.autonomousvillagers.villagers;

import de.petrofsky.autonomousvillagers.villagers.goals.BreakBlockGoal;
import de.petrofsky.autonomousvillagers.villagers.goals.Goal;
import de.petrofsky.autonomousvillagers.villagers.goals.MoveToGoal;
import de.petrofsky.autonomousvillagers.villagers.goals.PlaceBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public abstract class AbstractVillagerBehavior extends Behavior<Villager> {

    public static final float DEFAULT_WALK_SPEED = 0.5f;
    public static final int DEFAULT_BLOCK_RANGE = 4;

    protected Goal currentGoal;

    public AbstractVillagerBehavior(Map<MemoryModuleType<?>, MemoryStatus> p_22528_, int maxDuration) {
        super(p_22528_, maxDuration);
    }

    @Override
    protected final void tick(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        if(hasGoal()) {
            Goal goal = getGoal();

            if(goal.isInProgress()) {
                goal.executeTick();
                return;
            } else if(!goal.hasFailed() && goal.hasNext()) {
                this.currentGoal = goal.getNext();
                this.currentGoal.start();
                return;
            }
        }
        executeBehavior(level, villager, gameTime);
    }

    protected abstract void executeBehavior(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime);

    public boolean hasGoal() {
        return currentGoal != null;
    }

    public Goal getGoal() {
        return this.currentGoal;
    }

    public void setGoal(Goal goal) {
        this.currentGoal = goal;
    }

    public void clearGoal() {
        this.currentGoal = null;
    }

    public MoveToGoal moveToNow(Villager villager, BlockPos moveToPos) {
        MoveToGoal moveToGoal = new MoveToGoal(villager, moveToPos);
        setGoal(moveToGoal);
        return moveToGoal;

    }

    public MoveToGoal moveTo(Villager villager, BlockPos target) {
        return new MoveToGoal(villager, target);

    }

    public void breakBlock(Villager villager, BlockPos target, List<TagKey<Block>> allowedTags) {
        breakBlock(villager, target, allowedTags, null, DEFAULT_BLOCK_RANGE - 2);
    }

    public void breakBlock(Villager villager, BlockPos target, List<TagKey<Block>> allowedTags, int withinDistance) {
        breakBlock(villager, target, allowedTags, null, withinDistance);
    }

    public void breakBlock(Villager villager, BlockPos target,
                           List<TagKey<Block>> allowedTags, List<Block> allowedBlocks, int withinDistance) {
        BreakBlockGoal breakBlockGoal = new BreakBlockGoal(villager, target, allowedTags, allowedBlocks);
        if(blockInTouchRange(villager, target)) {
            currentGoal = breakBlockGoal;
        } else {
            currentGoal = new MoveToGoal(villager, target, withinDistance,
                    DEFAULT_WALK_SPEED, breakBlockGoal);
        }
        currentGoal.start();
    }

    public void placeBlock(Villager villager, BlockPos target, Block block) {
        PlaceBlockGoal placeBlockGoal = new PlaceBlockGoal(villager, target, block);
        if(blockInTouchRange(villager, target)) {
            currentGoal = placeBlockGoal;
        } else {
            currentGoal = new MoveToGoal(villager, target, DEFAULT_BLOCK_RANGE - 2,
                    DEFAULT_WALK_SPEED, placeBlockGoal);
        }
        currentGoal.start();
    }

    public static boolean blockInTouchRange(Villager villager, BlockPos target) {
        Vec3 eye = villager.getEyePosition();
        double dx = eye.x - (target.getX() + 0.5);
        double dy = eye.y - (target.getY() + 0.5);
        double dz = eye.z - (target.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (DEFAULT_BLOCK_RANGE * DEFAULT_BLOCK_RANGE);
    }
}
