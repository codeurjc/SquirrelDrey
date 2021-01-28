package es.codeurjc.squirrel.drey.local.autoscaling;

import com.google.gson.JsonObject;
import es.codeurjc.squirrel.drey.local.Config;

public class AutoscalingConfig {

    private final int minWorkers;
    private final int maxWorkers;
    private final int minIdleWorkers;
    private final int maxParallelization;
    private final int workersByMaxParallelization;
    private final int maxSecondsIdle;
    private final int maxSecondsNonRespondingWorker;
    private final int maxSecondsLaunchingNonRespondingWorker;
    private final int monitoringPeriod;
    private final int maxTimeOutFetchWorkers;

    private AutoscalingConfig(int minWorkers, int maxWorkers, int minIdleWorkers, int maxParallelization, int workersByMaxParallelization,
                              int maxSecondsIdle, int maxSecondsNoRespondingWorker, int maxSecondsLaunchingNonRespondingWorker,
                              int monitoringPeriod, int maxTimeOutFetchWorkers) {
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.minIdleWorkers = minIdleWorkers;
        this.maxParallelization = maxParallelization;
        this.workersByMaxParallelization = workersByMaxParallelization;
        this.maxSecondsIdle = maxSecondsIdle;
        this.maxSecondsNonRespondingWorker = maxSecondsNoRespondingWorker;
        this.maxSecondsLaunchingNonRespondingWorker = maxSecondsLaunchingNonRespondingWorker;
        this.monitoringPeriod = monitoringPeriod;
        this.maxTimeOutFetchWorkers = maxTimeOutFetchWorkers;
    }

    public static class Builder {
        private int minWorkers = Config.MIN_WORKERS;
        private int maxWorkers = Config.MAX_WORKERS;
        private int minIdleWorkers = Config.MIN_IDLE_WORKERS;
        private int maxParallelization = Config.DEFAULT_PARALLELIZATION_GRADE;
        private int workersByMaxParallelization = Config.WORKERS_BY_MAX_PARALLELIZATION;
        private int maxSecondsIdle = Config.MAX_SECONDS_IDLE;
        private int maxSecondsNonRespondingWorker = Config.MAX_SECONDS_NOT_RESPONDING;
        private int maxSecondsLaunchingNonRespondingWorker = Config.MAX_SECONDS_LAUNCHING_NOT_RESPONDING;
        private int monitoringPeriod = Config.DEFAULT_MONITORING_PERIOD;
        private int maxTimeOutFetchWorkers = Config.DEFAULT_MAX_TIMEOUT_FETCH_WORKERS;

        public AutoscalingConfig build() {
            return new AutoscalingConfig(this.minWorkers, this.maxWorkers, this.minIdleWorkers, this.maxParallelization,
                    this.workersByMaxParallelization, this.maxSecondsIdle, this.maxSecondsNonRespondingWorker, this.maxSecondsLaunchingNonRespondingWorker,
                    this.monitoringPeriod, this.maxTimeOutFetchWorkers);
        }

        public Builder minWorkers(int minWorkers) {
            this.minWorkers = minWorkers;
            return this;
        }

        public Builder maxWorkers(int maxWorkers) {
            this.maxWorkers = maxWorkers;
            return this;
        }

        public Builder minIdleWorkers(int minIdleWorkers) {
            this.minIdleWorkers = minIdleWorkers;
            return this;
        }

        public Builder maxParallelization(int parallelization) {
            this.maxParallelization = parallelization;
            return this;
        }

        public Builder workersByMaxParallelization(int workersByMaxParallelization) {
            this.workersByMaxParallelization = workersByMaxParallelization;
            return this;
        }

        public Builder maxSecondsIdle(int maxSecondsIdle) {
            this.maxSecondsIdle = maxSecondsIdle;
            return this;
        }

        public Builder maxSecondsNonRespondingWorker(int maxSecondsNonRespondingWorker) {
            this.maxSecondsNonRespondingWorker = maxSecondsNonRespondingWorker;
            return this;
        }

        public Builder maxSecondsLaunchingNonRespondingWorker(int maxSecondsLaunchingNonRespondingWorker) {
            this.maxSecondsLaunchingNonRespondingWorker = maxSecondsLaunchingNonRespondingWorker;
            return this;
        }

        public Builder monitoringPeriod(int monitoringPeriod) {
            this.monitoringPeriod = monitoringPeriod;
            return this;
        }

        public Builder maxTimeOutFetchWorkers(int maxTimeOutFetchWorkers) {
            this.maxTimeOutFetchWorkers = maxTimeOutFetchWorkers;
            return this;
        }
    }

    public int getMinWorkers() {
        return minWorkers;
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }

    public int getMinIdleWorkers() {
        return minIdleWorkers;
    }

    public int getMaxParallelization() {
        return maxParallelization;
    }

    public int getWorkersByMaxParallelization() {
        return workersByMaxParallelization;
    }

    public int getMaxSecondsIdle() {
        return maxSecondsIdle;
    }

    public int getMaxSecondsNonRespondingWorker() {
        return maxSecondsNonRespondingWorker;
    }

    public int getMaxSecondsLaunchingNonRespondingWorker() { return maxSecondsLaunchingNonRespondingWorker; }

    public int getMonitoringPeriod() {
        return monitoringPeriod;
    }

    public int getMaxTimeOutFetchWorkers() {
        return this.maxTimeOutFetchWorkers;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("minWorkers", minWorkers);
        json.addProperty("maxWorkers", maxWorkers);
        json.addProperty("minIdleWorkers", minIdleWorkers);
        json.addProperty("maxParallelization", maxParallelization);
        json.addProperty("workersByMaxParallelization", workersByMaxParallelization);
        json.addProperty("maxSecondsIdle", maxSecondsIdle);
        json.addProperty("maxSecondsNonRespondingWorker", maxSecondsNonRespondingWorker);
        json.addProperty("maxSecondsLaunchingNonRespondingWorker", maxSecondsLaunchingNonRespondingWorker);
        json.addProperty("monitoringPeriod", monitoringPeriod);
        json.addProperty("maxTimeOutFetchWorkers", maxTimeOutFetchWorkers);
        return json;
    }

    @Override
    public String toString() {
        return "AutoscalingConfig [" +
                "minWorkers=" + minWorkers +
                ", maxWorkers=" + maxWorkers +
                ", minIdleWorkers=" + minIdleWorkers +
                ", maxParallelization=" + maxParallelization +
                ", workersByMaxParallelization=" + workersByMaxParallelization +
                ", maxSecondsIdle=" + maxSecondsIdle +
                ", maxSecondsNonRespondingWorker=" + maxSecondsNonRespondingWorker +
                ", maxSecondsLaunchingNonRespondingWorker=" + maxSecondsLaunchingNonRespondingWorker +
                ", monitoringPeriod=" + monitoringPeriod +
                ", maxTimeOutFetchWorkers=" + maxTimeOutFetchWorkers + "]";
    }
}
