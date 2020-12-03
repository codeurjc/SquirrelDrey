package es.codeurjc.squirrel.drey.local.autoscaling;

import es.codeurjc.squirrel.drey.local.WorkerStats;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Map;

/**
 * Implement necessary logic to launch and remove workers from ECS Cluster
 */
public class InfrastructureManager {

    Map<String, WorkerStats> workers;

    public void launchWorker(boolean async) {
        // TODO Implement
        throw new NotImplementedException();
    }

    public void removeWorker(WorkerStats workerStats, boolean async) {
        // TODO implement
        throw new NotImplementedException();
    }


}
