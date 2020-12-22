package es.codeurjc.squirrel.drey.local.simulator.core;

import es.codeurjc.squirrel.drey.local.WorkerStats;
import es.codeurjc.squirrel.drey.local.WorkerStatus;

import java.util.*;
import java.util.stream.Collectors;

public class SimulationState {

    // Workers
    private Map<String, WorkerStats> workers = new HashMap<>();
    // Task queues
    private Queue<InputTask> highPriorityTasks = new LinkedList<>();
    private Queue<InputTask> lowPriorityTasks = new LinkedList<>();

    private Map<String, Integer> mapWorkerIdTimeLaunched = new HashMap<>();
    private Map<String, Integer> mapWorkerIdTimeTerminated = new HashMap<>();

    // Worker Id and running task
    private Map<String, List<InputTask>> runningTasks = new HashMap<>();

    public Map<String, WorkerStats> getWorkers() {
        return workers;
    }

    public Map<String, Integer> getMapWorkerIdTimeLaunched() {
        return mapWorkerIdTimeLaunched;
    }

    public Map<String, Integer> getMapWorkerIdTimeTerminated() {
        return mapWorkerIdTimeTerminated;
    }

    public Map<String, List<InputTask>> getRunningTasks() {
        return runningTasks;
    }

    public Queue<InputTask> getHighPriorityTasks() {
        return highPriorityTasks;
    }

    public Queue<InputTask> getLowPriorityTasks() {
        return lowPriorityTasks;
    }

    public int getNumRunningTasks() {
        int numRunningTasks = 0;
        for(List<InputTask> taskRunningInWorker: runningTasks.values()) {
            numRunningTasks += taskRunningInWorker.size();
        }
        return numRunningTasks;
    }

    public List<WorkerStats> getAllRuningWorkers() {
        return workers.values().stream().filter(w -> w.getStatus() == WorkerStatus.running).collect(Collectors.toList());
    }

    public List<WorkerStats> getAllLaunchingWorkers() {
        return workers.values().stream().filter(w -> w.getStatus() == WorkerStatus.launching).collect(Collectors.toList());
    }

    public List<WorkerStats> getAllTerminatingWorkers() {
        return workers.values().stream().filter(w -> w.getStatus() == WorkerStatus.terminating).collect(Collectors.toList());
    }

    public List<WorkerStats> getAllDisconnectedWorkers() {
        return workers.values().stream().filter(WorkerStats::isDisconnected).collect(Collectors.toList());
    }

    public List<WorkerStats> getAllIdleWorkers() {
        return workers.values().stream().filter(w -> w.getTasksRunning() == 0 && w.getStatus() == WorkerStatus.running).collect(Collectors.toList());
    }
}
