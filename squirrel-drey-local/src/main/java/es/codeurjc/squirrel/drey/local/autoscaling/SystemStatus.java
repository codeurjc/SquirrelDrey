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
    private final List<WorkerStats> terminatingWorkers;

    public SystemStatus(int numHighQueueMessages, int numLowPriorityMessages, List<WorkerStats> workers) {
        this.numHighPriorityMessages = numHighQueueMessages;
        this.numLowPriorityMessages = numLowPriorityMessages;
        this.runningWorkers = filterWorkersWithStatus(workers, WorkerStatus.running);
        this.launchingWorkers = filterWorkersWithStatus(workers, WorkerStatus.launching);
        this.terminatingWorkers = filterWorkersWithStatus(workers, WorkerStatus.terminating);
        this.numWorkers = getLaunchingWorkers().size() + getRunningWorkers().size();
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

    public List<WorkerStats> getLaunchingWorkers() {
        return launchingWorkers;
    }

    public List<WorkerStats> getTerminatingWorkers() {
        return terminatingWorkers;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("numHighPriorityMessages", numHighPriorityMessages);
        json.addProperty("numLowPriorityMessages", numLowPriorityMessages);
        json.addProperty("numWorkers", numWorkers);

        JsonArray jsonRunningWorkers = new JsonArray();
        JsonArray jsonLaunchingWorkers = new JsonArray();
        JsonArray jsonTerminatingWorkers = new JsonArray();

        this.runningWorkers.forEach(n -> jsonRunningWorkers.add(n.toJson()));
        this.launchingWorkers.forEach(n -> jsonLaunchingWorkers.add(n.toJson()));
        this.terminatingWorkers.forEach(n -> jsonTerminatingWorkers.add(n.toJson()));

        json.add("runningWorkers", jsonRunningWorkers);
        json.add("launchingWorkers", jsonLaunchingWorkers);
        json.add("terminatingWorkers", jsonTerminatingWorkers);

        return json;
    }

    @Override
    public String toString() {
        return "SystemStatus [" +
                "numHighPriorityMessages=" + numHighPriorityMessages +
                ", numLowPriorityMessages=" + numLowPriorityMessages +
                ", numWorkers=" + numWorkers +
                ", runningWorkers=" + runningWorkers +
                ", launchingWorkers=" + launchingWorkers;
    }
}
