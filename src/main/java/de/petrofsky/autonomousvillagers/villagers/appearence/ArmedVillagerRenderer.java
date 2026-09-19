package de.petrofsky.autonomousvillagers.villagers.appearence;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

/**
 * Custom entity renderer for villagers that enables arm animations and item rendering.
 * <p>
 * Standard vanilla villagers use {@code VillagerRenderer} and {@code VillagerModel}, which
 * lack articulated arms capable of holding tools or performing swing animations (e.g. chopping or farming).
 * By subclassing {@link MobRenderer} with a custom {@link ArmedVillagerModel}, this renderer allows
 * villagers to visually hold items in their hands while preserving their default profession visuals.
 * <p>
 * Registered layers:
 * <ul>
 *   <li>{@link ItemInHandLayer}: Renders items (axes, hoes, picks) held in the villager's hands.</li>
 *   <li>{@link VillagerProfessionLayer}: Renders profession-specific overlays (clothes, hats) on top of the model.</li>
 * </ul>
 */
public class ArmedVillagerRenderer extends MobRenderer<Villager, ArmedVillagerModel> {

    /** The default base texture applied to the villager entity body. */
    private static final ResourceLocation VILLAGER_BASE_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    /**
     * Constructs a new ArmedVillagerRenderer.
     * <p>
     * Bakes the custom {@link ArmedVillagerModel} layer from the client context and attaches
     * essential render layers (held items and profession clothing) to ensure full visual feature parity.
     *
     * @param ctx The renderer context provided by Minecraft during renderer initialization.
     */
    public ArmedVillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ArmedVillagerModel(ctx.bakeLayer(VillagerModelRegistry.VILLAGER_ARMED)), 0.5F);

        // Enables rendering of held items (e.g. tools, items) in the main or off hand.
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));

        // Re-applies standard and modded profession textures (farmer aprons, forester clothes) over the custom model.
        this.addLayer(new VillagerProfessionLayer<>(this, ctx.getResourceManager(), "villager"));
    }

    /**
     * Retrieves the base skin texture for the given villager entity.
     *
     * @param entity The villager entity instance being rendered.
     * @return The {@link ResourceLocation} pointing to the standard villager base texture.
     */
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Villager entity) {
        return VILLAGER_BASE_SKIN;
    }
}