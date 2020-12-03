package es.codeurjc.squirrel.drey.local.autoscaling;

import com.google.gson.JsonObject;

public class AutoscalingConfig {

    private final int minWorkers;
    private final int maxWorkers;
    private final int minIdleWorkers;
    private final int maxParallelization;
    private final int workersByMaxParallelization;

    private AutoscalingConfig(int minWorkers, int maxWorkers, int minIdleWorkers, int maxParallelization, int workersByMaxParallelization) {
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.minIdleWorkers = minIdleWorkers;
        this.maxParallelization = maxParallelization;
        this.workersByMaxParallelization = workersByMaxParallelization;
    }

    public static class Builder {
        private int minWorkers = 1;
        private int maxWorkers = 4;
        private int minIdleWorkers = 2;
        private int maxParallelization = 4;
        private int workersByMaxParallelization = 2;

        public AutoscalingConfig build() {
            return new AutoscalingConfig(this.minWorkers, this.maxWorkers, this.minIdleWorkers, this.maxParallelization, this.workersByMaxParallelization);
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

        public Builder maxParalleization(int parallelization) {
            this.maxParallelization = parallelization;
            return this;
        }

        public Builder workersByMaxParallelization(int workersByMaxParallelization) {
            this.workersByMaxParallelization = workersByMaxParallelization;
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

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("minWorkers", minWorkers);
        json.addProperty("maxWorkers", maxWorkers);
        json.addProperty("minIdleWorkers", minIdleWorkers);
        json.addProperty("maxParallelization", maxParallelization);
        json.addProperty("workersByMaxParallelization", workersByMaxParallelization);
        return json;
    }

    @Override
    public String toString() {
        return "AutoscalingConfig [" +
                "minWorkers=" + minWorkers +
                ", maxWorkers=" + maxWorkers +
                ", minIdleWorkers=" + minIdleWorkers +
                ", maxParallelization=" + maxParallelization +
                ", workersByMaxParallelization=" + workersByMaxParallelization + "]";
    }
}
