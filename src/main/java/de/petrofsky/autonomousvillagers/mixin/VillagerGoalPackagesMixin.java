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

@Mixin(VillagerGoalPackages.class)
public class VillagerGoalPackagesMixin {

    @Inject(method = "getWorkPackage", at = @At("RETURN"), cancellable = true)
    private static void autonomousvillagers$addAdvancedFarmerBehavior(
            VillagerProfession profession, float speedModifier,
            CallbackInfoReturnable<List<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {

        if (profession == VillagerProfessions.MODDED_FARMER.value()) {

            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> originalList = cir.getReturnValue();

            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> modifiedList = new ArrayList<>(originalList);

            modifiedList.add(Pair.of(0, new PlantCareBehavior()));
            modifiedList.add(Pair.of(1, new AccessChestBehavior()));
            modifiedList.add(Pair.of(2, new CreateFarmlandBehavior()));
            modifiedList.add(Pair.of(3, new DigWaterHoleBehavior()));
            modifiedList.add(Pair.of(4, new BreakBlockBehavior()));

            cir.setReturnValue(ImmutableList.copyOf(modifiedList));
        } else if (profession == VillagerProfessions.MODDED_FORESTER.value()) {
            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> originalList = cir.getReturnValue();
            List<Pair<Integer, ? extends BehaviorControl<? super Villager>>> modifiedList = new ArrayList<>(originalList);

            modifiedList.add(Pair.of(0, new ChopTreeBehavior()));
           /* modifiedList.add(Pair.of(1, new PlantSeedBehavior()));
            modifiedList.add(Pair.of(2, new OpenChestBehavior()));
            modifiedList.add(Pair.of(3, new CraftBehavior()));
            modifiedList.add(Pair.of(4, new BuildBehavior()));*/
            cir.setReturnValue(ImmutableList.copyOf(modifiedList));
        }
    }
}
