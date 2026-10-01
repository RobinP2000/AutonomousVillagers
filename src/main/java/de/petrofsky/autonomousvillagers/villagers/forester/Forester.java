package de.petrofsky.autonomousvillagers.villagers.forester;

import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import de.petrofsky.autonomousvillagers.utils.ResourcePreference;
import de.petrofsky.autonomousvillagers.utils.ResourceProfile;
import de.petrofsky.autonomousvillagers.villagers.VillagerProfessions;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

public class Forester {

    public final static List<ResourcePreference> PREFERENCES = List.of(
            new ResourcePreference(Items.DIRT, 5, 64, 0, 0),
            new ResourcePreference(ItemTags.LOGS, 0, 16, 0, 15 * 64),
            new ResourcePreference(ItemTags.AXES, 1, 1, 1, 1),
            new ResourcePreference(ItemTags.PLANKS, 0, 64, 32, 4 * 64),
            new ResourcePreference(ItemTags.SAPLINGS, 0, 32, 32, 3 * 64),
            new ResourcePreference(Items.STICK, 0, 32, 4, 64),
            new ResourcePreference(Items.BONE_MEAL, 64, 64, 64, 64),
            new ResourcePreference(Items.CRAFTING_TABLE, 0, 1, 0, 0));

    public static final ResourceProfile RESOURCE_PROFILE = new ResourceProfile(
            VillagerProfessions.MODDED_FORESTER.value(), PREFERENCES);

    protected static ForesterBlockEntity getBlockEntity(ServerLevel level, Villager villager) {
        Optional<GlobalPos> jobOpt = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobOpt.isEmpty()) return null;
        GlobalPos jobSite = jobOpt.get();
        if (!jobSite.dimension().equals(level.dimension())) return null;
        if (level.getBlockEntity(jobSite.pos()) instanceof ForesterBlockEntity blockEntity) return blockEntity;
        return null;
    }
}
