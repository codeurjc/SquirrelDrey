package es.codeurjc.squirrel.drey.local;

import es.codeurjc.squirrel.drey.local.autoscaling.AutoScalingException;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingManager;
import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
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
    private int MAX_TIME_FETCH_WORKERS = 10;

    private AlgorithmManager<R> algorithmManager;
    private AutoscalingManager autoscalingManager;

    private boolean autoscaling;
    private int monitoringPeriod;

    private Map<String, WorkerStats> workers;
    private ScheduledExecutorService monitoringClusterSchedule;

    public InfrastructureManager(AlgorithmManager<R> algorithmManager, ReentrantLock sharedInfrastructureManagerLock) {
        this.algorithmManager = algorithmManager;
        this.monitoringPeriod = System.getProperty("monitoring-period") != null
                ? Integer.parseInt(System.getProperty("monitoring-period"))
                : DEFAULT_MONITORING_PERIOD;

        this.autoscaling = System.getProperty("enable-autoscaling") != null
                || Boolean.parseBoolean(System.getProperty("enable-autoscaling"));

        this.autoscalingManager = new AutoscalingManager();

        this.workers = new ConcurrentHashMap<>();
        this.monitoringClusterSchedule = Executors.newScheduledThreadPool(1);
        this.startMonitoring();
    }

    public void launchWorker(boolean async) {
        // TODO
    }

    public void removeWorker(String workerId, boolean whenNotRunningTasks, boolean async) {
        // TODO
    }

    private void startMonitoring() {
        this.monitoringClusterSchedule.scheduleAtFixedRate(() -> {
            try {
                Map<String, Long> mapWorkerIdLastTimeFetched = new HashMap<>();
                this.workers.values().forEach(w -> mapWorkerIdLastTimeFetched.put(w.getWorkerId(), w.getLastTimeFetched()));
                try {
                    if (this.algorithmManager.sqsMaster != null) {
                        // Take account of updated workers
                        long currentTime = System.currentTimeMillis();
                        log.info("Monitoring workers");

                        this.algorithmManager.fetchInfrastructureWorkers(MAX_TIME_FETCH_WORKERS);

                        this.workers.values().stream()
                                .filter(w -> !w.isDisconnected)
                                .forEach(workerStats -> log.warn(workerStats.toString()));

                        if (this.autoscaling) {
                            // AUTOSCALING
                            // AutoscalingResult autoscalingResult = this.autoscalingManager.evalAutoscaling(config, status);
                            // this.applyAutoscalingResult(autoscalingResult)
                        }

                        double secondsExecuting = (double) (System.currentTimeMillis() - currentTime) / 1000;
                        log.info("Execution time of monitoring: {} seconds", secondsExecuting);
                    }
                } catch (Exception e) {
                    log.warn("Some workers are not responding: {}", e.getMessage());
                    // Check not updated workers
                    for(Map.Entry<String, Long> prevWorkerData: mapWorkerIdLastTimeFetched.entrySet()) {
                        String workerId = prevWorkerData.getKey();
                        long prevLastTimeFetched = prevWorkerData.getValue();
                        WorkerStats actualWorkerStats = workers.get(workerId);
                        long actualTimeFetched = workers.get(workerId).getLastTimeFetched();

                        if (prevLastTimeFetched == actualTimeFetched) {
                            // If Previous last time fetch and actual time fecth are equals
                            // the worker did not answered
                            log.warn("Worker not fetched {}:", actualWorkerStats);
                            actualWorkerStats.setDisconnected(true);
                        }
                    }

                    this.workers.values().stream()
                            .filter(w -> !w.isDisconnected)
                            .forEach(workerStats -> log.warn(workerStats.toString()));

                    e.printStackTrace();
                }
            } catch (Exception e) {
                log.error("An exception ocurred during monitoring");
                e.printStackTrace();
            }

        }, 0, monitoringPeriod, TimeUnit.SECONDS);
    }

    private void applyAutoscalingResult(AutoscalingResult result) {
        if (!result.isDoNothing()) {
            // Terminate Workers
            result.getWorkersToTerminate().forEach(node -> {
                removeWorker(node.workerId, true, true);
            });

            // Launch new Media Nodes only in auto cluster mode
            for (int i = 0; i < result.getNumWorkersToLaunch(); i++) {
                launchWorker(true);
            }
        }
    }

    public Map<String, WorkerStats> getWorkers() {
        return this.workers;
    }


}
