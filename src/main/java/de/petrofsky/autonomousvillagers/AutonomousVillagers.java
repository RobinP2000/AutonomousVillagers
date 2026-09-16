package de.petrofsky.autonomousvillagers;

import com.mojang.logging.LogUtils;
import de.petrofsky.autonomousvillagers.blocks.ForesterBlock;
import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import de.petrofsky.autonomousvillagers.debug.DebugSystem;
import de.petrofsky.autonomousvillagers.villagers.VillagerProfessions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AutonomousVillagers.MODID)
public class AutonomousVillagers {

        public static final String MODID = "autonomousvillagers";
        private static final Logger LOGGER = LogUtils.getLogger();

        public AutonomousVillagers(IEventBus modEventBus, ModContainer modContainer) {
            LOGGER.info("Loading mod");
            ForesterBlock.register(modEventBus);
            ForesterBlockEntity.register(modEventBus);

            VillagerProfessions.register(modEventBus);

        }
}