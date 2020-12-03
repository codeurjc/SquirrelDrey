package es.codeurjc.squirrel.drey.local.autoscaling;

import es.codeurjc.squirrel.drey.local.SQSConnectorMaster;
import es.codeurjc.squirrel.drey.local.WorkerStats;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class monitor state of workers. The functions of this scheduled class are:
 * 1. Remove non working sqs urls to not send pings after first failure.
 * 2. Autoscale cluster if autoscaling is enabled
 */
public class InfrastructureManager<R extends Serializable> {

    private int DEFAULT_MONITORING_PERIOD = 10;

    private boolean autoscaling;
    private int monitoringPeriod;

    private SQSConnectorMaster<R> sqsMaster;
    private Map<String, WorkerStats> workers;

    private ScheduledExecutorService monitoringClusterSchedule;

    public InfrastructureManager(SQSConnectorMaster<R> sqsMaster) {
        this.monitoringPeriod = System.getProperty("monitoring-period") != null
                ? Integer.parseInt(System.getProperty("monitoring-period"))
                : DEFAULT_MONITORING_PERIOD;

        this.autoscaling = System.getProperty("enable-autoscaling") != null
                || Boolean.parseBoolean(System.getProperty("enable-autoscaling"));

        this.workers = new ConcurrentHashMap<>();
        this.sqsMaster = sqsMaster;
        this.monitoringClusterSchedule = Executors.newScheduledThreadPool(1);
    }

    public void launchWorker(boolean async) {
        // TODO Implement
        throw new NotImplementedException();
    }

    public void removeWorker(WorkerStats workerStats, boolean async) {
        // TODO implement
        throw new NotImplementedException();
    }

    public void startMonitoring() {
        this.monitoringClusterSchedule.scheduleAtFixedRate(() -> {
            // TODO
        }, 0, monitoringPeriod, TimeUnit.SECONDS);
    }

    public Map<String, WorkerStats> getWorkers() {
        return this.workers;
    }



}
