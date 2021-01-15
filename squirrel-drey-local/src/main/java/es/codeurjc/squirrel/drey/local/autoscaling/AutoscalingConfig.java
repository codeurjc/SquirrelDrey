package es.codeurjc.squirrel.drey.local.autoscaling;

import com.google.gson.JsonObject;

public class AutoscalingConfig {

    private final int minWorkers;
    private final int maxWorkers;
    private final int minIdleWorkers;
    private final int maxParallelization;
    private final int workersByMaxParallelization;
    private final int maxSecondsIdle;
    private final int maxSecondsNonRespondingWorker;

    private AutoscalingConfig(int minWorkers, int maxWorkers, int minIdleWorkers, int maxParallelization, int workersByMaxParallelization,
                              int maxSecondsIdle, int maxSecondsNoRespondingWorker) {
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.minIdleWorkers = minIdleWorkers;
        this.maxParallelization = maxParallelization;
        this.workersByMaxParallelization = workersByMaxParallelization;
        this.maxSecondsIdle = maxSecondsIdle;
        this.maxSecondsNonRespondingWorker = maxSecondsNoRespondingWorker;
    }

    public static class Builder {
        private int minWorkers = 1;
        private int maxWorkers = 4;
        private int minIdleWorkers = 2;
        private int maxParallelization = 4;
        private int workersByMaxParallelization = 2;
        private int maxSecondsIdle = 60;
        private int maxSecondsNonRespondingWorker = 60;

        public AutoscalingConfig build() {
            return new AutoscalingConfig(this.minWorkers, this.maxWorkers, this.minIdleWorkers, this.maxParallelization,
                    this.workersByMaxParallelization, this.maxSecondsIdle, this.maxSecondsNonRespondingWorker);
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

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("minWorkers", minWorkers);
        json.addProperty("maxWorkers", maxWorkers);
        json.addProperty("minIdleWorkers", minIdleWorkers);
        json.addProperty("maxParallelization", maxParallelization);
        json.addProperty("workersByMaxParallelization", workersByMaxParallelization);
        json.addProperty("maxSecondsIdle", maxSecondsIdle);
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
                ", maxSecondsIdle" + maxSecondsIdle + "]";
    }
}
