package de.petrofsky.autonomousvillagers.villagers.forester;

import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ManageResourcesBehavior extends AbstractVillagerBehavior {

    private static final int MAX_DURATION = 1000;

    public ManageResourcesBehavior() {
        super(Map.of(
                MemoryModuleType.JOB_SITE,   MemoryStatus.VALUE_PRESENT
        ), MAX_DURATION, 10_000, 1_000);
    }

    @Override
    protected boolean canBegin(@NotNull ServerLevel level, @NotNull Villager villager) {
        SimpleContainer inventory = villager.getInventory();

/*
        if(InventoryUtils.count(villager, ItemTags.AXES))

        ForesterBlockEntity be = Forester.getBlockEntity(level, villager);
        if (be == null) return false;
        return be.hasTreeTarget() || findBaseLog(villager, level, be) != null;*/
        return false;
    }

    @Override
    protected boolean canContinue(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        return false;
    }

    @Override
    protected void start(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {

    }

    @Override
    protected void executeBehavior(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {

    }
}
