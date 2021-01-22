package es.codeurjc.squirrel.drey.local.autoscaling;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.codeurjc.squirrel.drey.local.WorkerStats;

import java.util.ArrayList;
import java.util.List;

public class AutoscalingResult {

    private SystemStatus status;
    private AutoscalingConfig config;

    private boolean doNothing = true;
    private int numWorkersToLaunch;
    private List<WorkerStats> workersToTerminate = new ArrayList<>();
    private List<WorkerStats> workersDisconnectedToTerminate = new ArrayList<>();

    public AutoscalingResult(SystemStatus status, AutoscalingConfig config) {
        this.status = status;
        this.config = config;
    }

    public boolean isDoNothing() {
        return doNothing;
    }

    public int getNumWorkersToLaunch() {
        return numWorkersToLaunch;
    }

    public List<WorkerStats> getWorkersToTerminate() {
        return workersToTerminate;
    }

    public List<WorkerStats> getWorkersDisconnectedToTerminate() {
        return workersDisconnectedToTerminate;
    }


    public AutoscalingResult numWorkersToLaunch(int numWorkersToLaunch) {
        this.numWorkersToLaunch = numWorkersToLaunch;
        doNothing = false;
        return this;
    }

    public AutoscalingResult workersToTerminate(List<WorkerStats> workersToTerminate) {
        this.workersToTerminate = workersToTerminate;
        doNothing = false;
        return this;
    }

    public AutoscalingResult workersDisconnectedToTerminate(List<WorkerStats> workersDisconnectedToTerminate) {
        this.workersDisconnectedToTerminate = workersDisconnectedToTerminate;
        doNothing = false;
        return this;
    }

    public JsonObject toJson() {
        JsonObject workers = new JsonObject();

        JsonArray terminateRunningWorkers = new JsonArray();
        JsonArray terminateDisconnectedWorkers = new JsonArray();
        this.workersToTerminate.forEach(n -> terminateRunningWorkers.add(n.toJson()));
        this.workersDisconnectedToTerminate.forEach(n -> terminateDisconnectedWorkers.add(n.toJson()));

        workers.addProperty("numWorkersToLaunch", numWorkersToLaunch);
        workers.add("workersToTerminate", terminateRunningWorkers);
        workers.add("workersDisconnectedToTerminate", terminateDisconnectedWorkers);

        JsonObject system = new JsonObject();
        system.add("config", this.config.toJson());
        system.add("status", this.status.toJson());

        JsonObject json = new JsonObject();
        json.addProperty("reason", this.generateReason());
        json.add("workers", workers);
        json.add("system", system);
        return json;
    }


    public String generateReason() {
        return "";
    }

    @Override
    public String toString() {
        return "AutoscalingResult [" +
                "status=" + status +
                ", config=" + config +
                ", doNothing=" + doNothing +
                ", numWorkersToLaunch=" + numWorkersToLaunch +
                ", workersToTerminate=" + workersToTerminate +
                ", workersDisconnectedToTerminate=" + workersDisconnectedToTerminate + "]";
    }


}
