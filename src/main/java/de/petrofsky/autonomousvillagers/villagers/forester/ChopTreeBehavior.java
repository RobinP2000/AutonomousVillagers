package de.petrofsky.autonomousvillagers.villagers.forester;

import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import de.petrofsky.autonomousvillagers.utils.BlockDataUtils;
import de.petrofsky.autonomousvillagers.utils.BlockGeometry2DUtils;
import de.petrofsky.autonomousvillagers.utils.BlockGeometry3DUtils;
import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import de.petrofsky.autonomousvillagers.villagers.goals.BreakBlockGoal;
import de.petrofsky.autonomousvillagers.villagers.goals.MoveToGoal;
import de.petrofsky.autonomousvillagers.villagers.goals.PlaceBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class ChopTreeBehavior extends AbstractVillagerBehavior {

    record TreeBlocks(ArrayList<BlockPos> logs) {}

    private enum Phase {
        NAVIGATE,
        CHOP,
        SCAFFOLD_BUILD,
        SCAFFOLD_GAIN,
        SCAFFOLD_DESTROY,
        SCAFFOLD_RETURN
    }

    // ── Konstanten ────────────────────────────────────────────────────────────

    private static final int   MAX_DURATION = 6000;
    private static final float WALK_SPEED   = 0.6f;

    // ── Zustandsfelder ────────────────────────────────────────────────────────

    private List<BlockPos> pendingLogs    = new ArrayList<>();
    private List<BlockPos> scaffoldBroken = new ArrayList<>();
    private List<Map.Entry<BlockPos, Boolean>> scaffoldPlaced = new ArrayList<>();
    private List<BlockPos> scaffoldCollectionTargets = new ArrayList<>();
    private List<ItemEntity> itemsAround = new ArrayList<>();
    private BlockPos baseLogPosition;

    private BlockPos nextBlockTarget;
    private Phase phase = Phase.CHOP;

    private int   placeCooldown = 0;

    public ChopTreeBehavior() {
        super(Map.of(
                MemoryModuleType.JOB_SITE,   MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(@NotNull ServerLevel level, @NotNull Villager villager) {
        ForesterBlockEntity be = getBlockEntity(level, villager);
        if (be == null) return false;
        return this.nextBlockTarget != null || findBaseLog(villager, level, be) != null;
    }

    @Override
    protected void start(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        AttributeInstance followRange = villager.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(48.0D);
            villager.getNavigation().setMaxVisitedNodesMultiplier(4.0f);
        }
        if (this.nextBlockTarget != null) return;

        ForesterBlockEntity be = getBlockEntity(level, villager);
        if (be == null) return;
        BlockPos treeBase = findBaseLog(villager, level, be);
        if (treeBase == null) return;

        this.baseLogPosition = treeBase;
        TreeBlocks tree = scanTree(level, treeBase);

        pendingLogs.addAll(tree.logs());
        pendingLogs.sort(Comparator.comparingInt(BlockPos::getY));

        selectNextTarget(level, villager);

        placeCooldown = 0;
    }


    protected void executeBehavior(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        System.out.println("Phase: " + phase);
        switch (phase) {
            case CHOP            -> tickChop(level, villager);
            case SCAFFOLD_GAIN   -> tickScaffoldGain(level, villager);
            case SCAFFOLD_BUILD -> tickScaffoldPlacement(level, villager);
            case SCAFFOLD_DESTROY -> tickScaffoldDestruction(level, villager);
            case SCAFFOLD_RETURN -> tickScaffoldReturn(level, villager);
        }
    }

    @Override
    protected boolean canStillUse(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        return ! this.pendingLogs.isEmpty() || ! this.scaffoldPlaced.isEmpty()
                || ! this.scaffoldBroken.isEmpty() || ! this.itemsAround.isEmpty();
    }

    @Override
    protected void stop(@NotNull ServerLevel level, Villager villager, long gameTime) {
        phase = Phase.CHOP;
        placeCooldown = 0;
        clearGoal();
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }


    // ── Phase: CHOP ───────────────────────────────────────────────────────────

    private void tickChop(ServerLevel level, Villager villager) {
        if(this.pendingLogs.isEmpty()) {
           if(this.itemsAround == null || this.itemsAround.isEmpty()) {
                this.phase = Phase.SCAFFOLD_DESTROY;
            } else if(hasGoal() && getGoal() instanceof MoveToGoal moveToGoal && moveToGoal.isStopped()
                        && moveToGoal.getTarget().equals(itemsAround.getFirst().blockPosition())) {
                itemsAround.removeFirst();
            } else {
                ItemEntity itemTarget = itemsAround.getFirst();
                moveToNow(villager, itemTarget.blockPosition()).start();
            }
            return;
        }

        BlockPos targetPosition = this.nextBlockTarget;
        if (targetPosition == null) {
            selectNextTarget(level, villager);
            return;
        }

        if (! level.getBlockState(targetPosition).is(BlockTags.LOGS) && ! hasGoal()) {
            System.out.println("Chop2");
            this.pendingLogs.remove(targetPosition);
            this.nextBlockTarget = null;
            selectNextTarget(level, villager);
            return;
        }

        if(!hasGoal()) {
            System.out.println("Chop3");
            breakBlockNow(villager, targetPosition)
                    .breakableBarrier(BlockTags.LOGS, BlockTags.LEAVES, BlockTags.FLOWERS, BlockTags.TALL_FLOWERS)
                    .breakable(BlockTags.LOGS)
                    .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES)).start();
        } else if(getGoal() instanceof BreakBlockGoal breakBlockGoal) {
            System.out.println("Chop4");
            if(!breakBlockGoal.hasFailed() && breakBlockGoal.isStopped()) {
                System.out.println("Chop4.2");
                if(pendingLogs.contains(breakBlockGoal.getBreakPos())) {
                    if(breakBlockGoal.getBreakPos().equals(this.nextBlockTarget)) {
                        this.nextBlockTarget = null;
                        System.out.println("Chop4.3");
                    }
                    pendingLogs.remove(breakBlockGoal.getBreakPos());

                    if(this.pendingLogs.isEmpty()) {
                        this.itemsAround = BlockDataUtils.getItemsAround(level,
                                villager.getBoundingBox(), 8, ItemTags.LOGS);
                    } else {
                        selectNextTarget(level, villager);
                    }
                    clearGoal();
                } else {
                    breakBlockNow(villager, targetPosition)
                            .breakableBarrier(BlockTags.LOGS, BlockTags.LEAVES, BlockTags.FLOWERS, BlockTags.TALL_FLOWERS)
                            .breakable(BlockTags.LOGS)
                            .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES)).start();
                }
            } else {
                System.out.println("Chop4.5");
            }
        } else if(getGoal() instanceof MoveToGoal moveToGoal && moveToGoal.hasFailed()) {
            clearGoal();
            phase = Phase.SCAFFOLD_BUILD;
        } else if(getGoal() instanceof MoveToGoal moveToGoal && !moveToGoal.hasFailed()) {
            clearGoal();
        }
    }

    public void tickScaffoldGain(ServerLevel level, Villager villager) {
        this.itemsAround = BlockDataUtils.getItemsAround(level,
                villager.getBoundingBox(), 8, ItemTags.DIRT);
        if(!this.itemsAround.isEmpty()) {
            ItemEntity itemTarget = this.itemsAround.getFirst();
            moveToNow(villager, itemTarget.blockPosition()).withinDistance(0).start();
            this.itemsAround.remove(itemTarget);
            return;
        }

        if (hasEnoughScaffoldBlocks(villager)) {
          this.phase = Phase.SCAFFOLD_BUILD;
            return;
        }

        BlockPos targetDirt = findNearestBreakableDirt(villager, level);
        if (targetDirt == null) {
            villager.getNavigation().stop();
            return;
        }

        if(hasGoal()) {
            if(getGoal() instanceof MoveToGoal moveToGoal && moveToGoal.isStopped() && moveToGoal.hasFailed()) {
                this.scaffoldCollectionTargets.remove(moveToGoal.getTarget());
            } else if(getGoal() instanceof BreakBlockGoal breakBlockGoal) {
                if(! breakBlockGoal.hasFailed() ) {
                    this.scaffoldBroken.add(breakBlockGoal.getBreakPos());
                }
                System.out.println("broken size: " + this.scaffoldBroken.size());
                this.scaffoldCollectionTargets.remove(breakBlockGoal.getBreakPos());

                if(breakBlockGoal.getBreakPos().equals(targetDirt)) {
                    clearGoal();
                    return;
                }
            }
            clearGoal();
        }
        breakBlockNow(villager, targetDirt)
                .breakableBarrier(BlockTags.LEAVES, BlockTags.FLOWERS, BlockTags.TALL_FLOWERS)
                .breakable(BlockTags.DIRT)
                .movement(moveToGoal -> moveToGoal.breakable(BlockTags.LEAVES).withinDistance(0))
                .start();
    }


    private BlockPos findNearestBreakableDirt(Villager villager, ServerLevel level) {
        if(!scaffoldCollectionTargets.isEmpty()) {
            System.out.println("Dirt 1");
            return scaffoldCollectionTargets.getFirst();
        }

        BlockPos villagerPos = villager.blockPosition();

        final BlockPos distanceTo;
        if(!scaffoldPlaced.isEmpty()) {
            distanceTo = scaffoldPlaced.getFirst().getKey();
        } else {
            distanceTo = null;
        }

        List<BlockPos> centerSelections = BlockGeometry2DUtils.getCircleBetweenXZ(villagerPos, 5, 9,
                villagerPos.getY(), false, false, true);
        if(distanceTo != null) {
            centerSelections = centerSelections.stream().filter(blockPos ->
                    !BlockGeometry3DUtils.withinDistance(blockPos, distanceTo, 6)).toList();
        }
        if(centerSelections.isEmpty()) {
            System.out.println("Dirt 2");
            return null;
        }
        BlockPos blockPos = centerSelections.getFirst();
        List<BlockPos> field = BlockGeometry2DUtils.getCircleXZ(blockPos, 4,
                blockPos.getY());
        List<BlockPos> targets = BlockDataUtils.getHighestPos(level, field, villagerPos, 1);
        System.out.println("Dirt 2.5: " + targets.size());

        targets = BlockDataUtils.getWithAnyTags(level, targets, BlockTags.DIRT);

        if(targets.isEmpty()) {
            System.out.println("Dirt 3");
            return null;
        } else {
            System.out.println("Dirt 4");
            if(distanceTo != null) {
                scaffoldCollectionTargets.addAll(targets);
            } else {
                scaffoldCollectionTargets.addAll(targets.subList(0, Math.min(4, targets.size() - 1)));
            }
            return !scaffoldCollectionTargets.isEmpty() ? scaffoldCollectionTargets.getFirst() : null;
        }
    }

    public void tickScaffoldPlacement(ServerLevel level, Villager villager) {
        boolean goalLeft = hasGoal() && getGoal() instanceof PlaceBlockGoal placeBlockGoal
                && ! placeBlockGoal.hasFailed();
        if(this.pendingLogs.isEmpty() && !goalLeft) {
            System.out.println("Placement 1");
            this.phase = Phase.SCAFFOLD_DESTROY;
            return;
        }
        if(goalLeft) {
            PlaceBlockGoal placeBlockGoal = (PlaceBlockGoal) getGoal();
            this.scaffoldPlaced.add(Map.entry(placeBlockGoal.getPlacePos(), false));
            clearGoal();
            return;
        }
        if (this.nextBlockTarget == null) {
            System.out.println("Placement 2");
            selectNextTarget(level, villager);

            return;
        }
        if( blockInTouchRange(villager, this.nextBlockTarget) || (!this.scaffoldPlaced.isEmpty() &&
                BlockGeometry3DUtils.withinDistance(this.nextBlockTarget,
                        this.scaffoldPlaced.getLast().getKey(), 2))) {
            this.phase = Phase.CHOP;
            clearGoal();
            System.out.println("Placement 3");
            return;
        }

      if(!InventoryUtils.hasAny(villager, ItemTags.DIRT)) {
            this.phase = Phase.SCAFFOLD_GAIN;
            return;
        }

        if( ! scaffoldPlaced.isEmpty() && !BlockGeometry3DUtils.withinDistance(villager.blockPosition(),
                scaffoldPlaced.getLast().getKey().above(), 2)) {
            moveToNow(villager, scaffoldPlaced.getLast().getKey().above()).start();
            return;
        }

        BlockPos leavePos = hasLeavesAround(level);
        if (leavePos != null) {
            breakBlock(villager, leavePos, List.of(BlockTags.LEAVES, BlockTags.LOGS));
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

        // Cooldown für das Platzieren abwarten (analog zu breakCooldown)
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        if(scaffoldPos == null) {
            System.out.println("Placement 7");
            return;
        }

        /*

        if(scaffoldPlaced.isEmpty() && !blockInTouchRange(villager, scaffoldPos)) {
            System.out.println("Placement 7.1");
            BlockPos blockPos = BlockDataUtils.getWalkableNeighbor(level, scaffoldPos.above(), this.nextBlockTarget);
            moveTo(villager, blockPos);
            return;
        } else if (!scaffoldPlaced.isEmpty() && ! BlockGeometry3DUtils.withinDistance(villager.getOnPos(),
                scaffoldPlaced.getLast().getKey(), 4)) {
            System.out.println("Placement 8");
            BlockPos lastPlaced = scaffoldPlaced.getLast().getKey();
            moveTo(villager, lastPlaced);

            return;
        }*/

        System.out.println("Placement 9: " + scaffoldPos);
        System.out.println("Placeent 9 range: " + !blockInTouchRange(villager, scaffoldPos));
        System.out.println("Placeent 9 not empty: " + ! scaffoldPlaced.isEmpty());


        if(!isSolid) {
            placeBlock(villager, scaffoldPos, Blocks.DIRT);
        } else {
            this.scaffoldPlaced.add(Map.entry(scaffoldPos, true));
        }
    }

    public void tickScaffoldDestruction(ServerLevel level, Villager villager) {
        System.out.println("left: " + this.scaffoldPlaced.size());
        if(this.scaffoldPlaced.isEmpty()) {
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
            this.phase = Phase.SCAFFOLD_RETURN;
            clearGoal();
            return;
        }

        Map.Entry<BlockPos, Boolean> target = this.scaffoldPlaced.getLast();
        BlockPos targetPos = target.getKey();
        boolean placedByVillager = ! target.getValue();
        if(! placedByVillager) {
            System.out.println("Destruction 1");
            this.scaffoldPlaced.removeLast();
            return;
        }

        if(hasGoal() && getGoal() instanceof BreakBlockGoal breakBlockGoal) {
            BlockPos breakPos = breakBlockGoal.getBreakPos();
            if(!breakBlockGoal.hasFailed()) {
                this.scaffoldPlaced.removeIf(entry ->
                        entry.getKey().equals(breakPos));
                if(breakPos.equals(targetPos)) {
                    clearGoal();
                    return;
                }
            } else if(!level.getBlockState(breakPos).is(BlockTags.DIRT) &&
                    !level.getBlockState(breakPos).is(Blocks.GRASS_BLOCK)) {
                // Block ist gar nicht mehr da (z.B. anderweitig entfernt) -> Eintrag verwerfen.
                this.scaffoldPlaced.removeIf(entry ->
                        entry.getKey().equals(breakPos));
                clearGoal();
            } else {
                // Vorher wurde hier NICHTS gemacht (die Bedingung "! hasGoal()" konnte innerhalb
                // von "if(hasGoal() && ...)" nie wahr werden), das gescheiterte Goal blieb also
                // fuer immer als currentGoal stehen und tickScaffoldDestruction() haengte sich
                // an genau diesem Eintrag auf - alle darunterliegenden Gerüstbloecke wurden nie
                // mehr angefasst. Jetzt: Goal loeschen, damit unten ein neuer Versuch gestartet wird.
                clearGoal();
            }
        } else {
            if(!level.getBlockState(targetPos).is(BlockTags.DIRT) &&
                    !level.getBlockState(targetPos).is(Blocks.GRASS_BLOCK) && ! hasGoal()) {
                this.scaffoldPlaced.removeIf(entry ->
                        entry.getKey().equals(targetPos));
                return;
            }

        }
        clearGoal();
        breakBlock(villager, targetPos, List.of(BlockTags.DIRT));
    }



    public void tickScaffoldReturn(ServerLevel level, Villager villager) {
        if(scaffoldBroken.isEmpty()) {
            System.out.println("return 1: All blocks broken");
            return;
        }

        BlockPos targetPos = scaffoldBroken.getLast();
        if (BlockDataUtils.isSolid(level, targetPos)) {
            System.out.println("return 3");
            scaffoldBroken.remove(targetPos);
            return;
        }

        if(!InventoryUtils.hasAny(villager, ItemTags.DIRT)) {
            System.out.println("return 2");
            return;
        }

        if (! blockInTouchRange(villager, targetPos)) {
            System.out.println("return 4");
            Path scaffoldPath = getPath(villager, targetPos);
            if ( scaffoldPath != null && scaffoldPath.canReach()) {
                villager.getNavigation().moveTo(scaffoldPath, WALK_SPEED);
            }
            return;
        }

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        villager.getNavigation().stop();
        lookAt(villager, targetPos);

        level.setBlock(targetPos, Blocks.DIRT.defaultBlockState(), 3);
        InventoryUtils.decrease(villager, ItemTags.DIRT);
    }

    // ── Hilfsmethoden (statisch) ──────────────────────────────────────────────

    private BlockPos hasLeavesAround(ServerLevel level) {
        if (this.scaffoldPlaced.isEmpty()) return null;
        int index = 0;
        while (index < this.scaffoldPlaced.size()) {
            Map.Entry<BlockPos, Boolean> placement = this.scaffoldPlaced.get(index);
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
            if (index == this.scaffoldPlaced.size() - 1) {
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

    private static ForesterBlockEntity getBlockEntity(ServerLevel level, Villager villager) {
        Optional<GlobalPos> jobOpt = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobOpt.isEmpty()) return null;
        GlobalPos jobSite = jobOpt.get();
        if (!jobSite.dimension().equals(level.dimension())) return null;
        if (level.getBlockEntity(jobSite.pos()) instanceof ForesterBlockEntity be) return be;
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
        if(this.scaffoldPlaced.isEmpty()) {
           BlockPos firstPos = BlockDataUtils.getReachableBlocksOnRadius(level, villager.blockPosition(), nextBlockTarget, 7);
            return firstPos == null ? null : Map.entry(firstPos, BlockDataUtils.isSolid(level, firstPos));
        }

        BlockPos lastPos = this.scaffoldPlaced.getLast().getKey();
        boolean stairs = lastPos.getY()+1 <this.nextBlockTarget.getY();

        if(stairs) {
            if(this.scaffoldPlaced.size() == 1) {
                List<BlockPos> blockPositions = BlockGeometry3DUtils.getNeighborByTargetDistance
                        (lastPos, this.nextBlockTarget);
                Optional<BlockPos> blockPosResult = blockPositions.stream()
                        .filter(neighbor -> BlockDataUtils.hasTagsVertical(level, neighbor.above(),
                                2, true, true, BlockTags.AIR))
                        .findFirst();
                return blockPosResult.map(blockPos -> Map.entry(blockPos,
                        BlockDataUtils.isSolid(level, blockPos))).orElse(null);
            } else {
                BlockPos lastBeforePos = this.scaffoldPlaced.get(this.scaffoldPlaced.size() - 2).getKey();
                if(lastBeforePos.getY() == lastPos.getY()) {
                    return Map.entry(lastPos.above(),  BlockDataUtils.isSolid(level, lastPos.above()));
                }
                List<BlockPos> blockPositions = BlockGeometry3DUtils.getNeighborByTargetDistance
                        (lastPos, this.nextBlockTarget);
                Optional<BlockPos> blockPosResult = blockPositions.stream()
                        .filter(neighbor -> BlockDataUtils.hasTagsVertical(level, neighbor.above(),
                                2, true, true, BlockTags.AIR) && ! placedPosition(neighbor.below())
                                && ! placedPosition(neighbor.below().below())
                                && ! placedPosition(neighbor.below().below().below()))
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
                            2, true, true, BlockTags.AIR) && ! placedPosition(neighbor.below())
                            && ! placedPosition(neighbor.below().below())
                            && ! placedPosition(neighbor.below().below().below()))
                    .findFirst();
            return blockPosResult.map(blockPos -> Map.entry(blockPos, BlockDataUtils
                    .isSolid(level, blockPos))).orElse(null);
        }

    }

    private boolean placedPosition(BlockPos blockPos) {
        for(Map.Entry<BlockPos, Boolean> entry : scaffoldPlaced) {
            if (blockPos.equals(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private Path getPath(Villager villager, BlockPos blockPos) {
        return getPath(villager, blockPos, 2);
    }

    private Path getPath(Villager villager, BlockPos blockPos, int distance) {
        return villager.getNavigation().createPath(blockPos, distance);
    }

    private void selectNextTarget(ServerLevel level, Villager villager) {
        selectNextTarget(level,villager, this.nextBlockTarget);
    }

    private void selectNextTarget(ServerLevel level, Villager villager, BlockPos blockPos) {
        if (!this.pendingLogs.isEmpty()) {
            this.nextBlockTarget = blockPos != null ? blockPos : this.pendingLogs.getFirst();
            System.out.println("next: " + this.nextBlockTarget);
            BlockPos target = BlockDataUtils.getWalkableNeighbor(level, this.nextBlockTarget, villager.getOnPos().above());
            System.out.println("pos on: " + villager.getOnPos());
            System.out.println("target: " + target);
            if(target != null) {
               // moveTo(villager, target);
/*
                villager.getNavigation().moveTo(this.navigationPath, WALK_SPEED);*/
            }
        }
    }



    private void lookAt(Villager villager, BlockPos pos) {
        villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
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