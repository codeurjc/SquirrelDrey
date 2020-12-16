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

            // Not enough workers according to Min workers, or enough workers but the are messages in the input queue
            // Launch required workers

            int numWorkersToLaunch = calculateNumWorkersToLaunch(status, config);
            if (status.hasWaitingIdleToTerminateWorkers() || status.hasCanceledWorkers()) {

                // Fill as much as possible the launching workers by changing to status "running"
                // workers with status "waiting-idle-to-terminate" and then changing to
                // "launching" workers with status "canceled"
                List<WorkerStats> waitingWorkers = sortedByNumTasksRunningDesc(status.getWaitingIdleToTerminateWorkers());
                List<WorkerStats> canceledWorkers = sortedByLaunchDateAsc(status.getCanceledWorkers());

                if (waitingWorkers.size() + canceledWorkers.size() >= numWorkersToLaunch) {
                    // No need of new workers. waiting-idle-to-terminate + canceled are enough

                    if (waitingWorkers.size() >= numWorkersToLaunch) {
                        // No need of transitioning canceled workers to launching
                        return new AutoscalingResult(status, config)
                                .relaunchWaitingIdleToTerminateWorkers(waitingWorkers.subList(0, numWorkersToLaunch));
                    } else {
                        // There is need of recovering both waiting-idle-to-terminate and canceled workers
                        return new AutoscalingResult(status, config).relaunchWaitingIdleToTerminateWorkers(waitingWorkers)
                                .relaunchCanceledWorkers(
                                        canceledWorkers.subList(0, numWorkersToLaunch - waitingWorkers.size()));
                    }

                } else {
                    return new AutoscalingResult(status, config).relaunchWaitingIdleToTerminateWorkers(waitingWorkers)
                            .relaunchCanceledWorkers(canceledWorkers)
                            .numWorkersToLaunch(numWorkersToLaunch - (waitingWorkers.size() + canceledWorkers.size()));
                }

            } else {

                // No workers with waiting-idle-to-terminate status. Launch new ones
                return new AutoscalingResult(status, config).numWorkersToLaunch(numWorkersToLaunch);
            }

        } else if ((status.getNumWorkers() > config.getMaxWorkers())
                || totalQueuedMessages == 0 && status.getNumWorkers() > config.getMinWorkers()) {

            // Too much workers according to Max workers, or not too much workers but the average
            // load is below the lower limit. Terminate required workers following the
            // algorithm's priority

            int numWorkersToTerminate = calculateNumWorkersToTerminate(status, config);
            List<WorkerStats> runningWorkers = sortedByNumTasksRunningAsc(status.getRunningWorkers());

            if (status.hasLaunchingWorkers()) {

                // Fill as much as possible the terminating workers by changing to status
                // "terminating" workers with status "launching"
                List<WorkerStats> launchingWorkers = sortedByLaunchDateAsc(status.getLaunchingWorkers());
                if (launchingWorkers.size() >= numWorkersToTerminate) {
                    return new AutoscalingResult(status, config)
                            .terminateLaunchingWorkers(launchingWorkers.subList(0, numWorkersToTerminate));
                } else {
                    return new AutoscalingResult(status, config).terminateLaunchingWorkers(launchingWorkers)
                            .workersToTerminate(runningWorkers.subList(0, numWorkersToTerminate - launchingWorkers.size()));
                }
            } else {

                // No workers with launching status. Terminate running ones
                return new AutoscalingResult(status, config)
                        .workersToTerminate(runningWorkers.subList(0, numWorkersToTerminate));
            }

        } else {

            // Don't apply anything into the cluster
            return new AutoscalingResult(status, config);
        }
    }

    private int calculateNumWorkersToTerminate(SystemStatus status, AutoscalingConfig config) {
        int totalQueuedMessages = status.getNumHighPriorityMessages() + status.getNumLowPriorityMessages();
        // No workers should be terminated if total queued messages is greater than 0
        if (totalQueuedMessages > 0) {
            return 0;
        } else {
            return getIdleWorkers(status, config, true).size();
        }
    }

    private int calculateNumWorkersToLaunch(SystemStatus status, AutoscalingConfig config) {
        int totalQueuedMessages = status.getNumHighPriorityMessages() + status.getNumLowPriorityMessages();
        int maxParallelization = config.getMaxParallelization();
        int workersByMaxParallelization = config.getWorkersByMaxParallelization();
        // Necessary num of workers needed to satisfy queues demand
        int idealNumOfWorkers = totalQueuedMessages / maxParallelization + ((totalQueuedMessages % maxParallelization == 0) ? 0 : 1);

        return idealNumOfWorkers * workersByMaxParallelization;
    }

    private List<WorkerStats> getIdleWorkers(SystemStatus status, AutoscalingConfig config, boolean getOnlyMaxSecondsIdle) {
        Stream<WorkerStats> iddleWorkers = status.getRunningWorkers().stream().filter(w -> w.getTasksRunning() == 0);
        if (getOnlyMaxSecondsIdle) {
            return iddleWorkers.filter((workerStats -> isWorkerExceedingMaxTimeIdle(workerStats, config))).collect(Collectors.toList());
        }
        return iddleWorkers.collect(Collectors.toList());
    }

    private List<WorkerStats> getNonRespondingWorkers(SystemStatus status, AutoscalingConfig config) {
        return status.getRunningWorkers().stream()
                .filter((workerStats -> isWorkerExceedingMaxTimeNonResponding(workerStats, config)))
                .collect(Collectors.toList());
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

    /**
    private List<WorkerStats> sortedByNumTasksAndNotResponding(List<WorkerStats> workers) {

    }
     **/

    private List<WorkerStats> sortedByNumTasksRunningAsc(List<WorkerStats> workers) {
        List<WorkerStats> result = new ArrayList<>(workers);
        result.sort(Comparator.comparing(WorkerStats::getTasksRunning));
        return result;
    }

    private List<WorkerStats> sortedByNumTasksRunningDesc(List<WorkerStats> workers) {
        List<WorkerStats> result = sortedByNumTasksRunningAsc(workers);
        Collections.reverse(result);
        return result;
    }

    private List<WorkerStats> sortedByLaunchDateAsc(List<WorkerStats> workers) {
        List<WorkerStats> result = new ArrayList<>(workers);
        result.sort(Comparator.comparing(WorkerStats::getLaunchingTime));
        return result;
    }

}
