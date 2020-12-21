package es.codeurjc.squirrel.drey.local.simulator.core;

public class InputTask {

    private int executionTime;

    // Only present if isRunning true
    private int endTime;

    public InputTask(int executionTime) {
        this.executionTime = executionTime;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }
}
