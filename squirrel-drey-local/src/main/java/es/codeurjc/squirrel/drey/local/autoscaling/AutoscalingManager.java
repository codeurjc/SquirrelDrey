package es.codeurjc.squirrel.drey.local.autoscaling;

import es.codeurjc.squirrel.drey.local.WorkerStats;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoscalingManager {

    public AutoscalingResult evalAutoscaling(AutoscalingConfig config, SystemStatus status) {

        // Scale UP
        int totalQueuedMessages = status.getNumHighPriorityMessages() + status.getNumLowPriorityMessages();
        if ((status.getNumWorkers() < config.getMinWorkers())
                || (totalQueuedMessages > 0 && status.getNumWorkers() < config.getMaxWorkers())) {

            int numWorkersToLaunch = calculateNumWorkersToLaunch(status, config);
            return new AutoscalingResult(status, config).numWorkersToLaunch(numWorkersToLaunch);

        // Scale Down
        } else if ((status.getNumWorkers() > config.getMaxWorkers())
                || totalQueuedMessages == 0 && status.getNumWorkers() > config.getMinWorkers()) {

            // Too much workers according to Max workers, or not too much workers but the average
            // load is below the lower limit. Terminate required workers following the
            // algorithm's priority
            List<WorkerStats> workersReadyToTerminate = getWorkersReadyToTerminate(status, config);
            int numWorkersToTerminate = workersReadyToTerminate.size();
            return new AutoscalingResult(status, config)
                    .workersToTerminate(workersReadyToTerminate.subList(0, numWorkersToTerminate));

        // Do nothing
        } else {

            // Don't apply anything into the cluster
            return new AutoscalingResult(status, config);
        }
    }

    private int calculateNumWorkersToLaunch(SystemStatus status, AutoscalingConfig config) {
        int totalQueuedMessages = status.getNumHighPriorityMessages() + status.getNumLowPriorityMessages();
        int maxParallelization = config.getMaxParallelization();
        int workersByMaxParallelization = config.getWorkersByMaxParallelization();
        // Necessary num of workers needed to satisfy queues demand
        int idealNumOfWorkers = totalQueuedMessages / maxParallelization + ((totalQueuedMessages % maxParallelization == 0) ? 0 : 1);
        idealNumOfWorkers = idealNumOfWorkers * workersByMaxParallelization;

        // Not necessary workers because they are launching
        int notNecessaryWorkers = status.getLaunchingWorkers().size();
        return idealNumOfWorkers - notNecessaryWorkers;
    }

    private List<WorkerStats> getWorkersReadyToTerminate(SystemStatus status, AutoscalingConfig config) {
        // Get all non responding workers
        // and order by last time fetched ascendant
        List<WorkerStats> nonRespondingWorkers = status.getRunningWorkers().stream()
                .filter(w -> isWorkerExceedingMaxTimeNonResponding(w, config))
                .sorted(Comparator.comparing(WorkerStats::getLastTimeFetched))
                .collect(Collectors.toList());

        // Get all workers connect with 0 running tasks which exceeds max idle time
        // and order idle workers to terminate by launch time ascendant
        List<WorkerStats> iddleWorkersToTerminate = status.getRunningWorkers().stream()
                .filter(w -> w.getTasksRunning() == 0)
                .filter(w -> isWorkerExceedingMaxTimeIdle(w, config))
                .filter(w -> !isWorkerExceedingMaxTimeNonResponding(w, config))
                .sorted(Comparator.comparing(WorkerStats::getLaunchingTime))
                .collect(Collectors.toList());

        // Don't delete minimum number of idle workers
        int minIddleWorkers = config.getMinIdleWorkers();
        iddleWorkersToTerminate = iddleWorkersToTerminate.subList(0, iddleWorkersToTerminate.size() - minIddleWorkers);

        // Concat both lists
        List<WorkerStats> allWorkersToTerminate = new ArrayList<>(nonRespondingWorkers);
        allWorkersToTerminate.addAll(iddleWorkersToTerminate);
        return allWorkersToTerminate;
    }


    private boolean isWorkerExceedingMaxTimeNonResponding(WorkerStats workerStats, AutoscalingConfig config) {
        long currentTime = (long) ((double) System.currentTimeMillis() / 1000);
        long lastTimeFetched = (long) ((double) workerStats.getLastTimeFetched());
        long secondsSinceLastFetch = currentTime - lastTimeFetched;
        return secondsSinceLastFetch > config.getMaxSecondsNonRespondingWorker();
    }

    private boolean isWorkerExceedingMaxTimeIdle(WorkerStats workerStats, AutoscalingConfig config) {
        if (workerStats.getTasksRunning() == 0) {
            return false;
        }
        long currentTime = (long) ((double) System.currentTimeMillis() / 1000);
        long lastTimeWorking = (long) ((double) workerStats.getLastTimeWorking() / 1000);
        long secondsIdle = currentTime - lastTimeWorking;
        return secondsIdle > config.getMaxSecondsIdle();
    }

}
