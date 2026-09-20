package com.blockai.ai.pathing;

import net.minecraft.core.BlockPos;

public class Node implements Comparable<Node> {
    public final BlockPos pos;
    public Node parent;
    public double gCost;
    public double hCost;

    public Node(BlockPos pos, Node parent, double gCost, double hCost) {
        this.pos = pos;
        this.parent = parent;
        this.gCost = gCost;
        this.hCost = hCost;
    }

    public double fCost() {
        return gCost + hCost;
    }

    @Override
    public int compareTo(Node other) {
        return Double.compare(this.fCost(), other.fCost());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Node)) return false;
        return pos.equals(((Node) obj).pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
