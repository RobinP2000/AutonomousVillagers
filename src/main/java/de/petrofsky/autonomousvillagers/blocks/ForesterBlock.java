package de.petrofsky.autonomousvillagers.blocks;

import com.mojang.serialization.MapCodec;
import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the physical workstation block for the Forester villager.
 * <p>
 * This class extends {@link BaseEntityBlock} because the block requires a {@link BlockEntity}
 * ({@link ForesterBlockEntity}) to persist the complex logic and queue states of tree-chopping operations.
 * It also handles the NeoForge lifecycle registration for both the block itself and its item representation.
 */
public class ForesterBlock extends BaseEntityBlock {

    /** Deferred register for all blocks in the Autonomous Villagers mod. */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AutonomousVillagers.MODID);

    /** Deferred register for all items in the Autonomous Villagers mod. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AutonomousVillagers.MODID);

    /**
     * The registered BlockItem for the Wood Chop Block.
     * <p>
     * This allows the block to exist in a player's inventory and be placed in the world.
     * It references the registered block via {@link #WOOD_CHOP_BLOCK}.
     */
    public static final DeferredItem<Item> WOOD_CHOP_BLOCK_ITEM = ITEMS.register("wood_chop_block",
            () -> new BlockItem(ForesterBlock.WOOD_CHOP_BLOCK.get(), new Item.Properties()));

    /**
     * The registered Forester Block instance.
     * <p>
     * Defines the physical properties of the block in the world, such as taking as long
     * to break as standard wood, sounding like wood when walked on/broken, and requiring
     * an axe (the correct tool) to drop its item when mined by a player.
     */
    public static final DeferredBlock<ForesterBlock> WOOD_CHOP_BLOCK = BLOCKS.register("wood_chop_block",
            () -> new ForesterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .requiresCorrectToolForDrops()
            ));

    /**
     * The codec required for serializing and deserializing this block type.
     * <p>
     * Mojang's data-driven architecture requires codecs for all block registrations to
     * support dynamic registry loading and syncing block states over the network.
     */
    public static final MapCodec<ForesterBlock> CODEC =
            simpleCodec(ForesterBlock::new);

    /**
     * Hooks the deferred registers into the main mod event bus.
     * <p>
     * This method must be called during mod initialization to ensure the block and
     * its item are actually injected into the game's item/block registries.
     *
     * @param eventBus The mod's primary event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }

    /**
     * Constructs a new ForesterBlock with the specified physical properties.
     *
     * @param properties The block behavior properties (e.g., strength, sound).
     */
    public ForesterBlock(Properties properties) {
        super(properties);
    }

    /**
     * Links this block to its corresponding {@link BlockEntity}.
     * <p>
     * Called automatically by the game whenever this block is placed in the world.
     *
     * @param pos   The world coordinate where the block is placed.
     * @param state The state of the block being placed.
     * @return A new instance of {@link ForesterBlockEntity} to manage the forester's logic.
     */
    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ForesterBlockEntity(pos, state);
    }

    /**
     * Defines how the game should render this block.
     * <p>
     * <b>Crucial:</b> {@link BaseEntityBlock} overrides the default render shape to {@link RenderShape#INVISIBLE}
     * by default (assuming the block will be rendered by a custom BlockEntityRenderer). Since this block
     * relies on a standard JSON block model rather than a custom Java renderer, we must explicitly return
     * {@link RenderShape#MODEL} so the block is actually visible in the world.
     *
     * @param state The current state of the block.
     * @return {@link RenderShape#MODEL} to use standard JSON models.
     */
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Returns the codec used to serialize this specific block type.
     *
     * @return The static {@link #CODEC} instance.
     */
    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
