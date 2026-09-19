package de.petrofsky.autonomousvillagers.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enables arm-swinging animations for Villagers.
 * <p>
 * In vanilla Minecraft, calling {@link LivingEntity#swing} initializes the swing state variables
 * ({@code swinging} and {@code swingTime}), but the actual animation progression is driven
 * by {@link LivingEntity#updateSwingTime()}. Since the vanilla game loop only invokes this update
 * for Players and Monsters, Villagers never visually execute the swing animation, even if
 * {@code swing()} is explicitly called during custom behaviors (e.g., chopping trees or farming).
 * <p>
 * This mixin bridges that gap by injecting the {@code updateSwingTime()} call directly into the
 * {@code aiStep()} method for all Villager entities. {@code aiStep()} is executed on both the
 * client and server, ensuring that the animation state stays synchronized across both sides.
 * <p>
 * <b>Important:</b> This mixin must remain in the common mixin configuration rather than the
 * client-only configuration. Even though rendering is client-side, the server must also track
 * the accurate swing time for logical consistency.
 */
@Mixin(LivingEntity.class)
public abstract class VillagerSwingMixin {

    /**
     * Exposes the internal updateSwingTime method from {@link LivingEntity}.
     * <p>
     * The @Shadow annotation acts as a placeholder. It tells the Java compiler: "Trust me,
     * this method will exist at runtime in the target class." This allows us to call
     * a method that otherwise wouldn't be accessible within the isolated mixin class.
     */
    @Shadow
    protected void updateSwingTime() {}

    /**
     * Injects the swing time update at the beginning of the entity's AI step.
     * <p>
     * <b>Note on @SuppressWarnings("ConstantConditions"):</b><br>
     * The IDE flags {@code (Object) this instanceof Villager} as always false because
     * it evaluates the code as standard Java, where this mixin class does not inherit from Villager.
     * However, at runtime, the Mixin framework injects this exact code block directly into
     * {@link LivingEntity}. In that runtime context, {@code this} can indeed be a Villager.
     * The warning is a false positive and safe to suppress.
     *
     * @param ci The callback info provided by Mixin.
     */
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void autonomousvillagers$updateVillagerSwingTime(CallbackInfo ci) {
        if ((Object) this instanceof Villager) {
            this.updateSwingTime();
        }
    }
}
