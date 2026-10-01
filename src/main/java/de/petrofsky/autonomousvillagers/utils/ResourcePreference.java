package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ResourcePreference {

    private final int inventoryMax;
    private final int inventoryMin;
    private final int chestMax;
    private final int chestMin;

    private Item item;
    private TagKey<Item> tag;

    public ResourcePreference(TagKey<Item> tag, int inventoryMin, int inventoryMax, int chestMin, int chestMax) {
        this.tag = tag;
        this.inventoryMin = Math.max(0, inventoryMin);
        this.inventoryMax = Math.max(0, inventoryMax);
        this.chestMin = Math.max(0, chestMin);
        this.chestMax = Math.max(0, chestMax);
    }

    public ResourcePreference(Item item, int inventoryMin, int inventoryMax, int chestMin, int chestMax) {
        this.item = item;
        this.inventoryMin = Math.max(0, inventoryMin);
        this.inventoryMax = Math.max(0, inventoryMax);
        this.chestMin = Math.max(0, chestMin);
        this.chestMax = Math.max(0, chestMax);
    }

    public int getInventoryMax() {
        return inventoryMax;
    }

    public int getInventoryMin() {
        return inventoryMin;
    }

    public int getChestMax() {
        return chestMax;
    }

    public int getChestMin() {
        return chestMin;
    }

    public int max() {
        return chestMax + inventoryMax;
    }

    public boolean is(Item item) {
        return this.item != null && this.item == item;
    }

    public boolean is(TagKey<Item> tag) {
        return this.tag != null && this.tag == tag;
    }

    public Item getItem() {
        return item;
    }

    public TagKey<Item> getTag() {
        return tag;
    }
}
