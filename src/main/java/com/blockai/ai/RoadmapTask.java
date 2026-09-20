package com.blockai.ai;

public class RoadmapTask {
    private int id;
    private String type;
    private String description;
    private TaskStatus status;

    // Task metadata for controllers
    private int radius = 8;
    private boolean allowsExcavation = false;
    private boolean protectGround = true;

    // Resource gathering metadata
    private String resourceCategory; // e.g. "WOOD_LOG", "STONE", "IRON_ORE"
    private String specificItem;     // e.g. "minecraft:oak_log" or null for generic
    private int quantity = 1;        // how many to gather

    public RoadmapTask(int id, String type, String description) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.status = TaskStatus.PENDING;
    }

    public int getId() { return id; }
    public String getType() { return type != null ? type : "unknown"; }
    public String getDescription() { return description != null ? description : ""; }
    public TaskStatus getStatus() { return status == null ? TaskStatus.PENDING : status; }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getRadius() { return radius > 0 ? radius : 8; }
    public boolean isAllowsExcavation() { return allowsExcavation; }
    public boolean isProtectGround() { return protectGround; }

    public String getResourceCategory() { return resourceCategory; }
    public String getSpecificItem() { return specificItem; }
    public int getQuantity() { return quantity > 0 ? quantity : 1; }

    public void setResource(String category, String item, int qty) {
        this.resourceCategory = category;
        this.specificItem = item;
        this.quantity = qty;
    }
}
