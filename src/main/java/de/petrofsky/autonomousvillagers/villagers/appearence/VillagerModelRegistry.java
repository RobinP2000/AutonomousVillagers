package de.petrofsky.autonomousvillagers.villagers.appearence;

import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side event subscriber responsible for registering custom entity models and renderers.
 * <p>
 * <b>Architectural Note on {@code @EventBusSubscriber(value = Dist.CLIENT)}:</b><br>
 * Rendering classes, model definitions, and renderer events only exist on the physical client.
 * Restricting this class to {@link Dist#CLIENT} ensures that dedicated servers never attempt
 * to load these client-only rendering classes, avoiding {@code ClassNotFoundException} crashes on servers.
 */
@EventBusSubscriber(modid = AutonomousVillagers.MODID, value = Dist.CLIENT)
public class VillagerModelRegistry {

    /**
     * Unique identifier for the armed villager model layer in Minecraft's geometry registry.
     */
    public static final ModelLayerLocation VILLAGER_ARMED = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AutonomousVillagers.MODID,
                    "armed_villager"), "main");

    /**
     * Binds the custom mesh geometry (cube definitions) of {@link ArmedVillagerModel}
     * to the {@link #VILLAGER_ARMED} layer location.
     *
     * @param event The client-side event for registering model layer definitions.
     */
    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VILLAGER_ARMED, ArmedVillagerModel::createBodyLayer);
    }

    /**
     * Replaces the vanilla renderer for {@link EntityType#VILLAGER} with our custom {@link ArmedVillagerRenderer}.
     * <p>
     * This override ensures that villagers use our modified model and renderer system capable
     * of displaying arm animations and holding items during work tasks.
     *
     * @param event The client-side event for registering entity renderers.
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.VILLAGER, ArmedVillagerRenderer::new);
    }
}