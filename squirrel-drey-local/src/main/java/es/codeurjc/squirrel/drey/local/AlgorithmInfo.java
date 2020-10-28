package es.codeurjc.squirrel.drey.local;

import java.io.Serializable;

public class AlgorithmInfo implements Serializable {

    private static final long serialVersionUID = -6637559519480887352L;

    private String algorithmId;
    private int tasksAdded;
    private int tasksCompleted;
    private int tasksQueued;
    private int tasksTimeout;
    private Long timeOfProcessing;
    private int priority;

    public AlgorithmInfo(String algorithmId, int tasksAdded, int tasksCompleted, int tasksQueued, int tasksTimeout,
            Long timeOfProcessing, int priority) {
        this.algorithmId = algorithmId;
        this.tasksAdded = tasksAdded;
        this.tasksCompleted = tasksCompleted;
        this.tasksQueued = tasksQueued;
        this.tasksTimeout = tasksTimeout;
        this.timeOfProcessing = timeOfProcessing;
        this.priority = priority;
    }

    public int getTasksAdded() {
        return tasksAdded;
    }

    public void setTasksAdded(int tasksAdded) {
        this.tasksAdded = tasksAdded;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }

    public void setTasksCompleted(int tasksCompleted) {
        this.tasksCompleted = tasksCompleted;
    }

    public int getTasksQueued() {
        return tasksQueued;
    }

    public void setTasksQueued(int tasksQueued) {
        this.tasksQueued = tasksQueued;
    }

    public int getTasksTimeout() {
        return tasksTimeout;
    }

    public void setTasksTimeout(int tasksTimeout) {
        this.tasksTimeout = tasksTimeout;
    }

    public Long getTimeOfProcessing() {
        return timeOfProcessing;
    }

    public void setTimeOfProcessing(Long timeOfProcessing) {
        this.timeOfProcessing = timeOfProcessing;
    }

    public String getAlgorithmId() {
        return algorithmId;
    }

    public void setAlgorithmId(String algorithmId) {
        this.algorithmId = algorithmId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "AlgorithmInfo [algorithmId=" + algorithmId + ", priority=" + priority + ", tasksAdded=" + tasksAdded
                + ", tasksCompleted=" + tasksCompleted + ", tasksQueued=" + tasksQueued + ", tasksTimeout="
                + tasksTimeout + ", timeOfProcessing=" + timeOfProcessing + "]";
    }

    
}
