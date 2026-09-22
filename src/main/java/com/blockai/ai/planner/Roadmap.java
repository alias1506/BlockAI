package com.blockai.ai.planner;

import java.util.List;

import com.blockai.execution.TaskState;

public class Roadmap {
    private String goal;
    private String summary;
    private List<RoadmapTask> tasks;

    private TaskState state = TaskState.IDLE;
    private int currentTaskIndex = 0;

    public Roadmap(String goal, String summary, List<RoadmapTask> tasks) {
        this.goal = goal;
        this.summary = summary;
        this.tasks = tasks;
    }

    public String getGoal() { return goal; }
    public String getSummary() { return summary; }
    public List<RoadmapTask> getTasks() { return tasks; }
    
    public TaskState getState() { return state; }
    public void setState(TaskState state) { this.state = state; }
    
    public int getCurrentTaskIndex() { return currentTaskIndex; }
    public void setCurrentTaskIndex(int currentTaskIndex) { this.currentTaskIndex = currentTaskIndex; }
}
