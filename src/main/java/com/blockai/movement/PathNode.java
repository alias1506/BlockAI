package com.blockai.movement;

import net.minecraft.core.BlockPos;

public class PathNode implements Comparable<PathNode> {
    public final BlockPos pos;
    public PathNode parent;
    public double gCost;
    public double hCost;

    public PathNode(BlockPos pos, PathNode parent, double gCost, double hCost) {
        this.pos = pos;
        this.parent = parent;
        this.gCost = gCost;
        this.hCost = hCost;
    }

    public double fCost() {
        return gCost + hCost;
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.fCost(), other.fCost());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PathNode)) return false;
        return pos.equals(((PathNode) obj).pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
