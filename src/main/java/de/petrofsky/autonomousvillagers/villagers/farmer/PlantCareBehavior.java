package de.petrofsky.autonomousvillagers.villagers.farmer;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static de.petrofsky.autonomousvillagers.villagers.farmer.FarmerUtils.*;

public class PlantCareBehavior extends Behavior<Villager>  {

    private enum ActionType { PICK_UP, HARVEST, PLANT, FERTILIZE }
    private ActionType currentAction = null;
    private BlockPos targetBlock = null;
    private ItemEntity targetItem = null;
    private BlockPos walkTargetPos = null;
    private boolean actionCompleted = false;
    private int workTicks = 0;

    public PlantCareBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 200, 400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager farmer) {
        GlobalPos jobSite = farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (jobSite == null || jobSite.dimension() != level.dimension()) return false;

        BlockPos composterPos = jobSite.pos();
        List<BlockPos> fieldBlocks = getFieldBlocks(composterPos);

        if (tryFindTask(level, farmer, fieldBlocks, 2)) return true;
        if (tryFindTask(level, farmer, fieldBlocks, 4)) return true;
        if (tryFindTask(level, farmer, fieldBlocks, 6)) return true;

        return tryFindTask(level, farmer, fieldBlocks, -1);
    }

    @Override
    protected void stop(ServerLevel level, Villager farmer, long gameTime) {
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.currentAction = null;
        this.targetBlock = null;
        this.targetItem = null;
        this.walkTargetPos = null;
    }

    @Override
    protected void start(ServerLevel level, Villager farmer, long gameTime) {
        this.workTicks = 0;
        this.actionCompleted = false;

        if (currentAction == ActionType.PICK_UP && targetItem != null) {
            this.walkTargetPos = targetItem.blockPosition();
        } else if (targetBlock != null) {
            this.walkTargetPos = getClosestAdjacentBlock(level, targetBlock, farmer.blockPosition());
        }

        if (this.walkTargetPos != null) {
            farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.walkTargetPos, 0.5f, 0));
        } else {
            this.actionCompleted = true;
        }
    }

    private boolean tryFindTask(ServerLevel level, Villager farmer, List<BlockPos> field, int radius) {
        BlockPos vPos = farmer.blockPosition();
        boolean hasSpace = !isInventoryFull(farmer);
        boolean hasSeeds = hasPlantableSeeds(farmer);
        boolean hasBoneMeal = hasItem(farmer, Items.BONE_MEAL);

        List<BlockPos> harvestTargets = new ArrayList<>();
        List<BlockPos> plantTargets = new ArrayList<>();
        List<BlockPos> fertilizeTargets = new ArrayList<>();
        List<ItemEntity> pickupTargets = new ArrayList<>();

        for (BlockPos pos : field) {
            if (radius != -1 && !pos.closerToCenterThan(farmer.position(), radius)) continue;

            BlockState state = level.getBlockState(pos);
            if (hasSpace && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                harvestTargets.add(pos);
            }
            if (hasSeeds && state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
                plantTargets.add(pos);
            }
            if (hasBoneMeal && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                fertilizeTargets.add(pos);
            }
        }

        if (hasSpace) {
            double searchRadius = radius == -1 ? 10.0 : radius;
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, farmer.getBoundingBox().inflate(searchRadius));
            for (ItemEntity item : items) {
                if (field.contains(item.blockPosition()) && isFarmerItem(item.getItem().getItem())) {
                    pickupTargets.add(item);
                }
            }
        }

        List<ActionType> possibleActions = new ArrayList<>();
        if (!harvestTargets.isEmpty()) possibleActions.add(ActionType.HARVEST);
        if (!plantTargets.isEmpty()) possibleActions.add(ActionType.PLANT);
        if (!fertilizeTargets.isEmpty()) possibleActions.add(ActionType.FERTILIZE);
        if (!pickupTargets.isEmpty()) possibleActions.add(ActionType.PICK_UP);

        if (possibleActions.isEmpty()) return false;

        this.currentAction = possibleActions.get(level.random.nextInt(possibleActions.size()));

        if (currentAction == ActionType.PICK_UP) {
            if (radius == -1) {
                this.targetItem = pickupTargets.get(level.random.nextInt(pickupTargets.size()));
            } else {
                this.targetItem = pickupTargets.stream()
                        .min(Comparator.comparingDouble(i -> i.distanceToSqr(farmer)))
                        .orElse(null);
            }
        } else {
            List<BlockPos> targets = switch (currentAction) {
                case HARVEST -> harvestTargets;
                case PLANT -> plantTargets;
                case FERTILIZE -> fertilizeTargets;
                default -> new ArrayList<>();
            };

            if (radius == -1) {
                this.targetBlock = targets.get(level.random.nextInt(targets.size()));
            } else {
                this.targetBlock = targets.stream()
                        .min(Comparator.comparingDouble(p -> p.distSqr(vPos)))
                        .orElse(null);
            }
        }

        return this.targetBlock != null || this.targetItem != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager farmer, long gameTime) {
        if (actionCompleted) return false;

        if (currentAction == ActionType.PICK_UP) {
            return targetItem != null && targetItem.isAlive();
        } else if (targetBlock != null) {
            BlockState state = level.getBlockState(targetBlock);
            return switch (currentAction) {
                case HARVEST -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
                case PLANT -> state.isAir() && level.getBlockState(targetBlock.below()).is(Blocks.FARMLAND);
                case FERTILIZE -> state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
                default -> false;
            };
        }
        return false;
    }

    @Override
    protected void tick(ServerLevel level, Villager farmer, long gameTime) {
        if (walkTargetPos == null) return;

        double distance = farmer.blockPosition().distSqr(walkTargetPos);

        if (distance <= 2.0D) {
            BlockPos lookPos = (currentAction == ActionType.PICK_UP && targetItem != null) ? targetItem.blockPosition() : targetBlock;
            farmer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lookPos));

            workTicks++;

            if (workTicks >= 10) {
                executeAction(level, farmer);
            }
        }
    }

    private boolean isFarmerItem(Item item) {
        return item instanceof ItemNameBlockItem || item == Items.WHEAT || item == Items.BONE_MEAL;
    }

    private boolean isInventoryFull(Villager farmer) {
        SimpleContainer inv = farmer.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || (isFarmerItem(stack.getItem()) && stack.getCount() < stack.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    private void executeAction(ServerLevel level, Villager farmer) {
        switch (currentAction) {
            case PICK_UP -> {

                actionCompleted = true;
            }
            case HARVEST -> {
                level.destroyBlock(targetBlock, true, farmer);


                List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, farmer.getBoundingBox().inflate(2.0));
                if (!drops.isEmpty() && !isInventoryFull(farmer)) {
                    this.currentAction = ActionType.PICK_UP;
                    this.targetItem = drops.get(0);
                    this.targetBlock = null;
                    this.walkTargetPos = this.targetItem.blockPosition();
                    this.workTicks = 0;
                    farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(this.walkTargetPos, 0.5f, 0));
                } else {
                    actionCompleted = true;
                }
            }
            case PLANT -> {
                plantSeed(level, farmer, targetBlock);
                actionCompleted = true;
            }
            case FERTILIZE -> {
                useBoneMeal(level, farmer, targetBlock);
                actionCompleted = true;
            }
        }
    }

    private void plantSeed(ServerLevel level, Villager farmer, BlockPos pos) {
        SimpleContainer inv = farmer.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof ItemNameBlockItem seedItem) {
                level.setBlockAndUpdate(pos, ((CropBlock)seedItem.getBlock()).defaultBlockState());
                level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.shrink(1);
                return;
            }
        }
    }

    private void useBoneMeal(ServerLevel level, Villager farmer, BlockPos pos) {
        SimpleContainer inv = farmer.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.BONE_MEAL)) {
                BoneMealItem.growCrop(stack, level, pos);
                level.levelEvent(2005, pos, 0);
                return;
            }
        }
    }
}
