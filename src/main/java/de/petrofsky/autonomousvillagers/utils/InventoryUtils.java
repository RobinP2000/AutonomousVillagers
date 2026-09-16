package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventoryUtils {

    public static boolean hasAny(Villager villager, TagKey<Item> tag) {
        return villager.getInventory().hasAnyMatching(itemStack -> itemStack.is(tag));
    }

    public static boolean hasAny(Villager villager, Item item) {
        return villager.getInventory().hasAnyMatching(itemStack -> itemStack.is(item));
    }

    public static int count(Villager villager, TagKey<Item> tag) {
        int totalCount = 0;
        SimpleContainer inventory = villager.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack itemStack = inventory.getItem(i);

            if (!itemStack.isEmpty() && itemStack.is(tag)) {
                totalCount += itemStack.getCount();
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
}
