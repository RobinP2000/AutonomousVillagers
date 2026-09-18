package de.petrofsky.autonomousvillagers.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ShortPath {

    private static final long DEFAULT_BLOCK_SEARCH_LIMIT = 200;
    private final long blockSearchLimit;
    private final Level level;
    private final BlockPos targetPos;
    private final PriorityQueue<Node> unchecked;
    private final HashSet<Node> checked;
    private final HashMap<BlockPos, Node> nodes;
    private final int withinDistance;
    private final long withinDistanceSquared;
    private boolean isReachable = false;
    private Node bestNode;
    private long bestDistanceSquared = -1;
    private boolean done = false;
    private boolean searchLimitReached = false;
    private final boolean closestPossible;
    private ArrayList<Node> path;
    private final HashSet<Block> breakableBlocks;
    private final HashSet<TagKey<Block>> breakableTags;
    private final HashMap<BlockPos, BlockState> stateCache = new HashMap<>();
    private final HashMap<BlockPos, Boolean> fullBlockCache = new HashMap<>();
    private final HashMap<BlockPos, Boolean> emptyCollisionCache = new HashMap<>();

    public ShortPath(Level level, BlockPos origin, BlockPos targetPos, int withinDistance,
                     HashSet<Block> breakableBlocks, HashSet<TagKey<Block>> breakableTags,
                     long blocksLeft) {
        this(level, origin, targetPos, withinDistance, breakableBlocks, breakableTags,
                blocksLeft, DEFAULT_BLOCK_SEARCH_LIMIT, false);
    }

    public ShortPath(Level level, BlockPos origin, BlockPos targetPos, int withinDistance,
                     HashSet<Block> breakableBlocks, HashSet<TagKey<Block>> breakableTags,
                     long blocksLeft, long blockSearchLimit, boolean closestPossible) {
        this.level = level;
        this.targetPos = targetPos;
        this.breakableBlocks = breakableBlocks;
        this.breakableTags = breakableTags;
        this.unchecked = new PriorityQueue<>(Comparator.comparingLong(node ->
                distanceCost(node.getPos(), targetPos) + node.getTotalMovementCost()
        ));
        this.checked = new HashSet<>();
        this.withinDistance = withinDistance;
        this.withinDistanceSquared = (long) withinDistance * withinDistance;
        this.nodes = new HashMap<>();
        this.unchecked.add(new Node(origin, null, 0, blocksLeft, new HashMap<>()));
        this.blockSearchLimit = blockSearchLimit;
        this.closestPossible = closestPossible;
        calculate();
    }

    protected BlockState getState(BlockPos pos) {
        return stateCache.computeIfAbsent(pos.immutable(), level::getBlockState);
    }

    protected boolean getFullBlock(BlockPos pos) {
        return! hasEmptyCollusion(pos) ;// fullBlockCache.computeIfAbsent(pos.immutable(), newPos ->
                //getState(newPos).isCollisionShapeFullBlock(level, newPos));
    }

    protected boolean hasEmptyCollusion(BlockPos pos) {
        return emptyCollisionCache.computeIfAbsent(pos.immutable(), newPos ->
                getState(newPos).getCollisionShape(level, newPos).isEmpty());
    }

    private void clearCache() {
        this.stateCache.clear();
        this.fullBlockCache.clear();
        this.emptyCollisionCache.clear();
    }

    public boolean isReachable() {
        return this.isReachable;
    }

    public boolean isNextValid(int index) {
        clearCache();
        if (this.path == null || index + 1 >= this.path.size()) return false;

        int nextIndex = index + 1;

        Node currentNode = this.path.get(index);
        Node nextNode = this.path.get(nextIndex);

        Map.Entry<Long, HashMap<BlockPos, NodeActionType>> movementCostEntry =
                movementCost(currentNode, nextNode.getPos());
        if (movementCostEntry.getKey() == -1) {
            return false;
        }
        HashMap<BlockPos, NodeActionType> actions = movementCostEntry.getValue();

        if(actions.size() != nextNode.getActions().size()) return false;
        for(BlockPos blockPos : nextNode.getActions().keySet()) {
            if(!actions.containsKey(blockPos)) return false;
            if(actions.get(blockPos) != nextNode.getActions().get(blockPos)) return false;
        }
        return true;
    }

    public BlockPos getClosestBlockPos() {
        return this.bestNode.getPos();
    }

    public ArrayList<Node> getPath() {
        return path;
    }

    public boolean searchLimitReached() {
        return this.searchLimitReached;
    }

    public int getWithinDistance() {
        return withinDistance;
    }

    private void calculate() {
        while (! unchecked.isEmpty() && checked.size() < blockSearchLimit && !done) {
            Node node = unchecked.poll();
            if(node == null) break;
            checked.add(node);
            BlockPos pos = node.getPos();
            long currentDistanceSquared = BlockGeometry3DUtils.getDistanceSquared(pos, targetPos);
            if (currentDistanceSquared < this.bestDistanceSquared || this.bestDistanceSquared == -1) {
                this.bestDistanceSquared = currentDistanceSquared;
                this.bestNode = node;
            }
            if(currentDistanceSquared <= this.withinDistanceSquared) {
                isReachable = true;
                if (currentDistanceSquared == 0 || !closestPossible) {
                    done = true;
                    break;
                }
            }

            List<BlockPos> neighbors = List.of(pos.north(), pos.east(), pos.south(), pos.west(),
                    pos.below().north(), pos.below().east(), pos.below().south(), pos.below().west(),
                    pos.above().north(), pos.above().east(), pos.above().south(), pos.above().west());
            for (BlockPos neighbor : neighbors) {
                Map.Entry<Long, HashMap<BlockPos, NodeActionType>> movementCostEntry =
                        movementCost(node, neighbor);
                if (movementCostEntry.getKey() == -1) {
                    continue;
                }
                long newMovementCost = movementCostEntry.getKey();
                long newTotalMovementCost = node.getTotalMovementCost() + newMovementCost;
                HashMap<BlockPos, NodeActionType> actions = movementCostEntry.getValue();
                long placed = actions.values().stream()
                        .filter(type -> type == NodeActionType.PLACED).count();

                Node existingNode = this.nodes.get(neighbor);
                if(existingNode == null) {
                    Node neighborNode = new Node(neighbor, node,
                            newMovementCost,node.getBlocksLeft() - placed, actions);
                    this.unchecked.add(neighborNode);
                    nodes.put(neighbor, neighborNode);
                } else if(newTotalMovementCost < existingNode.getTotalMovementCost()) {
                    this.unchecked.remove(existingNode);
                    this.checked.remove(existingNode);
                    existingNode.setParentPos(node);
                    existingNode.setMovementCost(newMovementCost);
                    existingNode.setBlocksLeft(node.getBlocksLeft() - placed);
                    existingNode.setActions(movementCostEntry.getValue());
                    this.unchecked.add(existingNode);
                }
            }
        }

        if(!isReachable && checked.size() == blockSearchLimit) {
            System.out.println("limit");
            this.searchLimitReached = true;
        }

        if(this.bestNode != null) {
            createPath(this.bestNode);
        }
    }

    public int getBlocksChecked() {
        return checked.size();
    }

    public boolean update(int fromIndex, int toIndex) {
        if(this.path == null) return false;
        int lastIndex = this.path.size() - 1;
        if (fromIndex < 0 || toIndex <= fromIndex || toIndex > lastIndex) return false;

        Node startNode = this.path.get(fromIndex);
        Node targetNode = this.path.get(toIndex);

        boolean toEnd = toIndex == this.path.size() - 1;
        int withinDistance = toEnd ? this.withinDistance : 0;

        ShortPath subPathFinder = new ShortPath(level, startNode.getPos(), targetNode.getPos(),
                withinDistance, breakableBlocks, breakableTags, startNode.getBlocksLeft(),
                blockSearchLimit, closestPossible);
        if(!subPathFinder.isReachable()) {
            return false;
        }

        ArrayList<Node> subPath = subPathFinder.getPath();
        subPath.removeFirst();
        subPath.getFirst().setParentPos(startNode);
        Node lastNode = subPath.getLast();

        ArrayList<Node> updatedPath = new ArrayList<>(this.path.subList(0, fromIndex + 1));
        updatedPath.addAll(subPath);

       if(!toEnd) {
           ArrayList<Node> endNodes = new ArrayList<>(this.path.subList(toIndex + 1, lastIndex + 1));
           endNodes.getFirst().setParentPos(lastNode);

            int updateIndex = 0;
            while (updateIndex < endNodes.size()) {
                Node node = endNodes.get(updateIndex);
                node.updateTotalCost();
                node.updateTotalActions();
                updateIndex++;
            }
            updatedPath.addAll(endNodes);
        }
        this.path.clear();
        this.path = updatedPath;
        return true;
    }

    private void createPath(Node end) {
        ArrayList<Node> path = new ArrayList<>();
        Node current = end;
        while (current != null) {
            path.addFirst(current);
            current = current.getParentPos();
        }
        System.out.println("Checked blocks: " + this.checked.size());
        this.path = path;
        this.unchecked.clear();
        this.checked.clear();
        this.nodes.clear();
        clearCache();
    }

    private static long distanceCost(BlockPos origin, BlockPos target) {
        long distanceX = Math.abs(origin.getX() - target.getX());
        long distanceY = Math.abs(origin.getY() - target.getY());
        long distanceZ = Math.abs(origin.getZ() - target.getZ());
        long distanceXZ = distanceX + distanceZ;
        return Math.min(3000, distanceXZ * distanceXZ * 10L) + Math.min(1500, distanceY * distanceY * 10L);
    }

    private Map.Entry<Long, HashMap<BlockPos, NodeActionType>> movementCost(Node originNode, BlockPos target) {
        long blocksLeft = originNode.getBlocksLeft();
        BlockPos aboveTarget = target.above();
        BlockPos belowTarget = target.below();
        if (PathUtils.isHazardClose(this, target)
                || PathUtils.isHazardClose(this, aboveTarget)
                || PathUtils.isHazardClose(this, belowTarget)) {
            return Map.entry(-1L, new HashMap<>());
        }

        /*
        if (PathUtils.isWater(this, target) && PathUtils.isWater(this, aboveTarget)) {
           return Map.entry(20L, new HashMap<>());
        } else if(PathUtils.isWater(this, target) && getState(aboveTarget).isAir()) {
            return Map.entry(10L, new HashMap<>());
        }*/

        HashMap<BlockPos, NodeActionType> checks = new HashMap<>(Map.of(
                target.below(), NodeActionType.PLACED,
                target, NodeActionType.BROKEN,
                aboveTarget, NodeActionType.BROKEN));


        HashMap<BlockPos, NodeActionType> actions = new HashMap<>();

        long cost = 5L;

        cost += PathUtils.countSolidNeighbor(this, originNode, target) * 8L;
        cost += PathUtils.countSolidNeighbor(this, originNode, aboveTarget) * 8L;
        cost += PathUtils.countCollusionFreeNeighbor(this, originNode, target.below()) * 8L;
        cost += PathUtils.countCollusionFreeNeighbor(this, originNode, target.below().below()) * 8L;

        BlockPos origin = originNode.getPos();
        if(origin.getY() > target.getY()) {
            cost += 10L;
            checks.put(aboveTarget.above(), NodeActionType.BROKEN);
        } else if(origin.getY() < target.getY()) {
            checks.put(origin.above().above(), NodeActionType.BROKEN);
            cost += 20L;
        }

        for(BlockPos blockPos : checks.keySet()) {
            NodeActionType type = checks.get(blockPos);

            if (type == NodeActionType.BROKEN
                    && originNode.getActionType(blockPos) != NodeActionType.PLACED
                    && PathUtils.isOperableDoor(this, blockPos)) {
                if (PathUtils.isOpen(this, originNode, blockPos)
                        || !PathUtils.isLowerHalf(this, blockPos)) {
                    actions.put(blockPos, NodeActionType.NONE);
                } else {
                    actions.put(blockPos, NodeActionType.OPENED);
                    cost += 5L;
                }
                continue;
            }

            if(type == NodeActionType.BROKEN && PathUtils.requiresBreak(this, originNode, blockPos)) {
                if(PathUtils.isBreakable(this, originNode, blockPos, breakableBlocks, breakableTags)) {
                    actions.put(blockPos, NodeActionType.BROKEN);
                    cost += 190;
                } else {
                    return Map.entry(-1L, actions);
                }
            } else if(type == NodeActionType.PLACED && PathUtils.requiresPlacement(this, originNode, blockPos)) {
                if(PathUtils.isPlaceable(this, originNode, blockPos, blocksLeft)) {
                    actions.put(blockPos, NodeActionType.PLACED);
                    blocksLeft--;
                    cost += 200L;
                } else {
                    return Map.entry(-1L, actions);
                }
            } else {
                actions.put(blockPos, NodeActionType.NONE);
            }

        }
        return Map.entry(cost, actions);
    }
}
