package es.codeurjc.squirrel.drey.local.simulator.core;

import es.codeurjc.squirrel.drey.local.WorkerStats;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingManager;

public class AutoscalingManagerSimulator extends AutoscalingManager {

    private long currentPeriod;
    private long secondsByPeriod;

    public AutoscalingManagerSimulator(long currentPeriod, long secondsByPeriod) {
        super();
        this.currentPeriod = currentPeriod;
        this.secondsByPeriod = secondsByPeriod;
    }

    @Override
    protected long getCurrentTimeInSeconds() {
        return (currentPeriod * secondsByPeriod) - secondsByPeriod;
    }

    @Override
    protected long getLastTimeWorkingInSeconds(WorkerStats workerStats) {
        return workerStats.getLastTimeWorking();
    }

    @Override
    protected long getLastTimeFetchedInSeconds(WorkerStats workerStats) {
        return workerStats.getLastTimeFetched();
    }

}
