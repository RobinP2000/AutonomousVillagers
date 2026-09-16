package de.petrofsky.autonomousvillagers.blocks;

import com.mojang.serialization.MapCodec;
import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jetbrains.annotations.Nullable;

public class ForesterBlock extends BaseEntityBlock {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AutonomousVillagers.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AutonomousVillagers.MODID);

    public static final DeferredItem<Item> WOOD_CHOP_BLOCK_ITEM = ITEMS.register("wood_chop_block",
            () -> new BlockItem(ForesterBlock.WOOD_CHOP_BLOCK.get(), new Item.Properties()));

    public static final DeferredBlock<ForesterBlock> WOOD_CHOP_BLOCK = BLOCKS.register("wood_chop_block",
            () -> new ForesterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
            ));
    public static final MapCodec<ForesterBlock> CODEC =
            simpleCodec(ForesterBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }

    public ForesterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForesterBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
