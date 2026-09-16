package de.petrofsky.autonomousvillagers.villagers.goals;

import de.petrofsky.autonomousvillagers.utils.BlockDataUtils;
import de.petrofsky.autonomousvillagers.villagers.AbstractVillagerBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BreakBlockGoal extends Goal{

    private final BlockPos targetPos;
    private BlockPos breakPos;
    private int speed;
    private int ticks = 0;
    private final Level level;
    private Block breakBlock;
    private final List<TagKey<Block>> allowedTags;
    private final List<Block> allowedBlocks;


    public BreakBlockGoal(Villager villager, BlockPos targetPos, List<TagKey<Block>> allowedTags,
                          List<Block> allowedBlocks) {
        super(villager);
        this.targetPos = targetPos;
        this.level = villager.level();
        this.allowedTags = allowedTags;
        this.allowedBlocks = allowedBlocks;
    }

    public BreakBlockGoal(Villager villager, BlockPos targetPos, List<TagKey<Block>> allowedTags) {
        this(villager, targetPos, allowedTags, null);
    }

    @Override
    protected void tick() {
        BlockState breakPosState = getLevel().getBlockState(getBreakPos());
        if(!getBreakBlock().equals(breakPosState.getBlock())) {
            fail();
            return;
        }

        getVillager().getNavigation().stop();
        getVillager().getLookControl().setLookAt(breakPos.getX() + 0.5D,
                breakPos.getY() + 0.5D, breakPos.getZ() + 0.5D);

        if(this.ticks < this.speed) {
            this.ticks++;
            int progress = (int) (((float) this.ticks / this.speed) * 10.0F);
            this.level.destroyBlockProgress(getVillager().getId(), breakPos, progress);
            this.ticks++;
        } else {
            level.destroyBlock(breakPos, true, getVillager());
              success();
        }
    }

    @Override
    public void init() {
        Vec3 from = getVillager().getEyePosition();
        Vec3 to = Vec3.atCenterOf(targetPos);

        ClipContext clipContext = new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, getVillager());
        BlockHitResult hit = level.clip(clipContext);
        BlockPos chopPosition = hit.getBlockPos();
        if (hit.getType() == HitResult.Type.MISS) {
            chopPosition = targetPos;
        }
        this.breakPos = chopPosition;

        BlockState chopPosState = this.level.getBlockState(this.breakPos);
        boolean hasValidTag = allowedTags != null && allowedTags.stream().anyMatch(chopPosState::is);
        boolean hasValidBlock = allowedBlocks != null && allowedBlocks.stream().anyMatch(chopPosState::is);
        boolean hasValidType = hasValidTag || hasValidBlock;
        ItemStack tool = getVillager().getMainHandItem();
        boolean hasCorrectTool = !chopPosState.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(chopPosState);
        if(!AbstractVillagerBehavior.blockInTouchRange(getVillager(), chopPosition) || ! hasValidType ||
                ! hasCorrectTool) {
            fail();
        }
        this.breakBlock = chopPosState.getBlock();
        this.speed = calculateBreakingTicks();
    }


    public BlockPos getTargetPos() {
        return targetPos;
    }

    public BlockPos getBreakPos() {
        return breakPos;
    }

    public Block getBreakBlock() {
        return this.breakBlock;
    }

    public Level getLevel() {
        return this.level;
    }

    private int calculateBreakingTicks() {
        Level level = getLevel();
        BlockState state = level.getBlockState(getBreakPos());
        ItemStack tool = getVillager().getMainHandItem();

        float hardness = state.getDestroySpeed(level, getBreakPos());
        if (hardness < 0) return -1;
        boolean correctTool = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);

        float speedMultiplier = tool.getDestroySpeed(state);

        if (correctTool) {
            if (speedMultiplier > 1.0f) {
                int efficiencyLevel = 0;
                var registry = getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                var efficiencyHolder = registry.getHolder(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY);

                if (efficiencyHolder.isPresent()) {
                    efficiencyLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getTagEnchantmentLevel(efficiencyHolder.get(), tool);
                }
                if (efficiencyLevel > 0) {
                    speedMultiplier += (float) (efficiencyLevel * efficiencyLevel + 1);
                }
            }
        } else {
            speedMultiplier = 1.0f;
        }

        float damagePerTick = speedMultiplier / hardness;
        if (correctTool) {
            damagePerTick /= 30.0f;
        } else {
            damagePerTick /= 100.0f;
        }

        if (damagePerTick <= 0) return -1;

        int ticks = (int) Math.ceil(1.0f / damagePerTick);
        return Math.max(ticks, 1);
    }
}
