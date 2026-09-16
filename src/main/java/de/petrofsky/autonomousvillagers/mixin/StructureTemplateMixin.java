package de.petrofsky.autonomousvillagers.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {

    @Inject(method = "placeInWorld", at = @At("RETURN"))
    private void autonomousvillagers$addChestToFarms(ServerLevelAccessor level, BlockPos offset, BlockPos pos, StructurePlaceSettings settings, RandomSource random, int flags, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        StructureTemplate template = (StructureTemplate) (Object) this;
        List<StructureTemplate.StructureBlockInfo> composters = template.filterBlocks(pos, settings, Blocks.COMPOSTER);

        if (composters.isEmpty()) return;

        List<BlockPos> searchOffsets = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    searchOffsets.add(new BlockPos(x, y, z));
                }
            }
        }
        searchOffsets.sort(Comparator.comparingDouble(p -> p.distSqr(BlockPos.ZERO)));

        for (StructureTemplate.StructureBlockInfo info : composters) {
            BlockPos composterPos = info.pos();
            if (hasChestNearby(level, composterPos)) continue;

            boolean placed = false;
            for (BlockPos searchOffset : searchOffsets) {
                BlockPos chestPos = composterPos.offset(searchOffset);
                BlockPos belowChest = chestPos.below();

                BlockState targetState = level.getBlockState(chestPos);
                BlockState groundState = level.getBlockState(belowChest);

                boolean canReplace = targetState.isAir() || targetState.canBeReplaced() || targetState.getBlock() instanceof CropBlock;


                boolean solidGround = !groundState.isAir() && groundState.getFluidState().isEmpty();

                if (canReplace && solidGround) {
                    if (groundState.is(Blocks.FARMLAND) || groundState.is(Blocks.DIRT_PATH) || groundState.is(Blocks.GRASS_BLOCK)) {
                        level.setBlock(belowChest, Blocks.DIRT.defaultBlockState(), 3);
                    }

                    Direction facing = getChestFacing(searchOffset);
                    level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
                    placed = true;
                    break;
                }
            }
        }
    }


    private Direction getChestFacing(BlockPos offset) {
        if (Math.abs(offset.getX()) > Math.abs(offset.getZ())) {
            return offset.getX() > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return offset.getZ() > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private boolean hasChestNearby(ServerLevelAccessor level, BlockPos composterPos) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos checkPos = composterPos.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);

                    if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.TRAPPED_CHEST)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}