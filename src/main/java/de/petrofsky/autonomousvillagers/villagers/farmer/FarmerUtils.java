package de.petrofsky.autonomousvillagers.villagers.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;

import java.util.ArrayList;
import java.util.List;

public class FarmerUtils {

    public static List<BlockPos> getFieldBlocks(BlockPos composter) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = -2; y <= 2; y++) {
                    blocks.add(composter.offset(x, y, z));
                }
            }
        }
        return blocks;
    }

    public static BlockPos getClosestAdjacentBlock(ServerLevel level, BlockPos target, BlockPos villagerPos) {
        BlockPos best = null;
        double minDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adj = target.relative(dir);
            if (level.getBlockState(adj).isAir() && level.getBlockState(adj.above()).isAir()) {
                double dist = adj.distSqr(villagerPos);
                if (dist < minDistance) {
                    minDistance = dist;
                    best = adj;
                }
            }
        }
        return best != null ? best : target;
    }

    public static boolean hasItem(Villager farmer, Item item) {
        return farmer.getInventory().hasAnyMatching(s -> s.is(item));
    }

    public static boolean hasPlantableSeeds(Villager farmer) {
        return farmer.getInventory().hasAnyMatching(stack -> stack.getItem() instanceof ItemNameBlockItem);
    }
}
