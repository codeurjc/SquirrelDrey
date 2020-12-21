package es.codeurjc.squirrel.drey.local.simulator.core;

import es.codeurjc.squirrel.drey.local.WorkerStats;

import java.util.*;

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
}
