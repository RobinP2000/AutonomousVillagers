package de.petrofsky.autonomousvillagers.villagers.forester;

import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import de.petrofsky.autonomousvillagers.utils.BlockDataUtils;
import de.petrofsky.autonomousvillagers.utils.BlockGeometry3DUtils;
import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.utils.geometry.Plane;
import de.petrofsky.autonomousvillagers.utils.geometry.ShapeUtils;
import de.petrofsky.autonomousvillagers.utils.pathfinding.SurfaceFinder;
import de.petrofsky.autonomousvillagers.village.Village;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import de.petrofsky.autonomousvillagers.villagers.goals.BreakBlockGoal;
import de.petrofsky.autonomousvillagers.villagers.goals.MoveToGoal;
import de.petrofsky.autonomousvillagers.villagers.goals.PlaceBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class ChopTreeBehavior extends AbstractVillagerBehavior {

    record TreeBlocks(ArrayList<BlockPos> logs) {}

    public enum Phase {
       CHOP, SCAFFOLD_BUILD, SCAFFOLD_GAIN, SCAFFOLD_DESTROY, SCAFFOLD_RETURN
    }

    private static final int MAX_DURATION = 6000;

    private List<ItemEntity> itemsAround = new ArrayList<>();
    private ForesterBlockEntity foresterBlockEntity;

    private BlockPos nextBlockTarget;

    public ChopTreeBehavior() {
        super(Map.of(
                MemoryModuleType.JOB_SITE,   MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION, 10_000, 1_000);
    }

    private boolean canContinueCurrentJob(ServerLevel level, Villager villager) {
        int dirtCount = InventoryUtils.count(villager.getInventory(), Items.DIRT);
        int logCount = 0;
        int emptySlots = InventoryUtils.countEmptySlots(villager.getInventory());
        if(this.foresterBlockEntity.getNextBlockTarget() != null) {
            BlockPos blockPos = this.foresterBlockEntity.getNextBlockTarget();
            BlockState blockState = level.getBlockState(blockPos);
            if(blockState.is(BlockTags.LOGS)) {
                Item log = blockState.getBlock().asItem();
                logCount = InventoryUtils.count(villager.getInventory(), log);
            }
        }
        Phase phase = this.foresterBlockEntity.getPhase();
        boolean hasPendingLogs = this.foresterBlockEntity.hasPendingLogs();
        if(phase == Phase.CHOP && hasPendingLogs
                && (logCount % 64) == 0 && emptySlots == 0 ) {
            return false;
        } else if (phase == Phase.SCAFFOLD_GAIN && hasPendingLogs
                && !hasEnoughScaffoldBlocks(villager) && (dirtCount % 64) == 0 && emptySlots == 0) {
            return false;
        } else if (phase == Phase.SCAFFOLD_DESTROY && this.foresterBlockEntity.hasScaffoldsPlaced()
                && (((dirtCount % 64) == 0 && emptySlots == 0))) {
            return false;
        }
        return phase != Phase.SCAFFOLD_RETURN || this.foresterBlockEntity.hasScaffoldsBroken();
    }

    @Override
    protected boolean canBegin(@NotNull ServerLevel level, @NotNull Villager villager) {
        ForesterBlockEntity be = Forester.getBlockEntity(level, villager);
        if (be == null) return false;
        this.foresterBlockEntity = be;

        if((be.hasTreeTarget() && canContinueCurrentJob(level, villager))) {
            return true;
        }

        List<BlockPos> targets = new SurfaceFinder(level, be.getBlockPos(), 8, 11)
                .find(BlockTags.LOGS).between(villager.blockPosition(), 1, 10)
                .breakable(BlockTags.LEAVES).scan(true);
        if(!targets.isEmpty()) {
            TreeBlocks tree = scanTree(level, targets.getFirst());
            tree.logs().sort(Comparator.comparingInt(BlockPos::getY));
            this.foresterBlockEntity.addAllPendingLog(tree.logs());
            selectNextTarget();
            this.foresterBlockEntity.setPhase(Phase.CHOP);
        }

        return false;
    }

    @Override
    protected void start(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        AttributeInstance followRange = villager.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(48.0D);
            villager.getNavigation().setMaxVisitedNodesMultiplier(4.0f);
        }
        if (this.foresterBlockEntity == null) return;

        if(this.foresterBlockEntity.hasTreeTarget()) {
            if(this.foresterBlockEntity.hasPendingLogs() && this.foresterBlockEntity.getNextBlockTarget() == null) {
                this.foresterBlockEntity.setNextBlockTarget(this.foresterBlockEntity.getFirstPendingLog());
            }
            return;
        }
    }


    protected void executeBehavior(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        System.out.println("Phase: " + this.foresterBlockEntity.getPhase());
        switch (this.foresterBlockEntity.getPhase()) {
            case CHOP            -> tickChop(level, villager);
            case SCAFFOLD_GAIN   -> tickScaffoldGain(level, villager);
            case SCAFFOLD_BUILD -> tickScaffoldPlacement(level, villager);
            case SCAFFOLD_DESTROY -> tickScaffoldDestruction(level, villager);
            case SCAFFOLD_RETURN -> tickScaffoldReturn(level, villager);
        }
    }

    @Override
    protected boolean canContinue(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        return this.foresterBlockEntity.hasTreeTarget() && canContinueCurrentJob(level, villager);
    }

    @Override
    protected void stop(@NotNull ServerLevel level, Villager villager, long gameTime) {
        clearGoal();
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }


    // ── Phase: CHOP ───────────────────────────────────────────────────────────

    private void tickChop(ServerLevel level, Villager villager) {
        if(!this.foresterBlockEntity.hasPendingLogs()) {
           if(this.itemsAround == null || this.itemsAround.isEmpty()) {
                this.foresterBlockEntity.setPhase(Phase.SCAFFOLD_DESTROY);
            } else if(hasGoal() && getGoal() instanceof MoveToGoal moveToGoal && moveToGoal.isStopped()
                        && moveToGoal.getTarget().equals(itemsAround.getFirst().blockPosition())) {
                itemsAround.removeFirst();
            } else {
                ItemEntity itemTarget = itemsAround.getFirst();
                moveToNow(villager, itemTarget.blockPosition()).withinDistance(0).start();
            }
            return;
        }

        BlockPos targetPosition = this.nextBlockTarget;
        if (targetPosition == null) {
            selectNextTarget();
            return;
        }

        if (! level.getBlockState(targetPosition).is(BlockTags.LOGS) && ! hasGoal()) {
            System.out.println("Chop2");
            this.foresterBlockEntity.removePendingLog(targetPosition);
            this.nextBlockTarget = null;
            selectNextTarget();
            return;
        }

        if(!hasGoal()) {
            System.out.println("Chop3");
            breakBlockNow(villager, targetPosition)
                    .breakableBarrier(BlockTags.LOGS, BlockTags.LEAVES, BlockTags.FLOWERS, BlockTags.TALL_FLOWERS)
                    .breakable(BlockTags.LOGS)
                    .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES)
                            .closeAsPossible().inTouchRange()).start();
        } else if(getGoal() instanceof BreakBlockGoal breakBlockGoal) {
            System.out.println("Chop4");
            if(!breakBlockGoal.hasFailed()) {
                System.out.println("Chop4.2");
                if(this.foresterBlockEntity.hasPendingLog(breakBlockGoal.getBreakPos())) {
                    if(breakBlockGoal.getBreakPos().equals(this.nextBlockTarget)) {
                        this.nextBlockTarget = null;
                        System.out.println("Chop4.3");
                    }
                    this.foresterBlockEntity.removePendingLog(breakBlockGoal.getBreakPos());

                    if(!this.foresterBlockEntity.hasPendingLogs()) {
                        this.itemsAround = BlockDataUtils.getItemsAround(level,
                                villager.getBoundingBox(), 8, ItemTags.LOGS);
                    } else {
                        selectNextTarget();
                    }
                    clearGoal();
                } else {
                    breakBlockNow(villager, targetPosition)
                            .breakableBarrier(BlockTags.LOGS, BlockTags.LEAVES, BlockTags.FLOWERS, BlockTags.TALL_FLOWERS)
                            .breakable(BlockTags.LOGS)
                            .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES)
                                    .inTouchRange().closeAsPossible()).start();
                }
            } else {
                moveToNow(villager, targetPosition).closeAsPossible()
                        .breakable(BlockTags.LEAVES).inTouchRange().start();
            }
        } else if(getGoal() instanceof MoveToGoal goal && goal.hasFailed()) {
            clearGoal();
            this.foresterBlockEntity.setPhase(Phase.SCAFFOLD_BUILD);
        } else {
            clearGoal();
        }
    }

    public void tickScaffoldGain(ServerLevel level, Villager villager) {
        if(hasGoal()) {
            if(getGoal() instanceof BreakBlockGoal breakBlockGoal) {
                System.out.println("Failed: " + breakBlockGoal.hasFailed());
                System.out.println("TargetBroken: " + breakBlockGoal.isTargetBroken());
                if(! breakBlockGoal.hasFailed() && breakBlockGoal.isTargetBroken()) {
                    this.foresterBlockEntity.addScaffoldBroken(breakBlockGoal.getTargetPos());
                    this.itemsAround = BlockDataUtils.getItemsAround(level,
                            villager.getBoundingBox(), 8, ItemTags.DIRT);
                }
                this.foresterBlockEntity.removeScaffoldCollectionTarget(breakBlockGoal.getBreakPos());
            }
            clearGoal();
        }


        if(!this.itemsAround.isEmpty()) {
            ItemEntity itemTarget = this.itemsAround.getFirst();
            moveToNow(villager, itemTarget.blockPosition()).withinDistance(0).start();
            this.itemsAround.remove(itemTarget);
            return;
        }

        if (hasEnoughScaffoldBlocks(villager)) {
            this.foresterBlockEntity.setPhase(Phase.SCAFFOLD_BUILD);
            return;
        }

        BlockPos targetDirt = findNearestBreakableDirt(villager, level);
        if (targetDirt == null) {
            villager.getNavigation().stop();
            return;
        }

        breakBlockNow(villager, targetDirt)
                .breakableBarrier(BlockTags.LEAVES, BlockTags.FLOWERS, BlockTags.TALL_FLOWERS)
                .breakable(BlockTags.DIRT)
                .multipleBlocks()
                .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES)
                        .withinDistance(0).cheapBreak())
                .start();
    }


    private BlockPos findNearestBreakableDirt(Villager villager, ServerLevel level) {
        if(this.foresterBlockEntity.hasScaffoldCollectionTargets()) {
            BlockPos next = this.foresterBlockEntity.getFirstScaffoldCollectionTarget();
            this.foresterBlockEntity.removeScaffoldCollectionTarget(next);
            return next;
        }

        BlockPos villagerPos = villager.blockPosition();

        final BlockPos distanceTo;
        if(this.foresterBlockEntity.hasScaffoldsPlaced()) {
            distanceTo = this.foresterBlockEntity.getFirstScaffoldPlaced().getKey();
        } else {
            distanceTo = null;
        }


        List<BlockPos> centerSelections = ShapeUtils.circle(villagerPos, 9)
                .on(Plane.XZ, villagerPos.getY()).between(5).toList();
        if(distanceTo != null) {
            centerSelections = centerSelections.stream().filter(blockPos ->
                    !BlockGeometry3DUtils.withinDistance(blockPos, distanceTo, 6)).toList();
        }
        if(centerSelections.isEmpty()) {
            System.out.println("Dirt 2");
            return null;
        }
        BlockPos blockPos = centerSelections.getFirst();
        List<BlockPos> field = ShapeUtils.circle(blockPos, 4).on(Plane.XZ, blockPos.getY()).toList();
        List<BlockPos> targets = BlockDataUtils.getHighestPos(level, field, villagerPos, 10);
        System.out.println("Dirt 2.5: " + targets.size());

        targets = BlockDataUtils.getWithAnyTags(level, targets, BlockTags.DIRT);
        targets = BlockDataUtils.getSolidBelow(level, targets);
        targets = BlockDataUtils.getNonHoles(level, targets, true);

        if(targets.isEmpty()) {
            System.out.println("Dirt 3");
            return null;
        } else {
            System.out.println("Dirt 4");
            if(distanceTo != null) {
                this.foresterBlockEntity.addAllScaffoldCollectionTargets(targets);
            } else {
                this.foresterBlockEntity.addAllScaffoldCollectionTargets
                        (targets.subList(0, Math.min(4, targets.size() - 1)));
            }
            return this.foresterBlockEntity.hasScaffoldCollectionTargets() ?
                    this.foresterBlockEntity.getFirstScaffoldCollectionTarget() : null;
        }
    }

    public void tickScaffoldPlacement(ServerLevel level, Villager villager) {
        if(getGoal() instanceof PlaceBlockGoal placeBlockGoal) {
            System.out.println("successful placement: " + placeBlockGoal.isSuccess());
            if(placeBlockGoal.isSuccess()) {
                if(!this.foresterBlockEntity.hasScaffoldsPlaced())
                    this.foresterBlockEntity.clearScaffoldCollectionTargets();
                this.foresterBlockEntity.addScaffoldPlaced(placeBlockGoal.getPlacePos(), false);
            } else if(BlockDataUtils.hasTags(level, placeBlockGoal.getPlacePos(), BlockTags.FLOWERS)){
               breakBlockNow(villager, placeBlockGoal.getPlacePos()).breakable(BlockTags.FLOWERS).start();
               return;
            }
            clearGoal();
            return;
        }

        if(!this.foresterBlockEntity.hasPendingLogs()) {
            this.foresterBlockEntity.setPhase(Phase.SCAFFOLD_DESTROY);
            return;
        }

        if (this.nextBlockTarget == null) {
            System.out.println("Placement 2");
            selectNextTarget();
            return;
        }
        if( blockInTouchRange(villager, this.nextBlockTarget) || (this.foresterBlockEntity.hasScaffoldsPlaced() &&
                BlockGeometry3DUtils.withinDistance(this.nextBlockTarget,
                        this.foresterBlockEntity.getLastScaffoldPlaced().getKey(), 2))) {
            this.foresterBlockEntity.setPhase(Phase.CHOP);
            clearGoal();
            System.out.println("Placement 3");
            return;
        }

      if(!InventoryUtils.hasAny(villager, ItemTags.DIRT)) {
            this.foresterBlockEntity.setPhase(Phase.SCAFFOLD_GAIN);
            return;
        }

        if( this.foresterBlockEntity.hasScaffoldsPlaced() && !BlockGeometry3DUtils.withinDistance(villager.blockPosition(),
                this.foresterBlockEntity.getLastScaffoldPlaced().getKey().above(), 2)) {
            moveToNow(villager,  this.foresterBlockEntity.getLastScaffoldPlaced().getKey().above()).withinDistance(0).start();
            System.out.println("Placement 4: " +  this.foresterBlockEntity.getLastScaffoldPlaced().getKey().above());
            return;
        }

        BlockPos leavePos = hasLeavesAround(level);
        if (leavePos != null) {
            breakBlockNow(villager, leavePos)
                    .breakableBarrier(BlockTags.LEAVES, BlockTags.LOGS).breakable(BlockTags.LEAVES, BlockTags.LOGS)
                    .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES).inTouchRange()
                            .cheapBreak())
                    .start();
            System.out.println("Leave detected");
            return;
        }

        Map.Entry<BlockPos, Boolean> nextScaffold = nextScaffoldPosition(villager, level);
        if (nextScaffold == null) {
            System.out.println("Placement 5");
            return;
        }

        BlockPos scaffoldPos = nextScaffold.getKey();
        boolean isSolid = nextScaffold.getValue();

        if(scaffoldPos == null) {
            System.out.println("Placement 7");
            return;
        }

        System.out.println("Placement 9: " + scaffoldPos);
        System.out.println("Placeent 9 range: " + !blockInTouchRange(villager, scaffoldPos));
        System.out.println("Placeent 9 not empty: " + this.foresterBlockEntity.hasScaffoldsPlaced());


        if(!isSolid) {
            placeBlockNow(villager, scaffoldPos, Blocks.DIRT).movement(movement ->
                    movement.cheapBreak().inTouchRange().breakable(BlockTags.LEAVES)).start();
            System.out.println("Placement 10");
        } else {
            if(!this.foresterBlockEntity.hasScaffoldsPlaced()) this.foresterBlockEntity.clearScaffoldCollectionTargets();
            this.foresterBlockEntity.addScaffoldPlaced(scaffoldPos, true);
        }
    }

    public void tickScaffoldDestruction(ServerLevel level, Villager villager) {
        if(!this.foresterBlockEntity.hasScaffoldsPlaced()) {
            System.out.println("Destruction 0");
            List<ItemEntity> dirtItemsAround = BlockDataUtils.getItemsAround(level,
                    villager.getBoundingBox(), 5, Items.DIRT);
            if( ! dirtItemsAround.isEmpty()) {
                System.out.println("dirt detected");
                ItemEntity itemTarget = dirtItemsAround.getFirst();
                BlockPos movementTarget = BlockDataUtils.getFirstAirBlock(level, itemTarget.blockPosition());
                moveToNow(villager, movementTarget).withinDistance(0).start();
                return;
            }
            this.foresterBlockEntity.setPhase(Phase.SCAFFOLD_RETURN);
            clearGoal();
            return;
        }
        int count = this.foresterBlockEntity.countScaffoldPlaced();
        Map.Entry<BlockPos, Boolean> target = this.foresterBlockEntity.getLastScaffoldPlaced();
        BlockPos targetPos = target.getKey();
        BlockState state = level.getBlockState(targetPos);
        boolean placedByVillager = ! target.getValue();
        if(! placedByVillager || state.isAir()) {
            System.out.println("Destruction 1");
            this.foresterBlockEntity.removeScaffoldPlaced(targetPos);
            return;
        }

        if(hasGoal() && getGoal() instanceof BreakBlockGoal breakBlockGoal) {
            BlockPos breakPos = breakBlockGoal.getBreakPos();
            if(!breakBlockGoal.hasFailed()) {
                this.foresterBlockEntity.removeScaffoldPlaced(breakPos);
                if(breakPos.equals(targetPos)) {
                    clearGoal();
                    return;
                }
            } else {
                if(count > 2) {
                    moveToNow(villager,  targetPos)
                            .withinDistance(2).closeAsPossible().cheapBreak()
                            .breakable(BlockTags.LEAVES).start();
                    return;
                }
                this.foresterBlockEntity.removeScaffoldPlaced(breakPos);
                clearGoal();
            }
        } else {
            if(!level.getBlockState(targetPos).is(BlockTags.DIRT) &&
                    !level.getBlockState(targetPos).is(Blocks.GRASS_BLOCK)) {
                this.foresterBlockEntity.removeScaffoldPlaced(targetPos);
                return;
            }

        }
        clearGoal();
        breakBlockNow(villager, targetPos).breakableBarrier(BlockTags.LEAVES)
                .breakable(BlockTags.DIRT).start();
    }



    public void tickScaffoldReturn(ServerLevel level, Villager villager) {
        if(!this.foresterBlockEntity.hasScaffoldsBroken())   return;

        BlockPos targetPos = this.foresterBlockEntity.getLastScaffoldBroken();
        this.foresterBlockEntity.removeScaffoldBroken(targetPos);
        placeBlockNow(villager, targetPos, Blocks.DIRT).movement(moveToGoal -> moveToGoal
                .inTouchRange().breakable(BlockTags.LEAVES)).start();
    }

    // ── Hilfsmethoden (statisch) ──────────────────────────────────────────────

    private BlockPos hasLeavesAround(ServerLevel level) {
        if (!this.foresterBlockEntity.hasScaffoldsPlaced()) return null;
        int index = 0;
        while (index < this.foresterBlockEntity.countScaffoldPlaced()) {
            Map.Entry<BlockPos, Boolean> placement = this.foresterBlockEntity.getScaffoldPlaced(index);
            BlockPos blockPos = placement.getKey();
            if(BlockDataUtils.hasTags(level, blockPos.above(), BlockTags.LEAVES)) {
                return blockPos.above();
            }
            if(BlockDataUtils.hasTags(level, blockPos.above().above(), BlockTags.LEAVES)) {
                return blockPos.above().above();
            }
            if(BlockDataUtils.hasTags(level, blockPos.above().above().above(), BlockTags.LEAVES)) {
                return blockPos.above().above().above();
            }
            if (index == this.foresterBlockEntity.countScaffoldPlaced() - 1) {
                if(BlockDataUtils.hasTags(level, blockPos.east(), BlockTags.LEAVES)) {
                    return blockPos.east();
                }
                if(BlockDataUtils.hasTags(level, blockPos.north(), BlockTags.LEAVES)) {
                    return blockPos.north();
                }
                if(BlockDataUtils.hasTags(level, blockPos.south(), BlockTags.LEAVES)) {
                    return blockPos.south();
                }
                if(BlockDataUtils.hasTags(level, blockPos.west(), BlockTags.LEAVES)) {
                    return blockPos.west();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().east(), BlockTags.LEAVES)) {
                    return blockPos.above().east();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().north(), BlockTags.LEAVES)) {
                    return blockPos.above().north();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().south(), BlockTags.LEAVES)) {
                    return blockPos.above().south();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().west(), BlockTags.LEAVES)) {
                    return blockPos.above().west();
                }
                if(BlockDataUtils.hasTags(level, blockPos.west(), BlockTags.LEAVES)) {
                    return blockPos.west();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().east(), BlockTags.LEAVES)) {
                    return blockPos.above().above().east();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().north(), BlockTags.LEAVES)) {
                    return blockPos.above().above().north();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().south(), BlockTags.LEAVES)) {
                    return blockPos.above().above().south();
                }
                if(BlockDataUtils.hasTags(level, blockPos.above().west(), BlockTags.LEAVES)) {
                    return blockPos.above().above().west();
                }
            }
            index++;
        }
        return null;
    }

    private BlockPos findBaseLog(Villager villager, ServerLevel level, ForesterBlockEntity be) {
        BlockPos villagerPosition = villager.blockPosition();
        int eyeY = (int) villager.getEyePosition().y;

        for (int radius = 0; radius <= 10; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (radius == 0 || Math.abs(x) == radius || Math.abs(z) == radius) {
                        for (int yOffset = -2; yOffset <= 4; yOffset++) {
                            BlockPos position = new BlockPos(villagerPosition.getX() + x,
                                    eyeY + yOffset, villagerPosition.getZ() + z);
                            if (level.getBlockState(position).is(BlockTags.LOGS)
                                    && Math.sqrt(be.getBlockPos().distSqr(position)) <= 9) {

                                boolean validNeighbor = Stream.of(position.north(),
                                        position.east(), position.south(), position.west(), position.above(),
                                                position.below(), position.north().below(), position.east().below(),
                                                position.south().below(), position.west().below(),
                                                position.north().above(), position.east().above(),
                                                position.south().above(), position.west().above())
                                        .allMatch(neighborPosition -> {
                                            BlockState neighborState = level.getBlockState(neighborPosition);
                                            return neighborState.isAir() || neighborState.is(BlockTags.LEAVES)
                                                    || neighborState.is(BlockTags.DIRT) ||
                                                    neighborState.is(BlockTags.FLOWERS) ||
                                                    neighborState.is(BlockTags.TALL_FLOWERS) ||
                                                    neighborState.is(BlockTags.SAPLINGS) ||
                                                    neighborState.is(BlockTags.LOGS) ||
                                                    neighborState.is(Blocks.GRASS_BLOCK);
                                        });
                                if ( validNeighbor) return position;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private TreeBlocks scanTree(ServerLevel level, BlockPos originBlockPos) {
        ArrayList<BlockPos> logs = new ArrayList<>();

        Set<BlockPos> logVisited = new HashSet<>();
        Queue<BlockPos> logQueue = new ArrayDeque<>();

        logVisited.add(originBlockPos);
        logQueue.add(originBlockPos);

        while (!logQueue.isEmpty()) {
            BlockPos nextPos = logQueue.poll();
            logs.add(nextPos);

            for (int y = nextPos.getY() - 1; y <= nextPos.getY() + 1; y++)
                for (int x = nextPos.getX() - 1; x <= nextPos.getX() + 1; x++)
                    for (int z = nextPos.getZ() - 1; z <= nextPos.getZ() + 1; z++) {
                        BlockPos neighborPos = new BlockPos(x, y, z);
                        if (!logVisited.contains(neighborPos)
                                && !neighborPos.equals(nextPos)
                                && level.getBlockState(neighborPos).is(BlockTags.LOGS)
                                && originBlockPos.closerThan(neighborPos, 50)) {
                            logVisited.add(neighborPos);
                            logQueue.add(neighborPos);
                        }
                    }
        }
        return new TreeBlocks(logs);
    }


    private Map.Entry<BlockPos, Boolean> nextScaffoldPosition(Villager villager, ServerLevel level) {
        if(!this.foresterBlockEntity.hasScaffoldsPlaced()) {
           BlockPos firstPos = BlockDataUtils.getReachableBlocksOnRadius(level, villager.blockPosition(), nextBlockTarget, 7);
            return firstPos == null ? null : Map.entry(firstPos, BlockDataUtils.isSolid(level, firstPos));
        }

        BlockPos lastPos = this.foresterBlockEntity.getLastScaffoldPlaced().getKey();
        boolean stairs = lastPos.getY()+1 <this.nextBlockTarget.getY();

        if(stairs) {
            int scaffoldPlacedCount = this.foresterBlockEntity.countScaffoldPlaced();
            if(scaffoldPlacedCount == 1) {
                List<BlockPos> blockPositions = BlockGeometry3DUtils.getNeighborByTargetDistance
                        (lastPos, this.nextBlockTarget);
                Optional<BlockPos> blockPosResult = blockPositions.stream()
                        .filter(neighbor -> BlockDataUtils.hasTagsVertical(level, neighbor.above(),
                                2, true, true, BlockTags.AIR))
                        .findFirst();
                return blockPosResult.map(blockPos -> Map.entry(blockPos,
                        BlockDataUtils.isSolid(level, blockPos))).orElse(null);
            } else {
                BlockPos lastBeforePos = this.foresterBlockEntity.getScaffoldPlaced(scaffoldPlacedCount - 2).getKey();
                if(lastBeforePos.getY() == lastPos.getY()) {
                    return Map.entry(lastPos.above(),  BlockDataUtils.isSolid(level, lastPos.above()));
                }
                List<BlockPos> blockPositions = BlockGeometry3DUtils.getNeighborByTargetDistance
                        (lastPos, this.nextBlockTarget);
                Optional<BlockPos> blockPosResult = blockPositions.stream()
                        .filter(neighbor -> BlockDataUtils.hasTagsVertical(level, neighbor.above(),
                                2, true, true, BlockTags.AIR)
                                && ! this.foresterBlockEntity.hasScaffoldPlaced(neighbor.below())
                                && ! this.foresterBlockEntity.hasScaffoldPlaced(neighbor.below().below())
                                && ! this.foresterBlockEntity.hasScaffoldPlaced(neighbor.below().below().below()))
                        .findFirst();
                System.out.println("choose placement 3");
                return blockPosResult.map(blockPos -> Map.entry(blockPos, BlockDataUtils
                        .isSolid(level, blockPos))).orElse(null);
            }
        } else {
            List<BlockPos> blockPositions = BlockGeometry3DUtils.getNeighborByTargetDistance
                    (lastPos, this.nextBlockTarget);
            Optional<BlockPos> blockPosResult = blockPositions.stream()
                    .filter(neighbor -> BlockDataUtils.hasTagsVertical(level, neighbor.above(),
                            2, true, true, BlockTags.AIR)
                            && ! this.foresterBlockEntity.hasScaffoldPlaced(neighbor.below())
                            && ! this.foresterBlockEntity.hasScaffoldPlaced(neighbor.below().below())
                            && ! this.foresterBlockEntity.hasScaffoldPlaced(neighbor.below().below().below()))
                    .findFirst();
            return blockPosResult.map(blockPos -> Map.entry(blockPos, BlockDataUtils
                    .isSolid(level, blockPos))).orElse(null);
        }

    }

    private void selectNextTarget() {
        if (this.foresterBlockEntity.hasPendingLogs()) {
            this.nextBlockTarget = this.foresterBlockEntity.getFirstPendingLog();
        }
    }

    private boolean hasEnoughScaffoldBlocks(Villager villager) {
        int dirtBlocks = 0;
        for(ItemStack itemStack : villager.getInventory().getItems()) {
            if(itemStack.is(ItemTags.DIRT)) {
                dirtBlocks += itemStack.getCount();
            }
        }
        return dirtBlocks >= 6;
    }

}