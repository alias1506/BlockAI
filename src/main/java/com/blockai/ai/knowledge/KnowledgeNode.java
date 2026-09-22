package com.blockai.ai.knowledge;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeNode {
    private String id;
    private String type; // "complex", "item", "capability", "interaction"
    private List<String> requires = new ArrayList<>();
    private String action; // optional mapping to controller actions like "GATHER", "CRAFT", "COMBAT"
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public List<String> getRequires() { return requires; }
    public void setRequires(List<String> requires) { this.requires = requires; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Override
    public String toString() {
        return "KnowledgeNode{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", requires=" + requires +
                ", action='" + action + '\'' +
                '}';
    }
}
