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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class BreakBlockGoal extends Goal{

    private final BlockPos targetPos;
    private BlockPos breakPos;
    private int speed;
    private int ticks = 0;
    private final Level level;
    private Block breakBlock;
    private MoveToGoal moveToGoal;
    private boolean allowMovement = true;
    private final HashSet<Block> breakableBarrierBlocks = new HashSet<>();
    private final HashSet<TagKey<Block>> breakableBarrierTags = new HashSet<>();
    private final HashSet<Block> breakableBlocks = new HashSet<>();
    private final HashSet<TagKey<Block>> breakableTags = new HashSet<>();
    private boolean multipleBlocks = false;

    public BreakBlockGoal(Villager villager, BlockPos targetPos) {
        super(villager);
        this.targetPos = targetPos;
        this.level = villager.level();
    }

    public BreakBlockGoal movement(UnaryOperator<MoveToGoal> configurer) {
        this.moveToGoal = configurer.apply(new MoveToGoal(getVillager(), getTargetPos()));
        return this;
    }

    public BreakBlockGoal noMovement() {
        this.allowMovement = false;
        return this;
    }

    public BreakBlockGoal multipleBlocks() {
        this.multipleBlocks = true;
        return this;
    }

    public BreakBlockGoal breakableBarrier(Block... block) {
        this.breakableBarrierBlocks.addAll(Set.of(block));
        return this;
    }

    @SafeVarargs
    public final BreakBlockGoal breakableBarrier(TagKey<Block>... tag) {
        this.breakableBarrierTags.addAll(Set.of(tag));
        return this;
    }

    public BreakBlockGoal breakable(Block... block) {
        this.breakableBlocks.addAll(Set.of(block));
        return this;
    }

    @SafeVarargs
    public final BreakBlockGoal breakable(TagKey<Block>... tag) {
        this.breakableTags.addAll(Set.of(tag));
        return this;
    }


    @Override
    protected void tick() {
        if(this.moveToGoal != null) {

            if(this.moveToGoal.isInProgress()) {
                this.moveToGoal.executeTick();
                return;
            } else if(this.moveToGoal.hasFailed()) {
                fail();
                System.out.println("Failed break because movement failed");
                return;
            }
            this.moveToGoal = null;
        }

        if(this.breakBlock == null) {
            BlockPos next = getBlockPosInSight();
            BlockState nextState = this.level.getBlockState(next);
            if(!isBreakableBlock(next, nextState)) {
                fail();
                System.out.println("Failed break because block not breakable");
                return;
            }
            this.breakPos = next;
            this.breakBlock = nextState.getBlock();
            this.speed = calculateBreakingTicks();
            return;
        }

        getVillager().getNavigation().stop();
        getVillager().getLookControl().setLookAt(getBreakPos().getX() + 0.5D,
                getBreakPos().getY() + 0.5D, getBreakPos().getZ() + 0.5D);

        if(this.ticks < this.speed) {
            this.ticks++;
            int progress = (int) (((float) this.ticks / this.speed) * 10.0F);
            this.level.destroyBlockProgress(getVillager().getId(), getBreakPos(), progress);
        } else {
            BlockState breakState = this.level.getBlockState(getBreakPos());
            if(!isBreakableBlock(getBreakPos(), breakState)) {
                fail();
                System.out.println("Failed break because block not breakable anymore");
                return;
            }
            level.destroyBlock(getBreakPos(), true, getVillager());
            if(!getTargetPos().equals(getBreakPos()) && this.multipleBlocks) {
                this.breakBlock = null;
            } else {
                success();
            }
        }
    }

    @Override
    public void init() {
        if(AbstractVillagerBehavior.blockInTouchRange(getVillager(), getTargetPos())) {
            return;
        }
        if(!this.allowMovement) {
            fail();
        } else if(this.moveToGoal == null) {
            this.moveToGoal = new MoveToGoal(getVillager(), getTargetPos()).begin();
        } else {
            this.moveToGoal.begin();
        }
    }

    private BlockPos getBlockPosInSight() {
        Vec3 from = getVillager().getEyePosition();
        Vec3 to = Vec3.atCenterOf(targetPos);

        ClipContext clipContext = new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, getVillager());
        BlockHitResult hit = level.clip(clipContext);
        BlockPos chopPosition = hit.getBlockPos();
        if (hit.getType() == HitResult.Type.MISS) {
            chopPosition = targetPos;
        }
        return chopPosition;
    }

    private boolean isBreakableBlock(BlockPos blockPos, BlockState blockState) {
        if(!getTargetPos().equals(blockPos) && ! this.breakableBarrierBlocks.contains(blockState.getBlock())
                && blockState.getTags().noneMatch(this.breakableBarrierTags::contains) ) {
            return false;
        } else if(getTargetPos().equals(blockPos) && !(this.breakableBlocks.isEmpty() && this.breakableTags.isEmpty())
                && ! this.breakableBlocks.contains(blockState.getBlock())
                && blockState.getTags().noneMatch(this.breakableTags::contains)) {
            return false;
        }
        ItemStack tool = getVillager().getMainHandItem();
        boolean hasCorrectTool = !blockState.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(blockState);
        if(!hasCorrectTool) return false;
        return AbstractVillagerBehavior.blockInTouchRange(getVillager(), blockPos);
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
