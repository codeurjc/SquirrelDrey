package es.codeurjc.squirrel.drey.local.simulator.core;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import es.codeurjc.squirrel.drey.local.Worker;
import es.codeurjc.squirrel.drey.local.WorkerStats;
import es.codeurjc.squirrel.drey.local.WorkerStatus;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingConfig;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingManager;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingResult;
import es.codeurjc.squirrel.drey.local.autoscaling.SystemStatus;
import es.codeurjc.squirrel.drey.local.simulator.config.InputTaskConfig;
import es.codeurjc.squirrel.drey.local.simulator.config.ScenaryConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Simulation {

    Path resourceDirectory = Paths.get("src","test","resources");

    private long seed;
    private int secondsByPeriod;
    private int durationInPeriods;
    private int minTimeForWorkersToBeRunning;
    private int maxTimeForWorkersToBeRunning;
    private int minTimeForWorkersToTerminate;
    private int maxTimeForWorkersToTerminate;

    private AutoscalingConfig autoscalingConfig;
    private AutoscalingManager autoscalingManager;

    private List<InputTask> allRunningTasks = new ArrayList<>();

    // Simulation state
    private SimulationState simulationState = new SimulationState();

    private Random random;

    private String DEFAULT_RESOURCES_CONFIG_FILE = "simulation_config.json";

    public Simulation() throws FileNotFoundException {
        this(null);
    }

    public Simulation(String configFile) throws FileNotFoundException {
        if (configFile == null) {
            String absolutePathToDefaultConfigFile = resourceDirectory.toAbsolutePath() + "/" + DEFAULT_RESOURCES_CONFIG_FILE;
            configFile = absolutePathToDefaultConfigFile;
        }
        File file = new File(configFile);
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader(file));
        ScenaryConfig scenaryConfig = gson.fromJson(reader, ScenaryConfig.class);
        this.seed = scenaryConfig.getSeed();
        this.secondsByPeriod = scenaryConfig.getSecondsByPeriod();
        this.durationInPeriods = scenaryConfig.getDurationInPeriods();
        this.minTimeForWorkersToBeRunning = scenaryConfig.getMinTimeForWorkersToBeRunning();
        this.maxTimeForWorkersToBeRunning = scenaryConfig.getMaxTimeForWorkersToBeRunning();
        this.minTimeForWorkersToTerminate = scenaryConfig.getMinTimeForWorkersToTerminate();
        this.maxTimeForWorkersToTerminate = scenaryConfig.getMaxTimeForWorkersToTerminate();
        this.random = new Random(this.seed);
        this.autoscalingConfig = scenaryConfig.getAutoscalingConfig();
        this.autoscalingManager = new AutoscalingManager();
        this.generateInitialInputTasks(scenaryConfig);
        this.generateInitialState(scenaryConfig);
    }

    private void generateInitialInputTasks(ScenaryConfig scenaryConfig) {
        for(InputTaskConfig inputTaskConfig: scenaryConfig.getInputTasks()) {
            int fromPeriod = inputTaskConfig.getFromPeriod();
            int toPeriod = inputTaskConfig.getToPeriod();
            for(int period = fromPeriod; period <= toPeriod; period++) {
                // High priority tasks
                int minExecutionTimeHP = inputTaskConfig.getMinTimeForHighPriorityTasksToComplete();
                int maxExecutionTimeHP = inputTaskConfig.getMaxTimeForHighPriorityTasksToComplete();
                for(int currentTask = 0; currentTask < inputTaskConfig.getNumHighPriorityTasksByPeriod(); currentTask++) {
                    int executionTime = random.nextInt(maxExecutionTimeHP - minExecutionTimeHP + 1) + minExecutionTimeHP;
                    simulationState.getHighPriorityTasks().add(new InputTask(executionTime));
                }
                // Low priority tasks
                int minExecutionTimeLP = inputTaskConfig.getMinTimeForLowPriorityTasksToComplete();
                int maxExecutionTimeLP = inputTaskConfig.getMaxTimeForLowPriorityTasksToComplete();
                for(int currentTask = 0; currentTask < inputTaskConfig.getNumLowPriorityTasksByPeriod(); currentTask++) {
                    int executionTime = random.nextInt(maxExecutionTimeLP - minExecutionTimeLP + 1) + minExecutionTimeLP;
                    simulationState.getLowPriorityTasks().add(new InputTask(executionTime));
                }
            }
        }
    }

    private void generateInitialState(ScenaryConfig scenaryConfig) {
        int numWorkersRunning = scenaryConfig.getInitialRunningWorkers();
        for(int i = 0; i < numWorkersRunning; i++) {
            String workerId = generateRandomWorkerId();
            this.simulationState.getWorkers().put(workerId, new WorkerStats(1, workerId, 0, workerId, null, 4, 0,
                    0, 0, 0, WorkerStatus.running));
        }
    }

    private String generateRandomWorkerId() {
        byte[] array = new byte[20];
        random.nextBytes(array);
        return new String(array, Charset.forName("UTF-8"));
    }

    private void launchWorker(int currentPeriod) {
        String workerId = generateRandomWorkerId();
        int timeToBeRunning = random.nextInt(maxTimeForWorkersToBeRunning - minTimeForWorkersToBeRunning + 1) + minTimeForWorkersToBeRunning;
        WorkerStats workerStats = new WorkerStats(currentPeriod, workerId, 1, workerId, null, 4, 0,
                0, 0, 0, WorkerStatus.launching);
        simulationState.getWorkers().put(workerId, workerStats);
        simulationState.getMapWorkerIdTimeLaunched().put(workerId, currentPeriod + timeToBeRunning);
    }

    private void terminateWorkers(int currentPeriod, List<WorkerStats> workersToTerminate) {
        for(WorkerStats workerStats: workersToTerminate) {
            String workerId = workerStats.getWorkerId();
            int timeToTerminate = random.nextInt(maxTimeForWorkersToTerminate - minTimeForWorkersToTerminate + 1) + minTimeForWorkersToTerminate;
            simulationState.getWorkers().get(workerId).setStatus(WorkerStatus.terminating);
            simulationState.getMapWorkerIdTimeTerminated().put(workerId, currentPeriod + timeToTerminate);
        }
    }

    private void checkLaunchingWorkersRunning(int currentPeriod) {
        List<WorkerStats> launchingWorkers = simulationState.getWorkers().values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.launching)
                .collect(Collectors.toList());

        for(WorkerStats workerStats: launchingWorkers) {
            String workerId = workerStats.getWorkerId();
            int timeMustBeRunning = simulationState.getMapWorkerIdTimeLaunched().get(workerId);
            int currentTime = currentPeriod * secondsByPeriod;
            if (currentTime >= timeMustBeRunning) {
                workerStats.setStatus(WorkerStatus.running);
                simulationState.getMapWorkerIdTimeLaunched().remove(workerId);
            }
        }
    }

    private void checkTerminatingWorkers(int currentPeriod) {
        List<WorkerStats> terminatingWorkers = simulationState.getWorkers().values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.terminating)
                .collect(Collectors.toList());

        for(WorkerStats workerStats: terminatingWorkers) {
            String workerId = workerStats.getWorkerId();
            int timeMustBeTerminated = simulationState.getMapWorkerIdTimeTerminated().get(workerId);
            int currentTime = currentPeriod * secondsByPeriod;
            if (currentTime >= timeMustBeTerminated) {
                simulationState.getWorkers().remove(workerId);
                simulationState.getMapWorkerIdTimeTerminated().remove(workerId);
            }
        }
    }

    private void checkTasks(int currentPeriod) {
        int maxParallelization = autoscalingConfig.getMaxParallelization();
        int currentTime = currentPeriod * secondsByPeriod;
        for(WorkerStats workerStats: simulationState.getWorkers().values()) {

            // End tasks which time is over
            String workerId = workerStats.getWorkerId();
            List<InputTask> workerRunningTasks = simulationState.getRunningTasks().get(workerId);
            List<InputTask> endedTasks = new ArrayList<>();
            for (InputTask inputTask: workerRunningTasks) {
                if (currentTime >= inputTask.getEndTime()) {
                    endedTasks.add(inputTask);
                }
            }
            workerRunningTasks.removeAll(endedTasks);
            allRunningTasks.removeAll(endedTasks);

            // Run new tasks
            int availableCores = workerStats.getTotalCores() - 1;
            int numRunningTasks = workerRunningTasks.size();
            if(numRunningTasks < maxParallelization) {
                int algorithmsRunning = numRunningTasks;
                while(algorithmsRunning <= maxParallelization && algorithmsRunning <= availableCores) {
                    if (!simulationState.getHighPriorityTasks().isEmpty()) {
                        InputTask newTask = simulationState.getHighPriorityTasks().poll();
                        newTask.setEndTime(newTask.getExecutionTime() + currentTime);
                        workerRunningTasks.add(newTask);
                        allRunningTasks.add(newTask);
                        algorithmsRunning++;
                    } else if (!simulationState.getLowPriorityTasks().isEmpty()) {
                        InputTask newTask = simulationState.getLowPriorityTasks().poll();
                        newTask.setEndTime(newTask.getExecutionTime() + currentTime);
                        workerRunningTasks.add(newTask);
                        allRunningTasks.add(newTask);
                        algorithmsRunning++;
                    }
                }
            }
        }
    }

    private SystemStatus getActualSystemStatus() {
        return new SystemStatus(simulationState.getHighPriorityTasks().size(), simulationState.getLowPriorityTasks().size(), new ArrayList<>(simulationState.getWorkers().values()));
    }

    private void applyAutoscaling(AutoscalingResult autoscalingResult, int currentPeriod) {
        if (!autoscalingResult.isDoNothing()) {
            // Launch workers
            int numWorkersToLaunch = autoscalingResult.getNumWorkersToLaunch();
            for(int i = 0; i < numWorkersToLaunch; i++) {
                launchWorker(currentPeriod);
            }

            // Remove Workers
            terminateWorkers(currentPeriod, autoscalingResult.getWorkersToTerminate());
        }
    }


    public void simulate() {
        for(int currentPeriod = 1; currentPeriod <= durationInPeriods; currentPeriod++) {
            checkLaunchingWorkersRunning(currentPeriod);
            checkTasks(currentPeriod);
            SystemStatus currentSystemStatus = getActualSystemStatus();
            AutoscalingResult autoScalingResult = autoscalingManager.evalAutoscaling(autoscalingConfig, currentSystemStatus);
            applyAutoscaling(autoScalingResult, currentPeriod);
        }
    }

}
