package es.codeurjc.squirrel.drey.local.simulator.core;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import es.codeurjc.squirrel.drey.local.WorkerStats;
import es.codeurjc.squirrel.drey.local.WorkerStatus;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingConfig;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingResult;
import es.codeurjc.squirrel.drey.local.autoscaling.SystemStatus;
import es.codeurjc.squirrel.drey.local.simulator.SimulationResult;
import es.codeurjc.squirrel.drey.local.simulator.config.InputTaskConfig;
import es.codeurjc.squirrel.drey.local.simulator.config.ScenaryConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
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
    private int initialNumWorkers;
    private int workerCores;

    private AutoscalingConfig autoscalingConfig;

    private Map<Integer, List<InputTask>> highPriorityTasks = new HashMap<>();
    private Map<Integer, List<InputTask>> lowPriorityTasks = new HashMap<>();

    // Simulation state
    private SimulationState simulationState = new SimulationState();

    // Simulation result
    private SimulationResult simulationResult;

    private Random random;

    public static String DEFAULT_RESOURCES_CONFIG_FILE = "simulation_config.json";
    private String RANDOM_WORKER_ID_CHARACTERS = "abcdefghijklmnopqrstuvwxyz123456789_-";

    public Simulation() throws FileNotFoundException, ParseException {
        this(null);
    }

    public Simulation(String configFile) throws FileNotFoundException, ParseException {
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
        this.initialNumWorkers = scenaryConfig.getInitialRunningWorkers();
        this.workerCores = scenaryConfig.getWorkerCores();
        this.random = new Random(this.seed);
        this.autoscalingConfig = scenaryConfig.getAutoscalingConfig();
        this.simulationResult = new SimulationResult(durationInPeriods, secondsByPeriod, autoscalingConfig.getMinWorkers(),
                autoscalingConfig.getMaxWorkers(), autoscalingConfig.getMinIdleWorkers());
        this.generateInitialInputTasks(scenaryConfig);
        this.generateInitialState();

        // Do simulation
        for(int currentPeriod = 1; currentPeriod <= durationInPeriods; currentPeriod++) {
            checkLaunchingWorkersRunning(currentPeriod);
            checkTerminatingWorkers(currentPeriod);
            simulateFetchWorkers(currentPeriod);
            checkTasks(currentPeriod);
            SystemStatus currentSystemStatus = getActualSystemStatus();
            AutoscalingManagerSimulator autoscalingManager = new AutoscalingManagerSimulator(currentPeriod, secondsByPeriod);
            AutoscalingResult autoScalingResult = autoscalingManager.evalAutoscaling(autoscalingConfig, currentSystemStatus);
            applyAutoscaling(autoScalingResult, currentPeriod);
            this.generateSimulationResult(currentPeriod);
        }
    }

    public SimulationResult getSimulationResult() {
        return this.simulationResult;
    }

    private void generateInitialInputTasks(ScenaryConfig scenaryConfig) {
        // Initialize Array for all periods
        for(int period = 1; period <= durationInPeriods; period++) {
            highPriorityTasks.put(period, new ArrayList<>());
            lowPriorityTasks.put(period, new ArrayList<>());
        }

        // Initialize defined input tasks
        for(InputTaskConfig inputTaskConfig: scenaryConfig.getInputTasks()) {
            int fromPeriod = inputTaskConfig.getFromPeriod();
            int toPeriod = inputTaskConfig.getToPeriod();
            for(int period = fromPeriod; period <= toPeriod; period++) {
                // High priority tasks
                List<InputTask> highPriorityTasksEnteringInPeriod = new ArrayList<>();
                int minExecutionTimeHP = inputTaskConfig.getMinTimeForHighPriorityTasksToComplete();
                int maxExecutionTimeHP = inputTaskConfig.getMaxTimeForHighPriorityTasksToComplete();
                for(int currentTask = 0; currentTask < inputTaskConfig.getNumHighPriorityTasksByPeriod(); currentTask++) {
                    int executionTime = random.nextInt(maxExecutionTimeHP - minExecutionTimeHP + 1) + minExecutionTimeHP;
                    highPriorityTasksEnteringInPeriod.add(new InputTask(executionTime));
                }
                highPriorityTasks.put(period, highPriorityTasksEnteringInPeriod);
                // Low priority tasks
                List<InputTask> lowPriorityTasksEnteringInPeriod = new ArrayList<>();
                int minExecutionTimeLP = inputTaskConfig.getMinTimeForLowPriorityTasksToComplete();
                int maxExecutionTimeLP = inputTaskConfig.getMaxTimeForLowPriorityTasksToComplete();
                for(int currentTask = 0; currentTask < inputTaskConfig.getNumLowPriorityTasksByPeriod(); currentTask++) {
                    int executionTime = random.nextInt(maxExecutionTimeLP - minExecutionTimeLP + 1) + minExecutionTimeLP;
                    lowPriorityTasksEnteringInPeriod.add(new InputTask(executionTime));
                }
                lowPriorityTasks.put(period, lowPriorityTasksEnteringInPeriod);
            }
        }
    }

    private void generateInitialState() {
        int numWorkersRunning = initialNumWorkers;
        int currentTime = getCurrentTime(1);
        for(int i = 0; i < numWorkersRunning; i++) {
            String workerId = generateRandomWorkerId();
            WorkerStats workerStats = new WorkerStats(currentTime, workerId, 0, workerId, null, workerCores, 0,
                    0, 0, 0, autoscalingConfig.getMaxParallelization(), WorkerStatus.running);
            workerStats.setLastTimeFetched(currentTime);
            workerStats.setLastTimeWorking(currentTime);
            this.simulationState.getWorkers().put(workerId, workerStats);
            this.simulationState.getRunningTasks().put(workerId, new ArrayList<>());
        }
    }

    private String generateRandomWorkerId() {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 20; i++) {
            sb.append(RANDOM_WORKER_ID_CHARACTERS.charAt(random.nextInt(RANDOM_WORKER_ID_CHARACTERS.length())));
        }
        return sb.toString();
    }

    private void launchWorker(int currentPeriod) {
        String workerId = generateRandomWorkerId();
        int currentTime = getCurrentTime(currentPeriod);
        int timeToBeRunning = random.nextInt(maxTimeForWorkersToBeRunning - minTimeForWorkersToBeRunning + 1) + minTimeForWorkersToBeRunning;
        WorkerStats workerStats = new WorkerStats(currentTime, workerId, 1, workerId, null, workerCores, 0,
                0, 0, 0, autoscalingConfig.getMaxParallelization(), WorkerStatus.launching);
        workerStats.setLastTimeFetched(currentTime);
        workerStats.setLastTimeWorking(currentTime);
        simulationState.getWorkers().put(workerId, workerStats);
        simulationState.getRunningTasks().put(workerId, new ArrayList<>());
        simulationState.getMapWorkerIdTimeLaunched().put(workerId, currentTime + timeToBeRunning);
    }

    private void terminateWorkers(int currentPeriod, List<WorkerStats> workersToTerminate) {
        for(WorkerStats workerStats: workersToTerminate) {
            String workerId = workerStats.getWorkerId();
            int timeToTerminate = random.nextInt(maxTimeForWorkersToTerminate - minTimeForWorkersToTerminate + 1) + minTimeForWorkersToTerminate;
            simulationState.getWorkers().get(workerId).setStatus(WorkerStatus.terminating);
            simulationState.getRunningTasks().remove(workerId);
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
            int currentTime = getCurrentTime(currentPeriod);
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
            int currentTime = getCurrentTime(currentPeriod);
            if (currentTime >= timeMustBeTerminated) {
                simulationState.getWorkers().remove(workerId);
                simulationState.getMapWorkerIdTimeTerminated().remove(workerId);
            }
        }
    }

    private void simulateFetchWorkers(int currentPeriod) {
        simulationState.getWorkers().values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.running)
                .forEach(w -> w.setLastTimeFetched(getCurrentTime(currentPeriod)));
    }

    private void checkTasks(int currentPeriod) {
        int maxParallelization = autoscalingConfig.getMaxParallelization();
        int currentTime = getCurrentTime(currentPeriod);

        // End tasks which time is over
        for(WorkerStats workerStats: simulationState.getAllRuningWorkers()) {
            String workerId = workerStats.getWorkerId();
            List<InputTask> workerRunningTasks = simulationState.getRunningTasks().get(workerId);
            List<InputTask> endedTasks = new ArrayList<>();
            for (InputTask inputTask : workerRunningTasks) {
                if (currentTime >= inputTask.getEndTime()) {
                    endedTasks.add(inputTask);
                }
            }
            workerRunningTasks.removeAll(endedTasks);
            workerStats.setTasksRunning(workerStats.getTasksRunning() - endedTasks.size());
            workerStats.setWorkingCores(workerStats.getWorkingCores() - endedTasks.size());
        }

        // Add tasks to queues
        List<InputTask> newHighPriorityTasks = highPriorityTasks.get(currentPeriod);
        List<InputTask> newLowHighPriorityTasks = lowPriorityTasks.get(currentPeriod);
        newHighPriorityTasks.forEach(newHighPriorityTask -> simulationState.getHighPriorityTasks().add(newHighPriorityTask));
        newLowHighPriorityTasks.forEach(newLowPriorityTasks -> simulationState.getLowPriorityTasks().add(newLowPriorityTasks));

        // Run new tasks
        for(WorkerStats workerStats: simulationState.getAllRuningWorkers()) {
            String workerId = workerStats.getWorkerId();
            List<InputTask> workerRunningTasks = simulationState.getRunningTasks().get(workerId);


            int availableCores = workerStats.getTotalCores() - 1; // One core is available for SQS
            while(workerRunningTasks.size() < maxParallelization
                    && workerRunningTasks.size() < availableCores
                    && (!simulationState.getHighPriorityTasks().isEmpty() || !simulationState.getLowPriorityTasks().isEmpty())) {
                if (!simulationState.getHighPriorityTasks().isEmpty()) {
                    InputTask newTask = simulationState.getHighPriorityTasks().poll();
                    newTask.setEndTime(newTask.getExecutionTime() + currentTime);
                    workerRunningTasks.add(newTask);
                    workerStats.setTasksRunning(workerStats.getTasksRunning() + 1);
                    workerStats.setWorkingCores(workerStats.getWorkingCores() + 1);
                    workerStats.setLastTimeFetched(currentTime);
                    workerStats.setLastTimeWorking(currentTime);
                } else if (!simulationState.getLowPriorityTasks().isEmpty()) {
                    InputTask newTask = simulationState.getLowPriorityTasks().poll();
                    newTask.setEndTime(newTask.getExecutionTime() + currentTime);
                    workerRunningTasks.add(newTask);
                    workerStats.setTasksRunning(workerStats.getTasksRunning() + 1);
                    workerStats.setWorkingCores(workerStats.getWorkingCores() + 1);
                    workerStats.setLastTimeFetched(currentTime);
                    workerStats.setLastTimeWorking(currentTime);
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

    private int getCurrentTime(int currentPeriod) {
        return (currentPeriod * secondsByPeriod) - secondsByPeriod;
    }

    private void generateSimulationResult(int currentPeriod) {
        this.simulationResult.addNumOfHighPriorityTasks(currentPeriod, this.simulationState.getHighPriorityTasks().size());
        this.simulationResult.addNumOfLowPriorityTasks(currentPeriod, this.simulationState.getLowPriorityTasks().size());
        int totalQueuedTasks = this.simulationState.getHighPriorityTasks().size() + this.simulationState.getLowPriorityTasks().size();
        this.simulationResult.addTotalTasks(currentPeriod, totalQueuedTasks);
        this.simulationResult.addNumRunningWorkers(currentPeriod, this.simulationState.getAllRuningWorkers().size());
        this.simulationResult.addNumLaunchingWorkers(currentPeriod, this.simulationState.getAllLaunchingWorkers().size());
        this.simulationResult.addNumTerminatingWorkers(currentPeriod, this.simulationState.getAllTerminatingWorkers().size());
        this.simulationResult.addNumDisconnectedWorkers(currentPeriod, this.simulationState.getAllDisconnectedWorkers().size());
        this.simulationResult.addNumIdleWorkers(currentPeriod, this.simulationState.getAllIdleWorkers().size());
        this.simulationResult.addRunningTasks(currentPeriod, this.simulationState.getNumRunningTasks());
    }

}
