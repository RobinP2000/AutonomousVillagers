package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ResourceProfile {

    private final HashMap<TagKey<Item>, ResourcePreference> tagPreferences = new HashMap<>();
    private final HashMap<Item, ResourcePreference> itemPreferences = new HashMap<>();
    private final VillagerProfession profession;

    public ResourceProfile(VillagerProfession profession, List<ResourcePreference> resourcePreferences) {
        this.profession = profession;
        for(ResourcePreference preference : resourcePreferences) {
            if(preference.getTag() != null && !tagPreferences.containsKey(preference.getTag())) {
                tagPreferences.put(preference.getTag(), preference);
            } else if(preference.getItem() != null && !itemPreferences.containsKey(preference.getItem())) {
                itemPreferences.put(preference.getItem(), preference);
            }
        }
    }

    public List<TagKey<Item>> getTags() {
        return new ArrayList<>(tagPreferences.keySet());
    }

    public List<Item> getItems() {
        return new ArrayList<>(itemPreferences.keySet());
    }

    public ResourcePreference getTag(TagKey<Item> tag) {
        return tagPreferences.get(tag);
    }

    public ResourcePreference getItem(Item item) {
        return itemPreferences.get(item);
    }

    public List<ResourcePreference> getByTags(Stream<TagKey<Item>> tags) {
        return tags.map(this::getTag).filter(Objects::nonNull).toList();
    }

    public List<ResourcePreference> getTagPreferences() {
        return tagPreferences.values().stream().toList();
    }

    public List<ResourcePreference> getItemPreferences() {
        return itemPreferences.values().stream().toList();
    }

    public VillagerProfession getProfession() {
        return profession;
    }
}
