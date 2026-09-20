package com.blockai.ai;

import java.util.List;

public class Roadmap {
    private String goal;
    private String summary;
    private List<RoadmapTask> tasks;

    private ExecutionState state = ExecutionState.IDLE;
    private int currentTaskIndex = 0;

    public Roadmap(String goal, String summary, List<RoadmapTask> tasks) {
        this.goal = goal;
        this.summary = summary;
        this.tasks = tasks;
    }

    public String getGoal() { return goal; }
    public String getSummary() { return summary; }
    public List<RoadmapTask> getTasks() { return tasks; }
    
    public ExecutionState getState() { return state; }
    public void setState(ExecutionState state) { this.state = state; }
    
    public int getCurrentTaskIndex() { return currentTaskIndex; }
    public void setCurrentTaskIndex(int currentTaskIndex) { this.currentTaskIndex = currentTaskIndex; }
}
