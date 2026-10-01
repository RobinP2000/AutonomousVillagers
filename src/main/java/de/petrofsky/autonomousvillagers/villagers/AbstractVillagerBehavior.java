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
    private int ticks = 0;
    private int totalTicks = 0;

    private long lastBeginCheck = 0;
    private final int checkBeginInterval;
    private long lastContinueCheck = 0;
    private final int checkContinueInterval;

    public AbstractVillagerBehavior(Map<MemoryModuleType<?>, MemoryStatus> p_22528_, int maxDuration,
                                    int checkBeginInterval, int checkContinueInterval) {
        super(p_22528_, maxDuration);
        this.checkBeginInterval = checkBeginInterval;
        this.checkContinueInterval = checkContinueInterval;
    }

    public AbstractVillagerBehavior(Map<MemoryModuleType<?>, MemoryStatus> p_22528_, int maxDuration) {
        this(p_22528_, maxDuration, 0, 0);
    }

    @Override
    protected final boolean checkExtraStartConditions(@NotNull ServerLevel level, @NotNull Villager villager) {
        long now = System.currentTimeMillis();
        if (now - this.lastBeginCheck <= this.checkBeginInterval) {
            return false;
        }
        this.lastBeginCheck = now;
        return canBegin(level, villager);
    }

    protected abstract boolean canBegin(@NotNull ServerLevel level, @NotNull Villager villager);

    @Override
    protected final boolean canStillUse(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        long now = System.currentTimeMillis();
        if(now - this.lastContinueCheck <= this.checkContinueInterval) {
            return true;
        }
        this.lastContinueCheck = now;
        return canContinue(level, villager, gameTime);
    }

    protected abstract boolean canContinue(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime);

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

    public MoveToGoal moveToNow(Villager villager, BlockPos targetPos) {
        MoveToGoal moveToGoal = new MoveToGoal(villager, targetPos);
        System.out.println("Move from: " + villager.blockPosition() + " to " + targetPos);
        setGoal(moveToGoal);
        return moveToGoal;

    }

    public MoveToGoal moveTo(Villager villager, BlockPos targetPos) {
        return new MoveToGoal(villager, targetPos);

    }

    public BreakBlockGoal breakBlockNow(Villager villager, BlockPos targetPos) {
        BreakBlockGoal breakBlockGoal = new BreakBlockGoal(villager, targetPos);
        setGoal(breakBlockGoal);
        return breakBlockGoal;
    }

    public BreakBlockGoal breakBlock(Villager villager, BlockPos targetPos) {
        return new BreakBlockGoal(villager, targetPos);
    }

    public PlaceBlockGoal placeBlockNow(Villager villager, BlockPos target, Block block) {
        PlaceBlockGoal placeBlockGoal = new PlaceBlockGoal(villager, target, block);
        setGoal(placeBlockGoal);
        return placeBlockGoal;
    }

    public PlaceBlockGoal placeBlock(Villager villager, BlockPos target, Block block) {
        return new PlaceBlockGoal(villager, target, block);
    }

    public static boolean blockInTouchRange(Villager villager, BlockPos target) {
        Vec3 eye = villager.getEyePosition();
        double dx = eye.x - (target.getX() + 0.5);
        double dy = eye.y - (target.getY() + 0.5);
        double dz = eye.z - (target.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (DEFAULT_BLOCK_RANGE * DEFAULT_BLOCK_RANGE);
    }
}
