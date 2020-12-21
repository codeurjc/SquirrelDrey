package es.codeurjc.squirrel.drey.local.simulator;

import java.util.ArrayList;
import java.util.List;

public class SimulationResult {

    private List<String> labelsList = new ArrayList<>();
    private List<Integer> highPriorityTasks = new ArrayList<>();
    private List<Integer> lowPriorityTasks = new ArrayList<>();
    private List<Integer> totalTasks = new ArrayList<>();
    private List<Integer> runningWorkers = new ArrayList<>();
    private List<Integer> launchingWorkers = new ArrayList<>();
    private List<Integer> terminatingWorkers = new ArrayList<>();
    private List<Integer> disconnectedWorkers = new ArrayList<>();
    private List<Integer> idleWorkers = new ArrayList<>();
    private List<Integer> minWorkersList = new ArrayList<>();
    private List<Integer> maxWorkersList = new ArrayList<>();
    private List<Integer> minIdleWorkers = new ArrayList<>();
    private List<Integer> runningTasks = new ArrayList<>();

    public List<String> getLabelsList() {
        return labelsList;
    }

    public List<Integer> getHighPriorityTasks() {
        return highPriorityTasks;
    }

    public List<Integer> getLowPriorityTasks() {
        return lowPriorityTasks;
    }

    public List<Integer> getTotalTasks() {
        return totalTasks;
    }

    public List<Integer> getRunningWorkers() {
        return runningWorkers;
    }

    public List<Integer> getLaunchingWorkers() {
        return launchingWorkers;
    }

    public List<Integer> getTerminatingWorkers() {
        return terminatingWorkers;
    }

    public List<Integer> getDisconnectedWorkers() {
        return disconnectedWorkers;
    }

    public List<Integer> getIdleWorkers() {
        return idleWorkers;
    }

    public List<Integer> getMinWorkersList() {
        return minWorkersList;
    }

    public List<Integer> getMaxWorkersList() {
        return maxWorkersList;
    }

    public List<Integer> getMinIdleWorkers() {
        return minIdleWorkers;
    }

    public List<Integer> getRunningTasks() {
        return this.getRunningTasks();
    }

    protected List<?> getResultByTemplateKeys(ChartGenerator.TemplateKeys templateKey) {
        switch (templateKey) {
            case LABELS_LIST: return this.labelsList;
            case HIGH_PRIORITY_TASK: return this.highPriorityTasks;
            case LOW_PRIORITY_TASKS: return this.lowPriorityTasks;
            case TOTAL_TASKS: return this.totalTasks;
            case RUNNING_WORKERS: return this.runningWorkers;
            case LAUNCHING_WORKERS: return this.launchingWorkers;
            case TERMINATING_WORKERS: return this.terminatingWorkers;
            case DISCONNECTED_WORKERS: return this.disconnectedWorkers;
            case IDLE_WORKERS: return this.idleWorkers;
            case MIN_WORKERS_LIST: return this.minWorkersList;
            case MAX_WORKERS_LIST: return this.maxWorkersList;
            case MIN_IDDLE_WORKERS: return this.minIdleWorkers;
            case RUNNING_TASKS: return this.runningTasks;
            default: throw new IllegalArgumentException("Template key is not recognised");
        }
    }
}
