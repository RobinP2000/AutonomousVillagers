package de.petrofsky.autonomousvillagers.blocks;

import de.petrofsky.autonomousvillagers.villagers.forester.ChopTreeBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import de.petrofsky.autonomousvillagers.AutonomousVillagers;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Represents the {@link BlockEntity} for the Forester's workstation block.
 * <p>
 * This entity manages state persistence for tree-chopping operations, including
 * remaining target logs, scaffolding placement/cleanup steps, and current execution phases.
 * All mutations automatically invoke {@link #setChanged()} to guarantee dirty chunk marking
 * and prevent data loss across server saves.
 */
public class ForesterBlockEntity extends BlockEntity {

    /** Deferred register for mod block entity types. */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AutonomousVillagers.MODID);

    /**
     * Deferred holder pointing to this specific Forester BlockEntity type.
     * <p>
     * <b>Note on {@code @SuppressWarnings("DataFlowIssue")}:</b><br>
     * Passing {@code null} to {@link BlockEntityType.Builder#build(com.mojang.datafixers.types.Type)}
     * triggers an IDE warning because Mojang's official mappings mark the argument as {@code @NotNull}.
     * However, passing {@code null} is runtime-safe and standard practice for custom mods, as modded
     * block entities do not register data schemas with Mojang's internal DataFixerUpper (DFU) system.
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForesterBlockEntity>> TYPE =
            BLOCK_ENTITIES.register("forester_block_entity",
                    () -> BlockEntityType.Builder
                            .of(ForesterBlockEntity::new, ForesterBlock.WOOD_CHOP_BLOCK.get())
                            .build(null));


    /** Contains the positions of a tree's log blocks that remain to be destroyed. */
    private final List<BlockPos> pendingLogs    = new ArrayList<>();

    /** Contains the positions of dirt blocks that have been destroyed to build a scaffold. */
    private final List<BlockPos> scaffoldBroken = new ArrayList<>();

    /** Contains the positions of dirt blocks that have been placed to build a scaffold. */
    private final List<Map.Entry<BlockPos, Boolean>> scaffoldPlaced = new ArrayList<>();

    /** Scaffold collection targets are blocks that have been marked for destruction in order to build a scaffold. */
    private final List<BlockPos> scaffoldCollectionTargets = new ArrayList<>();

    /** The current task while chopping a tree. */
    private ChopTreeBehavior.Phase phase = ChopTreeBehavior.Phase.CHOP;

    /** The next log block to destroy from a tree.  */
    private BlockPos nextBlockTarget;

    /**
     * Constructs a new ForesterBlockEntity instance.
     *
     * @param pos   The world position of this block entity.
     * @param state The current state of the associated block.
     */
    public ForesterBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE.get(), pos, state);
    }

    /**
     * Serializes block entity custom fields into the provided {@link CompoundTag} for world saves.
     * <p>
     * {@link BlockPos} collections are optimized as 64-bit {@code long[]} arrays to reduce NBT payload size.
     *
     * @param tag        The NBT tag compound to write data into.
     * @param registries Provider for registry-based serialization lookups.
     */
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        if (this.phase != null) {
            tag.putString("Phase", this.phase.name());
        }

        if (this.nextBlockTarget != null) {
            tag.putLong("NextBlockTarget", this.nextBlockTarget.asLong());
        }

        tag.putLongArray("PendingLogs", this.pendingLogs.stream()
                .mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("ScaffoldBroken", this.scaffoldBroken.stream()
                .mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("ScaffoldCollectionTargets", this.scaffoldCollectionTargets.stream()
                .mapToLong(BlockPos::asLong).toArray());

        ListTag scaffoldPlacedTag = new ListTag();
        for (Map.Entry<BlockPos, Boolean> entry : this.scaffoldPlaced) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("Pos", entry.getKey().asLong());
            entryTag.putBoolean("State", entry.getValue());
            scaffoldPlacedTag.add(entryTag);
        }
        tag.put("ScaffoldPlaced", scaffoldPlacedTag);

    }

    /**
     * Deserializes custom block entity fields from the given {@link CompoundTag} when loading world chunks.
     *
     * @param tag        The NBT tag compound containing saved data.
     * @param registries Provider for registry-based serialization lookups.
     */
    @Override
    public void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("Phase")) {
            try {
                this.phase = ChopTreeBehavior.Phase.valueOf(tag.getString("Phase"));
            } catch (IllegalArgumentException e) {
                this.phase = ChopTreeBehavior.Phase.CHOP;
            }
        }

        if (tag.contains("NextBlockTarget")) {
            this.nextBlockTarget = BlockPos.of(tag.getLong("NextBlockTarget"));
        } else {
            this.nextBlockTarget = null;
        }

        this.pendingLogs.clear();
        for (long l : tag.getLongArray("PendingLogs")) {
            this.pendingLogs.add(BlockPos.of(l));
        }

        this.scaffoldBroken.clear();
        for (long l : tag.getLongArray("ScaffoldBroken")) {
            this.scaffoldBroken.add(BlockPos.of(l));
        }

        this.scaffoldCollectionTargets.clear();
        for (long l : tag.getLongArray("ScaffoldCollectionTargets")) {
            this.scaffoldCollectionTargets.add(BlockPos.of(l));
        }

        this.scaffoldPlaced.clear();
        if (tag.contains("ScaffoldPlaced", Tag.TAG_LIST)) {
            ListTag scaffoldPlacedTag = tag.getList("ScaffoldPlaced", Tag.TAG_COMPOUND);
            for (int i = 0; i < scaffoldPlacedTag.size(); i++) {
                CompoundTag entryTag = scaffoldPlacedTag.getCompound(i);

                this.scaffoldPlaced.add(new AbstractMap.SimpleEntry<>(
                        BlockPos.of(entryTag.getLong("Pos")),
                        entryTag.getBoolean("State")
                ));
            }
        }
    }

    /**
     * Registers the {@link BlockEntityType} deferred register to the mod event bus.
     *
     * @param eventBus The primary mod event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }


    /**
     * Removes a target log position from the pending queue if present and marks chunk dirty.
     *
     * @param blockPos The position to remove.
     */
    public void removePendingLog(BlockPos blockPos) {
        if(hasPendingLog(blockPos)) {
            this.pendingLogs.remove(blockPos);
            setChanged();
        }
    }

    /**
     * Adds a collection of target log positions to the pending queue without duplicates.
     * Triggers {@link #setChanged()} only if new entries were added.
     *
     * @param collection The collection of positions to merge into the queue.
     */
    public void addAllPendingLog(Collection<? extends BlockPos> collection) {
        boolean changed = false;
        for(BlockPos blockPos : collection) {
            if(!this.pendingLogs.contains(blockPos)) {
                this.pendingLogs.add(blockPos);
                changed = true;
            }
        }
        if(changed) setChanged();
    }

    /**
     * Retrieves the first log block coordinate queued in the execution list.
     *
     * @return The first {@link BlockPos} in the pending queue.
     */
    public BlockPos getFirstPendingLog() {
        return this.pendingLogs.getFirst();
    }

    /**
     * Checks if a specific log position exists in the pending queue.
     *
     * @param blockPos The target coordinate to check.
     * @return {@code true} if the position is present in the list; {@code false} otherwise.
     */
    public boolean hasPendingLog(BlockPos blockPos) {
        return this.pendingLogs.contains(blockPos);
    }

    /**
     * Checks whether there are any remaining log blocks queued to be chopped.
     *
     * @return {@code true} if the pending log queue is not empty; {@code false} otherwise.
     */
    public boolean hasPendingLogs() {
        return ! this.pendingLogs.isEmpty();
    }


    /**
     * Removes a position where a block was broken for scaffolding after the block is replaced.
     *
     * @param blockPos The position to remove.
     */
    public void removeScaffoldBroken(BlockPos blockPos) {
        if(hasScaffoldBroken(blockPos)) {
            this.scaffoldBroken.remove(blockPos);
            setChanged();
        }
    }

    /**
     * Registers a position where a block was broken for scaffolding.
     *
     * @param blockPos The block coordinate to record.
     */
    public void addScaffoldBroken(BlockPos blockPos) {
        if(!hasScaffoldBroken(blockPos)) {
            this.scaffoldBroken.add(blockPos);
            setChanged();
        }
    }

    /**
     * Retrieves the most recently block position where a dirt block
     * has been destroyed to build a scaffold.
     *
     * @return The last {@link BlockPos} added to the list of broken dirt block positions.
     */
    public BlockPos getLastScaffoldBroken() {
        return this.scaffoldBroken.getLast();
    }

    /**
     * Checks if a specific {@link BlockPos} is marked for replacing a dirt
     * block that is used to build a scaffold.
     *
     * @param blockPos The position to check.
     * @return {@code true} if the position is recorded; {@code false} otherwise.
     */
    public boolean hasScaffoldBroken(BlockPos blockPos) {
        return this.scaffoldBroken.contains(blockPos);
    }

    /**
     * Checks whether any broken scaffolding block coordinates are currently tracked.
     *
     * @return {@code true} if the broken scaffolding list is not empty; {@code false} otherwise.
     */
    public boolean hasScaffoldsBroken() {
        return ! this.scaffoldBroken.isEmpty();
    }

    /**
     * Removes a target from the scaffold collection list if present and marks chunk dirty.
     *
     * @param blockPos The position to remove.
     */
    public void removeScaffoldCollectionTarget(BlockPos blockPos) {
        if(hasScaffoldCollectionTarget(blockPos)) {
            this.scaffoldCollectionTargets.remove(blockPos);
            setChanged();
        }
    }

    /**
     * Clears all currently tracked scaffold collection targets and marks chunk dirty.
     */
    public void clearScaffoldCollectionTargets() {
        if(hasScaffoldCollectionTargets()) {
            this.scaffoldCollectionTargets.clear();
            setChanged();
        }
    }

    /**
     * Adds a collection of positions to the scaffold collection targets without duplicates.
     * Triggers {@link #setChanged()} only if new entries were added.
     *
     * @param collection The collection of positions to merge into the targets.
     */
    public void addAllScaffoldCollectionTargets(Collection<? extends BlockPos> collection) {
        boolean changed = false;
        for(BlockPos blockPos : collection) {
            if(!this.scaffoldCollectionTargets.contains(blockPos)) {
                this.scaffoldCollectionTargets.add(blockPos);
                changed = true;
            }
        }
        if(changed) setChanged();
    }

    /**
     * Retrieves the first tracked scaffold collection target.
     *
     * @return The first {@link BlockPos} in the scaffold collection targets.
     */
    public BlockPos getFirstScaffoldCollectionTarget() {
        return this.scaffoldCollectionTargets.getFirst();
    }

    /**
     * Checks if a specific position exists in the scaffold collection targets.
     *
     * @param blockPos The position to check.
     * @return {@code true} if the position is present; {@code false} otherwise.
     */
    public boolean hasScaffoldCollectionTarget(BlockPos blockPos) {
        return this.scaffoldCollectionTargets.contains(blockPos);
    }

    /**
     * Checks whether there are any tracked scaffold collection targets.
     *
     * @return {@code true} if the scaffold collection list is not empty; {@code false} otherwise.
     */
    public boolean hasScaffoldCollectionTargets() {
        return ! this.scaffoldCollectionTargets.isEmpty();
    }

    /**
     * Removes the most recently placed scaffold entry and marks chunk dirty.
     */
    public void removeLastScaffoldPlaced() {
        if(hasScaffoldsPlaced()) {
            this.scaffoldPlaced.removeLast();
            setChanged();
        }
    }

    /**
     * Removes a specific placed scaffold block position from the list and marks chunk dirty.
     *
     * @param blockPos The position to remove.
     */
    public void removeScaffoldPlaced(BlockPos blockPos) {
        if(hasScaffoldPlaced(blockPos)) {
            this.scaffoldPlaced.removeIf(entry -> entry.getKey().equals(blockPos));
            setChanged();
        }
    }

    /**
     * Registers a position where a block has been registered as placement
     * for scaffolding and marks chunk dirty.
     *
     * @param blockPos  The position of the placed scaffold block.
     * @param notPlaced The boolean state if the block is placed by the villager.
     */
    public void addScaffoldPlaced(BlockPos blockPos, boolean notPlaced) {
        if(!hasScaffoldPlaced(blockPos)) {
            this.scaffoldPlaced.add(Map.entry(blockPos, notPlaced));
            setChanged();
        }
    }

    /**
     * Retrieves the most recently placed scaffold entry.
     *
     * @return The last map entry representing a placed scaffold block and its state,
     * indicating whether it was placed by the villager.
     */
    public Map.Entry<BlockPos, Boolean> getLastScaffoldPlaced() {
        return this.scaffoldPlaced.getLast();
    }

    /**
     * Retrieves the first placed scaffold entry in the list.
     *
     * @return The first map entry representing a placed scaffold block and its state,
     * indicating whether it was placed by the villager.
     */
    public Map.Entry<BlockPos, Boolean> getFirstScaffoldPlaced() {
        return this.scaffoldPlaced.getFirst();
    }

    /**
     * Retrieves the placed scaffold entry at the specified index.
     *
     * @param index The index of the element to retrieve.
     * @return The map entry at the specified index.
     */
    public Map.Entry<BlockPos, Boolean> getScaffoldPlaced(int index) {
        return this.scaffoldPlaced.get(index);
    }

    /**
     * Retrieves the total number of currently tracked placed scaffold blocks.
     *
     * @return The size of the placed scaffold list.
     */
    public int countScaffoldPlaced() {
        return this.scaffoldPlaced.size();
    }

    /**
     * Checks if a specific position exists in the placed scaffold tracking list.
     *
     * @param blockPos The position to check.
     * @return {@code true} if the position is tracked as placed; {@code false} otherwise.
     */
    public boolean hasScaffoldPlaced(BlockPos blockPos) {
        return this.scaffoldPlaced.stream().anyMatch(entry ->
                entry.getKey().equals(blockPos));
    }

    /**
     * Checks whether there are any tracked placed scaffold blocks.
     *
     * @return {@code true} if the placed scaffold list is not empty; {@code false} otherwise.
     */
    public boolean hasScaffoldsPlaced() {
        return !this.scaffoldPlaced.isEmpty();
    }

    /**
     * Retrieves the next log block coordinate targeted for destruction.
     *
     * @return The targeted {@link BlockPos}, or {@code null} if no target is set.
     */
    public BlockPos getNextBlockTarget() {
        return nextBlockTarget;
    }


    /**
     * Retrieves the current execution phase of the tree-chopping operation.
     *
     * @return The current {@link ChopTreeBehavior.Phase}.
     */
    public ChopTreeBehavior.Phase getPhase() {
        return phase;
    }

    /**
     * Updates the current log block target coordinate and marks chunk dirty.
     *
     * @param nextBlockTarget The new target coordinate.
     */
    public void setNextBlockTarget(BlockPos nextBlockTarget) {
        this.nextBlockTarget = nextBlockTarget;
        setChanged();
    }

    /**
     * Updates the active tree-chopping phase state and marks chunk dirty.
     *
     * @param phase The new execution phase.
     */
    public void setPhase(ChopTreeBehavior.Phase phase) {
        this.phase = phase;
        setChanged();
    }

    /**
     * Evaluates if there is an active tree-chopping operation by checking all tracking queues.
     *
     * @return {@code true} if pending logs, broken scaffolds, placed scaffolds, or collection targets exist.
     */
    public boolean hasTreeTarget() {
        return hasPendingLogs() || hasScaffoldsBroken() || hasScaffoldsPlaced() || hasScaffoldCollectionTargets();
    }

}