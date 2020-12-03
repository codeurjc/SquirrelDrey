package es.codeurjc.squirrel.drey.local.autoscaling;

import es.codeurjc.squirrel.drey.local.WorkerStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AutoscalingManager {

    public AutoscalingResult evalAutoscaling(AutoscalingConfig config, SystemStatus status) {
        // TODO
        return null;
    }

    private int calculateNumWorkersToLaunch(SystemStatus status, AutoscalingConfig config) {
        // TODO
        return 0;
    }

    private int calculateNumWorkersToTerminate(SystemStatus status, AutoscalingConfig config) {
        // TODO
        return 0;
    }

    private List<WorkerStats> sortedByNumTasksRunningAsc(List<WorkerStats> workers) {
        List<WorkerStats> result = new ArrayList<>(workers);
        result.sort(Comparator.comparing(WorkerStats::getTaskRunning));
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
