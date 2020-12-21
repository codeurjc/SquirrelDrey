package es.codeurjc.squirrel.drey.local.simulator.config;

public class InputTaskConfig {

    private int fromPeriod;
    private int toPeriod;
    private int numHighPriorityTasksByPeriod;
    private int numLowPriorityTasksByPeriod;
    private int minTimeForHighPriorityTasksToComplete;
    private int maxTimeForHighPriorityTasksToComplete;
    private int minTimeForLowPriorityTasksToComplete;
    private int maxTimeForLowPriorityTasksToComplete;

    public int getFromPeriod() {
        return fromPeriod;
    }

    public int getToPeriod() {
        return toPeriod;
    }

    public int getNumHighPriorityTasksByPeriod() {
        return numHighPriorityTasksByPeriod;
    }

    public int getNumLowPriorityTasksByPeriod() {
        return numLowPriorityTasksByPeriod;
    }

    public int getMinTimeForHighPriorityTasksToComplete() {
        return minTimeForHighPriorityTasksToComplete;
    }

    public int getMaxTimeForHighPriorityTasksToComplete() {
        return maxTimeForHighPriorityTasksToComplete;
    }

    public int getMinTimeForLowPriorityTasksToComplete() {
        return minTimeForLowPriorityTasksToComplete;
    }

    public int getMaxTimeForLowPriorityTasksToComplete() {
        return maxTimeForLowPriorityTasksToComplete;
    }
}
