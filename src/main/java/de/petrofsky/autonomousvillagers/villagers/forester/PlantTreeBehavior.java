package de.petrofsky.autonomousvillagers.villagers.forester;

import de.petrofsky.autonomousvillagers.blocks.ForesterBlock;
import de.petrofsky.autonomousvillagers.blocks.ForesterBlockEntity;
import de.petrofsky.autonomousvillagers.utils.BlockDataUtils;
import de.petrofsky.autonomousvillagers.utils.InventoryUtils;
import de.petrofsky.autonomousvillagers.utils.PerformanceUtils;
import de.petrofsky.autonomousvillagers.utils.geometry.ShapeUtils;
import de.petrofsky.autonomousvillagers.utils.pathfinding.SurfaceFinder;
import de.petrofsky.autonomousvillagers.village.Village;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import de.petrofsky.autonomousvillagers.villagers.goals.PlaceBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PlantTreeBehavior extends AbstractVillagerBehavior {

    private static final int MAX_DURATION = 1000;
    private BlockPos nextSpot;

    public PlantTreeBehavior() {
        super(Map.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), MAX_DURATION,
                20_000, 1_000);
    }

    public boolean matchesTaskCondition(Villager villager) {
        int saplingCount = InventoryUtils.count(villager, ItemTags.SAPLINGS, false);
        return saplingCount > 0;
    }

    @Override
    protected boolean canBegin(@NotNull ServerLevel level, @NotNull Villager villager) {
        ForesterBlockEntity be = Forester.getBlockEntity(level, villager);
        if(be == null) {
            return false;
        }
        boolean hasSaplings = matchesTaskCondition(villager);
        if(!hasSaplings) {
            return false;
        }
        long nanos = System.nanoTime();
        List<BlockPos> targets = new SurfaceFinder(level, be.getBlockPos(), 8, 11)
                .blocked(BlockTags.LOGS).blocked(BlockTags.SAPLINGS).blocked(Blocks.CHEST).distanceToBlocked(4)
                .between(villager.blockPosition(), 1, 8).minOriginDistance(2)
                .ground(Blocks.GRASS_BLOCK).scan(true);
        PerformanceUtils.timeMeasured("Surface Finder", nanos, true);

        if(!targets.isEmpty()) {
            this.nextSpot = targets.getFirst();
        }
        return !targets.isEmpty();
    }

    @Override
    protected void start(ServerLevel p_22540_, Villager p_22541_, long p_22542_) {
        super.start(p_22540_, p_22541_, p_22542_);
        clearGoal();
    }

    @Override
    protected boolean canContinue(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        boolean goalDone = hasGoal() && currentGoal instanceof PlaceBlockGoal goal && ! goal.isInProgress();
        return !goalDone && matchesTaskCondition(villager);
    }

    @Override
    protected void executeBehavior(@NotNull ServerLevel level, @NotNull Villager villager, long gameTime) {
        Optional<ItemStack> itemStackResult = villager.getInventory().getItems().stream().filter(itemStack ->
                itemStack.getTags().anyMatch(tag -> tag == ItemTags.SAPLINGS)).findFirst();
        if(itemStackResult.isEmpty()) return;
        ItemStack itemStack = itemStackResult.get();
        Block placementBlock = Block.byItem(itemStack.getItem());

        placeBlockNow(villager, nextSpot, placementBlock).start();
    }
}