package de.petrofsky.autonomousvillagers.villagers.goals;

import de.petrofsky.autonomousvillagers.utils.*;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.*;

public class MoveToGoal extends Goal {

    private final BlockPos target;
    private ShortPath shortPath;
    private final Level level;
    private int withinDistance = AbstractVillagerBehavior.DEFAULT_BLOCK_RANGE - 2;
    private float speed = AbstractVillagerBehavior.DEFAULT_WALK_SPEED;
    private boolean inTouchRange = false;
    private int tick = 19;
    private int index = 0;
    private Vec3 lastPosition;
    private Goal currentTodo;
    private HashMap<BlockPos, NodeActionType> currentActions;
    private final HashSet<Block> breakableBlocks = new HashSet<>();
    private final HashSet<TagKey<Block>> breakableTags = new HashSet<>();
    private final HashSet<Item> placeableItems = new HashSet<>();
    private final HashSet<TagKey<Item>> placeableTags = new HashSet<>();
    private boolean closestPossible = false;
    private long searchLimit = 200;
    private int maxRetries = 2;

    public MoveToGoal(Villager villager, BlockPos target) {
        super(villager);
        this.target = target;
        this.level = villager.level();
    }

    public MoveToGoal withinDistance(int distance) {
        this.withinDistance = distance;
        return this;
    }

    public MoveToGoal walkSpeed(float speed) {
        this.speed = speed;
        return this;
    }

    public MoveToGoal breakable(Block... block) {
        this.breakableBlocks.addAll(Set.of(block));
        return this;
    }

    @SafeVarargs
    public final MoveToGoal breakable(TagKey<Block>... tag) {
        this.breakableTags.addAll(Set.of(tag));
        return this;
    }

    public MoveToGoal placeable(Item... item) {
        this.placeableItems.addAll(Set.of(item));
        return this;
    }

    @SafeVarargs
    public final MoveToGoal placeable(TagKey<Item>... tag) {
        this.placeableTags.addAll(Set.of(tag));
        return this;
    }

    public MoveToGoal nextGoal(Goal goal) {
        setNext(goal);
        return this;
    }

    public MoveToGoal closeAsPossible() {
        this.closestPossible = true;
        return this;
    }

    public MoveToGoal searchLimit(long blocks) {
        this.searchLimit = blocks;
        return this;
    }

    public MoveToGoal inTouchRange() {
        this.inTouchRange = true;
        return this;
    }

    public MoveToGoal begin() {
        start();
        return this;
    }

    private void calculate(BlockPos target) {
        long before = System.currentTimeMillis();
        this.shortPath = new ShortPath(getLevel(), getVillagerPos(), target, getWithinDistance(),
                breakableBlocks, breakableTags, InventoryUtils.count(getVillager(), ItemTags.DIRT),
                searchLimit, closestPossible);
        PerformanceUtils.timeMeasured("Path calculation", before);
    }

    private BlockPos getVillagerPos() {
        BlockPos villagerPos = getVillager().blockPosition();
        BlockPos belowVillagerPos = villagerPos.below();
        BlockState belowVillagerPosState = getLevel().getBlockState(belowVillagerPos);
        if(getVillager().onGround() && belowVillagerPosState.getCollisionShape(getLevel(), belowVillagerPos).isEmpty()) {
            villagerPos = villagerPos.below();
        }

        return villagerPos;
    }

    public BlockPos getTarget() {
        return target;
    }

    public Level getLevel() {
        return level;
    }

    public int getWithinDistance() {
        return withinDistance;
    }

    @Override
    protected void tick() {
        System.out.println("movement tick: " + tick);
        tick++;
        if(tick >= 20) {
            tick = 0;
        }

        if(inTouchRange && AbstractVillagerBehavior.blockInTouchRange(getVillager(), getTarget())) {
            success();
            System.out.println("in touch range success movement:");
            return;
        }

        if(currentTodo != null && currentTodo.isInProgress()) {
            currentTodo.executeTick();
            System.out.println("execute todo in movement:");
            return;
        } else if(currentTodo != null) {
            System.out.println("is door todo in movement:");
            if(currentTodo instanceof OpenDoorGoal openDoorGoal) {
                long distance = BlockGeometry3DUtils.getDistanceSquared(getVillager().blockPosition(),
                        openDoorGoal.getDoorPos());
                long distanceStart = BlockGeometry3DUtils.getDistanceSquared(getVillager().blockPosition(),
                        openDoorGoal.getStartPos());
                if(distance >= 1) {
                    if(distanceStart >= 2) {
                        currentTodo = new CloseDoorGoal(getVillager(), openDoorGoal.getDoorPos());
                        currentTodo.start();
                        return;
                    }
                }
            } else {
                currentTodo = null;
            }
        }

        ArrayList<Node> path = shortPath.getPath();
        int size = path.size();
        if(index >= size) {
            System.out.println("move reach: " + this.shortPath.isReachable());
            System.out.println("move limit: " + this.shortPath.searchLimitReached());
            if(!this.shortPath.isReachable() && this.shortPath.searchLimitReached() && !(inTouchRange
                    && AbstractVillagerBehavior.blockInTouchRange(getVillager(), getTarget()))) {
                calculate(getTarget());
                if(this.shortPath.getPath().size() < 3 && this.shortPath.searchLimitReached()) {
                    fail();
                }
                index = 0;
                return;
            }
            System.out.println("normal Reachable: " + this.shortPath.isReachable());
            if( ! this.shortPath.isReachable() && !(inTouchRange
                    && AbstractVillagerBehavior.blockInTouchRange(getVillager(), getTarget()))
                ) fail(); else success();
            return;
        }
        if(tick % 10 == 0) {
            int lastIndex = size - 1;
            int currentIndex = Math.max(0, index-1);
            int toIndex = index + 2;

            while (currentIndex < lastIndex && currentIndex <= toIndex) {
                if(shortPath.isNextValid(currentIndex)) {
                    currentIndex++;
                    continue;
                }
                int nextValidIndex = currentIndex + 1;
                while (nextValidIndex < lastIndex && ! shortPath.isNextValid(nextValidIndex)) {
                    nextValidIndex++;
                }


                int retry = 0;
                boolean successful = false;
                int range = nextValidIndex - currentIndex;
                int targetIndex = nextValidIndex;
                while (!successful && retry <= this.maxRetries && targetIndex < lastIndex) {
                    if(retry == this.maxRetries) {
                        calculate(getTarget());

                    }
                    targetIndex = Math.min(currentIndex + range * 2, lastIndex);
                    successful = shortPath.update(currentIndex, targetIndex);
                    retry++;
                }

                if(!successful) {
                    fail();
                    return;
                }
                break;
            }

        }

        Node nextNode = shortPath.getPath().get(index);
        if(currentActions == null && !nextNode.getActions().isEmpty()) {
            currentActions = new HashMap<>();
            for(BlockPos blockPos : nextNode.getActions().keySet()) {
                NodeActionType type = nextNode.getActions().get(blockPos);

                if(type != NodeActionType.NONE) {
                    currentActions.put(blockPos, type);
                }
            }
        }

        if(currentActions != null && ! currentActions.isEmpty()) {
            Optional<BlockPos> openPosResult = currentActions.keySet().stream()
                    .filter(key -> currentActions.get(key) == NodeActionType.OPENED).findAny();
            Optional<BlockPos> breakPosResult = currentActions.keySet().stream()
                    .filter(key -> currentActions.get(key) == NodeActionType.BROKEN).findAny();
            BlockPos changeTarget;
            if(openPosResult.isPresent()) {
                changeTarget = openPosResult.get();
                currentTodo = new OpenDoorGoal(getVillager(), changeTarget);
            } else if(breakPosResult.isPresent()) {
                changeTarget = breakPosResult.get();
                currentTodo = new BreakBlockGoal(getVillager(), changeTarget);
            } else {
                changeTarget = currentActions.keySet().stream().findFirst().get();
                currentTodo = new PlaceBlockGoal(getVillager(), changeTarget, Blocks.DIRT);
            }
            currentActions.remove(changeTarget);
            currentTodo.start();
            return;
        } else {
            currentActions = null;
        }


        BlockPos nextPos = nextNode.getPos();
        getVillager().getLookControl().setLookAt(nextPos.getX() + 0.5D,
                nextPos.getY() + 1.5D, nextPos.getZ() + 0.5D);
        getVillager().getMoveControl().setWantedPosition(nextPos.getX() + 0.5D,
                nextPos.getY(), nextPos.getZ() + 0.5D, speed);



        BlockPos currentPos = nextNode.getParentPos() != null ? nextNode.getParentPos().getPos() : nextPos ;

            BlockState currentState = level.getBlockState(currentPos);
            BlockState aboveCurrentState = level.getBlockState(currentPos.above());
            BlockState belowCurrentState = level.getBlockState(currentPos.below());
            if(tick % 3 == 0 &&
                    (currentState.getFluidState().is(Tags.Fluids.WATER) && (
                    !aboveCurrentState.getFluidState().is(Tags.Fluids.WATER) ||
                            (nextNode.getParentPos() != null && nextNode.getParentPos().getPos().getY() < nextPos.getY()))) ||
                    (belowCurrentState.getFluidState().is(Tags.Fluids.WATER)) ) {
                getVillager().setSwimming(true);
                if(currentState.getFluidState().is(Tags.Fluids.WATER)) {
                    getVillager().jumpInFluid(currentState.getFluidState().getFluidType());
                } else {
                    getVillager().jumpInFluid(belowCurrentState.getFluidState().getFluidType());
                }

            } else if (getVillager().isSwimming()) {
                getVillager().setSwimming(false);
            }



        int dBlockX = getVillager().getBlockX() - nextPos.getX();
        int dBlockZ = getVillager().getBlockZ() - nextPos.getZ();
        int dBlockY = Math.abs(getVillager().getBlockY() - nextPos.getY());
        double dX = getVillager().getX() - (nextPos.getX() + 0.5D);
        double dZ = getVillager().getZ() - (nextPos.getZ() + 0.5D);
        double horizontalDistanceSquared = dX * dX + dZ * dZ;
        int blockHorizontalDistanceSquared = dBlockX * dBlockX + dBlockZ * dBlockZ;

        BlockPos villagerPos = getVillager().blockPosition();
        BlockState villagerPosState = level.getBlockState(villagerPos);
        boolean inWater = villagerPosState.getFluidState().is(Tags.Fluids.WATER);
        double dY = Math.abs(getVillager().getY() - nextPos.getY());
        if(index == getShortPath().getPath().size() - 1 ) {
            if(blockHorizontalDistanceSquared < 1 && dY < 0.5) this.index++;
        } else if (inWater && blockHorizontalDistanceSquared < 1) {
            this.index++;
        } else if(!inWater && horizontalDistanceSquared < 0.8D && dY <= 1.5D) {
            this.index++;
        }

        if(tick % 10 == 0) {
            if(this.lastPosition != null && this.lastPosition.equals(getVillager().position())) {
                getVillager().getJumpControl().jump();
            }
        }
        this.lastPosition = getVillager().position();
    }

    @Override
    public void init() {
        getVillager().getNavigation().stop();
        calculate(getTarget());
        System.out.println("Start movement");
    }

    public ShortPath getShortPath() {
        return this.shortPath;
    }
}
