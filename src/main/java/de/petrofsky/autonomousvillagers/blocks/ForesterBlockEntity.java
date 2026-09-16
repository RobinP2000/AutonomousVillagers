package de.petrofsky.autonomousvillagers.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import de.petrofsky.autonomousvillagers.AutonomousVillagers;

public class ForesterBlockEntity extends BlockEntity {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AutonomousVillagers.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForesterBlockEntity>> TYPE =
            BLOCK_ENTITIES.register("forester_block_entity",
                    () -> BlockEntityType.Builder
                            .of(ForesterBlockEntity::new, ForesterBlock.WOOD_CHOP_BLOCK.get())
                            .build(null));


    private int fieldDirX  = 0;
    private int fieldDirZ  = 0;
    private int fieldWidth = 12;
    private int fieldDepth = 12;

    public ForesterBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE.get(), pos, state);
    }

    public void setField(int[] dir, int width, int depth) {
        this.fieldDirX  = dir[0];
        this.fieldDirZ  = dir[1];
        this.fieldWidth = width;
        this.fieldDepth = depth;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("FieldDirX",  fieldDirX);
        tag.putInt("FieldDirZ",  fieldDirZ);
        tag.putInt("FieldWidth", fieldWidth);
        tag.putInt("FieldDepth", fieldDepth);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fieldDirX  = tag.getInt("FieldDirX");
        fieldDirZ  = tag.getInt("FieldDirZ");
        fieldWidth = tag.getInt("FieldWidth");
        fieldDepth = tag.getInt("FieldDepth");
    }

    public int[] getFieldDir()  { return new int[]{fieldDirX, fieldDirZ}; }
    public int   getFieldWidth(){ return fieldWidth; }
    public int   getFieldDepth(){ return fieldDepth; }

    public AABB getFieldBounds() {
        BlockPos origin = getBlockPos();
        int perpX = fieldDirZ;
        int perpZ = fieldDirX;
        int half  = fieldWidth / 2;

        int x1 = origin.getX() + fieldDirX          - perpX * half;
        int z1 = origin.getZ() + fieldDirZ          - perpZ * half;
        int x2 = origin.getX() + fieldDirX * fieldDepth + perpX * half;
        int z2 = origin.getZ() + fieldDirZ * fieldDepth + perpZ * half;

        return new AABB(
                Math.min(x1, x2), origin.getY() - 1, Math.min(z1, z2),
                Math.max(x1, x2), origin.getY() + 10, Math.max(z1, z2)
        );
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}