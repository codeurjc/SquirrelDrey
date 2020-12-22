package es.codeurjc.squirrel.drey.local.simulator.config;

import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingConfig;

import java.util.List;

public class ScenaryConfig {

    private long seed;
    private int secondsByPeriod;
    private int durationInPeriods;
    private int minTimeForWorkersToBeRunning;
    private int maxTimeForWorkersToBeRunning;
    private int minTimeForWorkersToTerminate;
    private int maxTimeForWorkersToTerminate;
    private int initialRunningWorkers;
    private int workerCores;
    private AutoscalingConfig autoscalingConfig;
    private List<InputTaskConfig> inputTasks;

    public long getSeed() {
        return seed;
    }

    public int getSecondsByPeriod() {
        return secondsByPeriod;
    }

    public int getDurationInPeriods() {
        return durationInPeriods;
    }

    public int getMinTimeForWorkersToBeRunning() {
        return minTimeForWorkersToBeRunning;
    }

    public int getMaxTimeForWorkersToBeRunning() {
        return maxTimeForWorkersToBeRunning;
    }

    public int getMinTimeForWorkersToTerminate() {
        return minTimeForWorkersToTerminate;
    }

    public int getMaxTimeForWorkersToTerminate() {
        return maxTimeForWorkersToTerminate;
    }

    public int getInitialRunningWorkers() {
        return initialRunningWorkers;
    }

    public AutoscalingConfig getAutoscalingConfig() {
        return autoscalingConfig;
    }

    public List<InputTaskConfig> getInputTasks() {
        return inputTasks;
    }

    public int getWorkerCores() {
        return workerCores;
    }
}
