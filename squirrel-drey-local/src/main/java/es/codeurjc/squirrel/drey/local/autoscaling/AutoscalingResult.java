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
    private List<WorkerStats> relaunchWaitingIdleToTerminateWorkers = new ArrayList<>();
    private List<WorkerStats> relaunchCanceledWorkers = new ArrayList<>();
    private List<WorkerStats> terminateLaunchingWorkers = new ArrayList<>();
    private List<WorkerStats> workersToTerminate = new ArrayList<>();

    public AutoscalingResult(SystemStatus status, AutoscalingConfig config) {
        this.status = status;
        this.config = config;
    }

    public boolean isDoNothing() {
        return doNothing;
    }

    public List<WorkerStats> getRelaunchWaitingIdleToTerminateWorkers() {
        return relaunchWaitingIdleToTerminateWorkers;
    }

    public List<WorkerStats> getRelaunchCanceledWorkers() {
        return relaunchCanceledWorkers;
    }

    public int getNumWorkersToLaunch() {
        return numWorkersToLaunch;
    }

    public List<WorkerStats> getTerminateLaunchingWorkers() {
        return terminateLaunchingWorkers;
    }

    public List<WorkerStats> getWorkersToTerminate() {
        return workersToTerminate;
    }

    public AutoscalingResult relaunchWaitingIdleToTerminateWorkers(List<WorkerStats> relaunchWaitingIdleToTerminateWorkers) {
        this.relaunchWaitingIdleToTerminateWorkers = relaunchWaitingIdleToTerminateWorkers;
        doNothing = false;
        return this;
    }

    public AutoscalingResult relaunchCanceledWorkers(List<WorkerStats> relaunchCanceledWorkers) {
        this.relaunchCanceledWorkers = relaunchCanceledWorkers;
        doNothing = false;
        return this;
    }

    public AutoscalingResult numWorkersToLaunch(int numWorkersToLaunch) {
        this.numWorkersToLaunch = numWorkersToLaunch;
        doNothing = false;
        return this;
    }

    public AutoscalingResult terminateLaunchingWorkers(List<WorkerStats> terminateLaunchingWorkers) {
        this.terminateLaunchingWorkers = terminateLaunchingWorkers;
        doNothing = false;
        return this;
    }

    public AutoscalingResult workersToTerminate(List<WorkerStats> workersToTerminate) {
        this.workersToTerminate = workersToTerminate;
        doNothing = false;
        return this;
    }

    public JsonObject toJson() {
        JsonObject workers = new JsonObject();
        JsonObject launchWorkers = new JsonObject();
        JsonObject terminateWorkers = new JsonObject();

        JsonArray launchIdleWorkers = new JsonArray();
        JsonArray launchCanceledWorkers = new JsonArray();
        JsonArray terminateLaunchingWorkers = new JsonArray();
        JsonArray terminateRunningWorkers = new JsonArray();
        this.relaunchWaitingIdleToTerminateWorkers.forEach(n -> launchIdleWorkers.add(n.toJson()));
        this.relaunchCanceledWorkers.forEach(n -> launchCanceledWorkers.add(n.toJson()));
        this.terminateLaunchingWorkers.forEach(n -> terminateLaunchingWorkers.add(n.toJson()));
        this.workersToTerminate.forEach(n -> terminateRunningWorkers.add(n.toJson()));

        launchWorkers.addProperty("total", this.numWorkersToLaunch + this.relaunchWaitingIdleToTerminateWorkers.size());
        launchWorkers.add("waitingIdleToTerminateWorkers", launchIdleWorkers);
        launchWorkers.add("canceledWorkers", launchCanceledWorkers);
        launchWorkers.addProperty("newWorkers", numWorkersToLaunch);

        terminateWorkers.addProperty("total", this.workersToTerminate.size() + this.terminateLaunchingWorkers.size());
        terminateWorkers.add("launchingWorkers", terminateLaunchingWorkers);
        terminateWorkers.add("runningWorkers", terminateRunningWorkers);

        workers.add("launch", launchWorkers);
        workers.add("terminate", terminateWorkers);

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
                ", relaunchWaitingIdleToTerminateWorkers=" + relaunchWaitingIdleToTerminateWorkers +
                ", relaunchCanceledWorkers=" + relaunchCanceledWorkers +
                ", terminateLaunchingWorkers=" + terminateLaunchingWorkers +
                ", workersToTerminate=" + workersToTerminate + "]";
    }



}


