package de.petrofsky.autonomousvillagers.villagers;

import com.google.common.collect.ImmutableSet;
import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import de.petrofsky.autonomousvillagers.blocks.ForesterBlock;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public class VillagerProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, AutonomousVillagers.MODID);

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(BuiltInRegistries.POINT_OF_INTEREST_TYPE, AutonomousVillagers.MODID);

    public static final Holder<PoiType> FARMER_POI = POI_TYPES.register("modded_farmer_poi",
            () -> new PoiType(getBlockStates(Blocks.COMPOSTER), 1, 1));

    public static final Holder<VillagerProfession> MODDED_FARMER = PROFESSIONS.register("modded_farmer",
    () -> new VillagerProfession(
            "modded_farmer",
            holder -> holder.value() == FARMER_POI.value(),
            poiTypeHolder -> poiTypeHolder.value() == FARMER_POI.value(),
            ImmutableSet.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.POTATO, Items.CARROT,
                    Items.BEETROOT_SEEDS, Items.BONE_MEAL),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_FARMER));

    public static final Holder<PoiType> FORESTER_POI = POI_TYPES.register("modded_forester_poi",
            () -> new PoiType(getBlockStates(ForesterBlock.WOOD_CHOP_BLOCK.get()), 1, 1));

    public static final Holder<VillagerProfession> MODDED_FORESTER = PROFESSIONS.register("modded_forester",
            () -> new VillagerProfession(
                    "modded_forester",
                    holder -> holder.value() == FORESTER_POI.value(),
                    poiTypeHolder -> poiTypeHolder.value() == FORESTER_POI.value(),
                    ImmutableSet.of(Items.OAK_WOOD, Items.OAK_LEAVES, Items.OAK_LOG,
                            Items.ACACIA_WOOD, Items.ACACIA_LEAVES, Items.ACACIA_LOG,
                            Items.BIRCH_WOOD, Items.BIRCH_LEAVES, Items.BIRCH_LOG,
                            Items.CHERRY_WOOD, Items.CHERRY_LEAVES, Items.CHERRY_LOG,
                            Items.DARK_OAK_WOOD, Items.DARK_OAK_LEAVES, Items.DARK_OAK_LOG,
                            Items.JUNGLE_WOOD, Items.JUNGLE_LEAVES, Items.JUNGLE_LOG,
                            Items.MANGROVE_WOOD, Items.MANGROVE_LEAVES, Items.MANGROVE_LOG,
                            Items.SPRUCE_WOOD, Items.SPRUCE_LEAVES, Items.SPRUCE_LOG,
                            Items.DIRT
                            ),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_NO));

    public static void register(IEventBus eventBus) {
        PROFESSIONS.register(eventBus);
        POI_TYPES.register(eventBus);
    }

    private static Set<BlockState> getBlockStates(Block p_218074_) {
        return ImmutableSet.copyOf(p_218074_.getStateDefinition().getPossibleStates());
    }
}
