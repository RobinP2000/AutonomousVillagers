package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class Node  {
    private final BlockPos pos;
    private Node parentPos;
    private long movementCost;
    private long totalMovementCost;
    private long blocksLeft;
    private HashMap<BlockPos, NodeActionType> actions;
    private HashMap<BlockPos, NodeActionType> totalActions;

    public Node(@NotNull BlockPos pos, Node parentPos, long movementCost,
                long blocksLeft,  HashMap<BlockPos, NodeActionType> actions) {
        this.pos = pos;
        this.parentPos = parentPos;
        this.movementCost = movementCost;
        this.totalMovementCost = getParentPos() != null ? getParentPos().getTotalMovementCost()
                + getMovementCost() : getMovementCost();
        this.blocksLeft = blocksLeft;
        this.actions = actions;
        HashMap<BlockPos, NodeActionType> totalActions = new HashMap<>(this.actions);
        if(getParentPos() != null) totalActions.putAll( getParentPos().getTotalActions());
        this.totalActions = totalActions;
    }

    public HashMap<BlockPos, NodeActionType> getTotalActions() {
        return totalActions;
    }

    public NodeActionType getActionType(BlockPos blockPos) {
        return getTotalActions().get(blockPos);
    }

    public HashMap<BlockPos, NodeActionType> getActions() {
        return actions;
    }

    protected void setParentPos(Node parentPos) {
        this.parentPos = parentPos;
    }

    public void setBlocksLeft(long blocksLeft) {
        this.blocksLeft = blocksLeft;
    }

    public void setActions(HashMap<BlockPos, NodeActionType> actions) {
        this.actions = actions;
    }

    protected long getTotalMovementCost() {
        return totalMovementCost;
    }

    protected long getMovementCost() {
        return movementCost;
    }

    protected void setMovementCost(long movementCost) {
        this.movementCost = movementCost;
    }

    protected void updateTotalCost() {
        this.totalMovementCost = getParentPos() != null ? getParentPos().getTotalMovementCost()
                + getMovementCost() : getMovementCost();
    }

    protected void updateTotalActions() {
        totalActions.clear();
        HashMap<BlockPos, NodeActionType> totalActions = new HashMap<>(this.actions);
        if(getParentPos() != null) totalActions.putAll( getParentPos().getTotalActions());
        this.totalActions = totalActions;
    }

    public BlockPos getPos() {
        return pos;
    }

    protected boolean hasBlocksLeft() {
        return getBlocksLeft() > 0;
    }

    protected long getBlocksLeft() {
        return blocksLeft;
    }

    public Node getParentPos() {
        return parentPos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return pos.equals(node.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
