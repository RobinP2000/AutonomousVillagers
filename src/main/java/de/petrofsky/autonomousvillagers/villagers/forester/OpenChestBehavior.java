package de.petrofsky.autonomousvillagers.villagers.forester;

import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

import java.util.Map;

public class OpenChestBehavior extends Behavior<Villager>  {
    public OpenChestBehavior(Map<MemoryModuleType<?>, MemoryStatus> p_22528_) {
        super(p_22528_);
    }
}
