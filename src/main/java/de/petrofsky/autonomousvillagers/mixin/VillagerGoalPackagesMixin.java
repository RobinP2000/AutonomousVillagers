package de.petrofsky.autonomousvillagers.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import de.petrofsky.autonomousvillagers.villagers.VillagerProfessions;
import de.petrofsky.autonomousvillagers.villagers.farmer.*;
import de.petrofsky.autonomousvillagers.villagers.forester.*;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin to inject custom AI behaviors (goals) into modded villagers during their work hours.
 * <p>
 * Vanilla Minecraft strictly hardcodes the behavior packages for villagers inside
 * {@link VillagerGoalPackages}. To allow our custom professions (like the Modded Farmer
 * or Forester) to perform unique tasks, we must intercept the creation of their work
 * packages and append our custom {@link BehaviorControl} instances.
 */
@Mixin(VillagerGoalPackages.class)
public class VillagerGoalPackagesMixin {

    /**
     * Injects custom behaviors into the villager's work package.
     * <p>
     * This method intercepts the {@code getWorkPackage} method right before it returns
     * ({@code @At("RETURN")}). It takes the already constructed vanilla list of behaviors,
     * checks if the villager has one of our custom professions, and if so, appends our
     * specialized tasks.
     * <p>
     * The priority of tasks is determined by the integer in the {@link Pair} (lower number = higher priority).
     *
     * @param profession    The profession of the villager requesting a work package.
     * @param speedModifier The movement speed modifier for the villager during work tasks.
     * @param cir           The callback info containing the originally returned behavior list,
     *                      which we override with our modified list using {@code setReturnValue}.
     */
    @Inject(method = "getWorkPackage", at = @At("RETURN"), cancellable = true)
    private static void autonomousvillagers$addAdvancedFarmerBehavior(
            VillagerProfession profession, float speedModifier,
            CallbackInfoReturnable<List<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {

        if (profession == VillagerProfessions.MODDED_FARMER.value()) {

            // The original list returned by vanilla is immutable, so we must copy it to a mutable ArrayList.
            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> originalList = cir.getReturnValue();
            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> modifiedList = new ArrayList<>(originalList);

            // Inject custom farmer behaviors.
            // Priority 0 is the highest, ensuring these tasks override default wandering if conditions are met.
            modifiedList.add(Pair.of(0, new PlantCareBehavior()));
            modifiedList.add(Pair.of(1, new AccessChestBehavior()));
            modifiedList.add(Pair.of(2, new CreateFarmlandBehavior()));
            modifiedList.add(Pair.of(3, new DigWaterHoleBehavior()));
            modifiedList.add(Pair.of(4, new BreakBlockBehavior()));

            // Wrap the modified list back into an ImmutableList, as the Minecraft AI system expects it,
            // and override the return value.
            cir.setReturnValue(ImmutableList.copyOf(modifiedList));
        } else if (profession == VillagerProfessions.MODDED_FORESTER.value()) {

            // Repeat the process for the forester profession.
            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> originalList = cir.getReturnValue();
            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> modifiedList = new ArrayList<>(originalList);

            modifiedList.add(Pair.of(0, new ChopTreeBehavior()));
            modifiedList.add(Pair.of(1, new PlantTreeBehavior()));
            /*
             * TODO: Uncomment and implement additional forester behaviors once they are ready.
             * modifiedList.add(Pair.of(1, new PlantSeedBehavior()));
             * modifiedList.add(Pair.of(2, new OpenChestBehavior()));
             * modifiedList.add(Pair.of(3, new CraftBehavior()));
             * modifiedList.add(Pair.of(4, new BuildBehavior()));
             */
            cir.setReturnValue(ImmutableList.copyOf(modifiedList));
        }
    }
}
