package de.petrofsky.autonomousvillagers.mixin;

import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.utils.ResourcePreference;
import de.petrofsky.autonomousvillagers.utils.ResourceProfile;
import de.petrofsky.autonomousvillagers.villagers.VillagerProfessions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin to allow custom villagers to pick up specific modded items from the ground.
 * <p>
 * Vanilla Minecraft strictly requires an immutable set of specific item instances
 * during the registration of a {@link net.minecraft.world.entity.npc.VillagerProfession}.
 * To ensure cross-mod compatibility, we bypass this limitation by intercepting the
 * item pickup logic at runtime. This allows our custom professions (like the Modded
 * Forester) to recognize and pick up items dynamically based on item tags.
 */
@Mixin(Villager.class)
public class VillagerPickupMixin {

    /**
     * Injects custom logic into the villager's decision process for picking up items.
     * <p>
     * This method intercepts the {@code wantsToPickUp} method right at the beginning
     * ({@code @At("HEAD")}). It checks if the villager is assigned to our custom profession,
     * and if so, evaluates the item against predefined tags rather than a hardcoded list.
     *
     * @param stack The {@link ItemStack} lying on the ground that the villager is evaluating.
     * @param cir   The callback info containing the return state. We override this using
     *              {@code setReturnValue(true)} to force the villager to pick up the item.
     */
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void customForesterPickup(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Villager villager = (Villager) (Object) this;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        cir.setReturnValue(true);
        return;
      /*  if(!VillagerProfessions.PROFILES.containsKey(profession)) {
            return;
        }

        int emptySlots = InventoryUtils.countEmptySlots(villager.getInventory());
        ResourceProfile profile = VillagerProfessions.PROFILES.get(profession);
        ResourcePreference itemPreference = profile.getItem(stack.getItem());

        if(itemPreference != null) {
            int max = itemPreference.getInventoryMax();
            int min = itemPreference.getInventoryMin();
            int itemCount = InventoryUtils.count(villager.getInventory(), stack.getItem());
            cir.setReturnValue((emptySlots > 1 || min > 0 || itemCount % stack.getMaxStackSize() > 0)
                    && itemCount < max);
        } else {
            List<ResourcePreference> tagPreferences = profile.getByTags(stack.getTags());
            if(tagPreferences.isEmpty()) {
                return;
            }
            ResourcePreference tagPreference = tagPreferences.getFirst();
            int max = tagPreference.getInventoryMax();
            int min = tagPreference.getInventoryMin();
            int itemCount = InventoryUtils.count(villager.getInventory(), stack.getItem());
            int itemsWithTagCount = InventoryUtils.count(villager, tagPreference.getTag(), true);
            boolean singleItemCondition = itemsWithTagCount <= 1 && itemCount > 0
                    && (emptySlots > 1 || min > 0 || itemCount % stack.getMaxStackSize() > 0);
            boolean multipleItemsCondition = itemsWithTagCount == 2 && itemCount > 0
                    && ( itemCount % stack.getMaxStackSize() > 0 || emptySlots > 1);

            cir.setReturnValue((singleItemCondition || multipleItemsCondition) && itemCount < max);
        }*/
    }
}
