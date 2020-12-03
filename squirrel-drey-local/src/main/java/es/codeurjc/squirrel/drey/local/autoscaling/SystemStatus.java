package es.codeurjc.squirrel.drey.local.autoscaling;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import es.codeurjc.squirrel.drey.local.WorkerStats;
import es.codeurjc.squirrel.drey.local.WorkerStatus;

import java.util.List;
import java.util.stream.Collectors;

public class SystemStatus {

    private final int numHighPriorityMessages;
    private final int numLowPriorityMessages;
    private final int numWorkers;
    private final List<WorkerStats> runningWorkers;
    private final List<WorkerStats> launchingWorkers;
    private final List<WorkerStats> waitingIdleToTerminateWorkers;
    private final List<WorkerStats> canceledWorkers;

    public SystemStatus(int numQueueMessages, int numLowPriorityMessages, List<WorkerStats> workers) {
        this.numHighPriorityMessages = numQueueMessages;
        this.numLowPriorityMessages = numLowPriorityMessages;
        this.numWorkers = getLaunchingWorkers().size() + getRunningWorkers().size();
        this.runningWorkers = filterWorkersWithStatus(workers, WorkerStatus.running);
        this.launchingWorkers = filterWorkersWithStatus(workers, WorkerStatus.launching);
        this.waitingIdleToTerminateWorkers = filterWorkersWithStatus(workers, WorkerStatus.waitingIdleToTerminate);
        this.canceledWorkers = filterWorkersWithStatus(workers, WorkerStatus.canceled);
    }

    private List<WorkerStats> filterWorkersWithStatus(List<WorkerStats> workers, WorkerStatus status) {
        return workers.stream().filter(w -> w.getStatus() == status).collect(Collectors.toList());
    }

    public int getNumHighPriorityMessages() {
        return numHighPriorityMessages;
    }

    public int getNumLowPriorityMessages() {
        return numLowPriorityMessages;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public List<WorkerStats> getRunningWorkers() {
        return runningWorkers;
    }

    public boolean hasLaunchingWorkers() {
        return launchingWorkers != null && !launchingWorkers.isEmpty();
    }

    public List<WorkerStats> getLaunchingWorkers() {
        return launchingWorkers;
    }

    public boolean hasWaitingIdleToTerminateWorkers() {
        return waitingIdleToTerminateWorkers != null && !waitingIdleToTerminateWorkers.isEmpty();
    }

    public List<WorkerStats> getWaitingIdleToTerminateWorkers() {
        return waitingIdleToTerminateWorkers;
    }

    public boolean hasCanceledWorkers() {
        return canceledWorkers != null && !canceledWorkers.isEmpty();
    }

    public List<WorkerStats> getCanceledWorkers() {
        return canceledWorkers;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("numHighPriorityMessages", numHighPriorityMessages);
        json.addProperty("numLowPriorityMessages", numLowPriorityMessages);
        json.addProperty("numWorkers", numWorkers);

        JsonArray jsonRunningWorkers = new JsonArray();
        JsonArray jsonLaunchingWorkers = new JsonArray();
        JsonArray jsonWaitingIdleToTerminateWorkers = new JsonArray();
        JsonArray jsonCanceledWorkers = new JsonArray();

        this.runningWorkers.forEach(n -> jsonRunningWorkers.add(n.toJson()));
        this.launchingWorkers.forEach(n -> jsonLaunchingWorkers.add(n.toJson()));
        this.waitingIdleToTerminateWorkers.forEach(n -> jsonWaitingIdleToTerminateWorkers.add(n.toJson()));
        this.canceledWorkers.forEach(n -> jsonCanceledWorkers.add(n.toJson()));

        json.add("runningWorkers", jsonRunningWorkers);
        json.add("launchingWorkers", jsonLaunchingWorkers);
        json.add("waitingIdleToTerminateWorkers", jsonWaitingIdleToTerminateWorkers);
        json.add("canceledWorkers", jsonCanceledWorkers);

        return json;
    }

    @Override
    public String toString() {
        return "SystemStatus [" +
                "numHighPriorityMessages=" + numHighPriorityMessages +
                ", numLowPriorityMessages=" + numLowPriorityMessages +
                ", numWorkers=" + numWorkers +
                ", runningWorkers=" + runningWorkers +
                ", launchingWorkers=" + launchingWorkers +
                ", waitingIdleToTerminateWorkers=" + waitingIdleToTerminateWorkers +
                ", canceledWorkers=" + canceledWorkers + "]";
    }
}
