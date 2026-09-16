package de.petrofsky.autonomousvillagers.villagers.farmer;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Collectors;

public class AccessChestBehavior extends Behavior<Villager> {

    private enum ActionType { STORE, RETRIEVE }

    private ActionType currentAction = null;
    private BlockPos targetChestPos = null;
    private int taskStep = 0;
    private int waitTicks = 0;
    private long chestCooldownTime = 0;

    private final Map<Item, Integer> fieldCropCounts = new HashMap<>();

    public AccessChestBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT
        ), 200, 400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager farmer) {
        if (level.getGameTime() < chestCooldownTime) return false;

        GlobalPos jobSitePos = farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (jobSitePos == null || jobSitePos.dimension() != level.dimension()) return false;

        List<BlockPos> fieldBlocks = getFieldBlocks(jobSitePos.pos());

        BlockPos chestPos = findChest(level, fieldBlocks);
        if (chestPos == null) return false;

        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof Container chest)) return false;

        updateFieldCropCounts(level, fieldBlocks);

        if (needsToStoreItems(farmer, chest)) {
            this.targetChestPos = chestPos;
            this.currentAction = ActionType.STORE;
            return true;
        }

        if (needsToRetrieveItems(farmer, chest)) {
            this.targetChestPos = chestPos;
            this.currentAction = ActionType.RETRIEVE;
            return true;
        }

        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager farmer, long gameTime) {
        this.taskStep = 0;
        this.waitTicks = 0;
        farmer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(targetChestPos, 0.5f, 1));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager farmer, long gameTime) {
        return targetChestPos != null && taskStep < 2;
    }

    @Override
    protected void tick(ServerLevel level, Villager farmer, long gameTime) {
        if (targetChestPos == null) return;

        if (farmer.blockPosition().closerThan(targetChestPos, 2.5D)) {

            if (taskStep == 0) {
                farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                farmer.getNavigation().stop();
            }

            farmer.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetChestPos));

            if (taskStep == 0) {
                setContainerOpenState(level, targetChestPos, true);

                if (currentAction == ActionType.STORE) {
                    storeItemsLogic(level, farmer, targetChestPos);
                } else if (currentAction == ActionType.RETRIEVE) {
                    retrieveItemsLogic(level, farmer, targetChestPos);
                }

                taskStep = 1;
                waitTicks = 0;

            } else if (taskStep == 1) {
                waitTicks++;
                if (waitTicks >= 15) {
                    setContainerOpenState(level, targetChestPos, false);
                    taskStep = 2;
                }
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager farmer, long gameTime) {
        if (taskStep == 1 && targetChestPos != null) {
            setContainerOpenState(level, targetChestPos, false);
        }
        farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        farmer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.currentAction = null;
        this.targetChestPos = null;
        this.taskStep = 0;
    }


    private boolean needsToStoreItems(Villager farmer, Container chest) {
        SimpleContainer inv = farmer.getInventory();
        List<Item> top4Seeds = getTop4RarestSeeds();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            boolean wantsToStore = false;

            if (stack.is(Items.BONE_MEAL) && countItems(farmer, Items.BONE_MEAL) > 64) {
                wantsToStore = true;
            } else if (isSeed(stack.getItem())) {
                if (!top4Seeds.contains(stack.getItem())) wantsToStore = true;
                else if (countItems(farmer, stack.getItem()) > 32) wantsToStore = true;
            } else if (!stack.is(Items.BONE_MEAL)) {
                wantsToStore = true;
            }

            if (wantsToStore && chestHasSpaceFor(chest, stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean needsToRetrieveItems(Villager farmer, Container chest) {
        if (countItems(farmer, Items.BONE_MEAL) < 64 && chestContains(chest, Items.BONE_MEAL)) return true;

        List<Item> top4Seeds = getTop4RarestSeeds();
        for (Item seed : top4Seeds) {
            if (countItems(farmer, seed) < 32 && chestContains(chest, seed)) return true;
        }
        return false;
    }

    private boolean chestHasSpaceFor(Container chest, ItemStack stackToStore) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(slot, stackToStore) && slot.getCount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean chestContains(Container chest, Item item) {
        for(int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) return true;
        }
        return false;
    }


    private void setContainerOpenState(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BARREL)) {
            level.setBlock(pos, state.setValue(net.minecraft.world.level.block.BarrelBlock.OPEN, open), 3);
            SoundEvent sound = open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        } else {
            level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
            SoundEvent sound = open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        }
    }



    private void storeItemsLogic(ServerLevel level, Villager farmer, BlockPos chestPos) {
        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof Container chest)) return;

        SimpleContainer inv = farmer.getInventory();
        List<Item> top4Seeds = getTop4RarestSeeds();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            int amountToMove = 0;
            if (!isSeed(stack.getItem()) && !stack.is(Items.BONE_MEAL)) {
                amountToMove = stack.getCount();
            } else if (stack.is(Items.BONE_MEAL)) {
                amountToMove = countItems(farmer, Items.BONE_MEAL) - 64;
            } else if (isSeed(stack.getItem())) {
                if (!top4Seeds.contains(stack.getItem())) amountToMove = stack.getCount();
                else amountToMove = countItems(farmer, stack.getItem()) - 32;
            }

            if (amountToMove > 0) {
                transferToChest(chest, stack, Math.min(amountToMove, stack.getCount()));
            }
        }
    }

    private void retrieveItemsLogic(ServerLevel level, Villager farmer, BlockPos chestPos) {
        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof Container chest)) return;

        List<Item> top4Seeds = getTop4RarestSeeds();

        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack chestStack = chest.getItem(i);
            if (chestStack.isEmpty()) continue;

            int needed = 0;
            if (chestStack.is(Items.BONE_MEAL)) needed = 64 - countItems(farmer, Items.BONE_MEAL);
            else if (top4Seeds.contains(chestStack.getItem())) needed = 32 - countItems(farmer, chestStack.getItem());

            if (needed > 0) {
                transferToVillager(farmer, chestStack, Math.min(needed, chestStack.getCount()));
            }
        }
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

    private BlockPos findChest(ServerLevel level, List<BlockPos> field) {
        return field.stream().filter(p -> level.getBlockEntity(p) instanceof Container).findFirst().orElse(null);
    }

    private void updateFieldCropCounts(ServerLevel level, List<BlockPos> fieldBlocks) {
        fieldCropCounts.clear();
        fieldCropCounts.put(Items.WHEAT_SEEDS, 0);
        fieldCropCounts.put(Items.POTATO, 0);
        fieldCropCounts.put(Items.CARROT, 0);
        fieldCropCounts.put(Items.BEETROOT_SEEDS, 0);

        for (BlockPos pos : fieldBlocks) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop) {
                Item seedItem = crop.getCloneItemStack(level, pos, state).getItem();
                fieldCropCounts.put(seedItem, fieldCropCounts.getOrDefault(seedItem, 0) + 1);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container chest) {
                for(int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack stack = chest.getItem(i);
                    if (isSeed(stack.getItem())) {
                        fieldCropCounts.put(stack.getItem(), fieldCropCounts.getOrDefault(stack.getItem(), 0) + stack.getCount());
                    }
                }
            }
        }
    }

    private List<Item> getTop4RarestSeeds() {
        return fieldCropCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(4)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private boolean isSeed(Item item) { return item instanceof ItemNameBlockItem; }

    private int countItems(Villager farmer, Item item) { return farmer.getInventory().countItem(item); }

    private void transferToChest(Container chest, ItemStack stack, int amountToTransfer) {
        int amountLeft = amountToTransfer;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (amountLeft <= 0) break;
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty()) {
                chest.setItem(i, stack.split(amountLeft));
                chest.setChanged();
                amountLeft = 0;
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int space = Math.min(chest.getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount();
                if (space > 0) {
                    int add = Math.min(space, amountLeft);
                    slot.grow(add);
                    stack.shrink(add);
                    amountLeft -= add;
                    chest.setChanged();
                }
            }
        }
    }

    private void transferToVillager(Villager villager, ItemStack chestStack, int amountToTransfer) {
        SimpleContainer inv = villager.getInventory();
        int amountLeft = amountToTransfer;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (amountLeft <= 0) break;
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                inv.setItem(i, chestStack.split(amountLeft));
                amountLeft = 0;
            } else if (ItemStack.isSameItemSameComponents(slot, chestStack)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space > 0) {
                    int add = Math.min(space, amountLeft);
                    slot.grow(add);
                    chestStack.shrink(add);
                    amountLeft -= add;
                }
            }
        }
    }
}