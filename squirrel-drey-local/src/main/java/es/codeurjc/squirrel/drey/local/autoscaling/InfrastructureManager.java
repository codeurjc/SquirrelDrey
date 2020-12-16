package es.codeurjc.squirrel.drey.local.autoscaling;

import es.codeurjc.squirrel.drey.local.AlgorithmManager;
import es.codeurjc.squirrel.drey.local.WorkerStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class monitor state of workers. The functions of this scheduled class are:
 * 1. Remove non working sqs urls to not send pings after first failure.
 * 2. Autoscale cluster if autoscaling is enabled
 */
public class InfrastructureManager<R extends Serializable> {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureManager.class);

    private int DEFAULT_MONITORING_PERIOD = 10;
    private int MAX_TIME_FETCH_WORKERS = 5;

    private AlgorithmManager<R> algorithmManager;
    private ReentrantLock sharedInfrastructureManagerLock;

    private boolean autoscaling;
    private int monitoringPeriod;

    private Map<String, WorkerStats> workers;
    private ScheduledExecutorService monitoringClusterSchedule;

    public InfrastructureManager(AlgorithmManager<R> algorithmManager, ReentrantLock sharedInfrastructureManagerLock) {
        this.sharedInfrastructureManagerLock = sharedInfrastructureManagerLock;
        this.algorithmManager = algorithmManager;
        this.monitoringPeriod = System.getProperty("monitoring-period") != null
                ? Integer.parseInt(System.getProperty("monitoring-period"))
                : DEFAULT_MONITORING_PERIOD;

        this.autoscaling = System.getProperty("enable-autoscaling") != null
                || Boolean.parseBoolean(System.getProperty("enable-autoscaling"));

        this.workers = new ConcurrentHashMap<>();
        this.monitoringClusterSchedule = Executors.newScheduledThreadPool(1);
        this.startMonitoring();
    }

    public void launchWorker(boolean async) throws Exception {
        // TODO Implement
        throw new Exception("Not implemented");
    }

    public void removeWorker(WorkerStats workerStats, boolean async) throws Exception {
        // TODO implement
        throw new Exception("Not implemented");
    }

    private void startMonitoring() {
        this.monitoringClusterSchedule.scheduleAtFixedRate(() -> {
            try {
                if (this.algorithmManager.getSqsMaster() != null) {
                    this.algorithmManager.fetchInfrastructureWorkers(MAX_TIME_FETCH_WORKERS);
                    this.workers.values().forEach(workerStats -> log.info(workerStats.toString()));
                }
            } catch (Exception e) {
                log.warn("Some workers are not responding: {}", e.getMessage());
                this.workers.values().forEach(workerStats -> log.warn(workerStats.toString()));
                e.printStackTrace();
            }
        }, 0, monitoringPeriod, TimeUnit.SECONDS);
    }

    /**
     * Every access to this method is protected with {@link InfrastructureManager#sharedInfrastructureManagerLock}
     */
    public Map<String, WorkerStats> getWorkers() {
        return this.workers;
    }


}
