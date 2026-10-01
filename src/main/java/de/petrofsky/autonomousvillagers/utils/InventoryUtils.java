package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryUtils {

    public static boolean hasAny(Villager villager, TagKey<Item> tag) {
        return villager.getInventory().hasAnyMatching(itemStack -> itemStack.is(tag));
    }

    public static boolean hasAny(Villager villager, Item item) {
        return villager.getInventory().hasAnyMatching(itemStack -> itemStack.is(item));
    }

    public static int countEmptySlots(Container container) {
        int totalCount = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
               totalCount++;
            }
        }
        return totalCount;
    }

    public static int count(Container container, Item item) {
        int totalCount = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack itemStack = container.getItem(i);
            if (!itemStack.isEmpty() && itemStack.is(item)) {
                totalCount += itemStack.getCount();
            }
        }

        return totalCount;
    }

    public static int count(Villager villager, TagKey<Item> tag, boolean countDifferentItems) {
        int totalCount = 0;
        SimpleContainer inventory = villager.getInventory();
        ArrayList<Item> items = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack itemStack = inventory.getItem(i);

            if(!countDifferentItems) {
                if (!itemStack.isEmpty() && itemStack.is(tag)) {
                    totalCount += itemStack.getCount();
                }
            } else {
                if(!items.contains(itemStack.getItem())) {
                    totalCount++;
                    items.add(itemStack.getItem());
                }
            }
        }

        return totalCount;
    }

    public static void increase(Villager villager, TagKey<Item> tag, int count) {
        if (count <= 0) return;

        SimpleContainer inventory = villager.getInventory();
        Item itemType = null;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack.is(tag)) {
                itemType = itemStack.getItem();

                int space = itemStack.getMaxStackSize() - itemStack.getCount();
                if (space > 0) {
                    int toAdd = Math.min(count, space);
                    itemStack.grow(toAdd);
                    count -= toAdd;

                    if (count <= 0) return;
                }
            }
        }

        if (itemType == null) {
            itemType = BuiltInRegistries.ITEM.getTag(tag)
                    .flatMap(holders -> holders.stream().findFirst())
                    .map(Holder::value)
                    .orElse(null);
        }

        if (itemType == null) return;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack.isEmpty()) {
                int maxStack = itemType.getDefaultInstance().getMaxStackSize();
                int toAdd = Math.min(count, maxStack);

                inventory.setItem(i, new ItemStack(itemType, toAdd));
                count -= toAdd;

                if (count <= 0) return;
            }
        }
    }

    public static void decrease(Villager villager, TagKey<Item> tag) {
        for(int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = villager.getInventory().getItem(i);
            if(itemStack.is(tag)) {
                itemStack.shrink(1);

                if(itemStack.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    public static void decrease(Villager villager, Item item) {
        for(int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = villager.getInventory().getItem(i);
            if(itemStack.is(item)) {
                itemStack.shrink(1);

                if(itemStack.isEmpty()) {
                    villager.getInventory().setItem(i, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    public static List<Container> getChestContainer(Level level, List<Map.Entry<BlockPos, BlockState>> chests) {
        return chests.stream().map(entry -> ChestBlock.getContainer(
                (ChestBlock) entry.getValue().getBlock(), entry.getValue(), level, entry.getKey(), true))
                .toList();
    }
}
