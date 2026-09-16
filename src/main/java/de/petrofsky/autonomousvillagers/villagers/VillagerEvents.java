package de.petrofsky.autonomousvillagers.villagers;

import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = AutonomousVillagers.MODID)
public class VillagerEvents {

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.getVillagerData().getProfession() == VillagerProfessions.MODDED_FARMER.value()) {
                event.setCanceled(true);
            }
        }
    }
}