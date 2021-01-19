package es.codeurjc.squirrel.drey.local;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import es.codeurjc.squirrel.drey.local.autoscaling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private Config config;
    private AlgorithmManager<R> algorithmManager;

    private AutoscalingManager autoscalingManager;
    private boolean autoscaling;
    private int monitoringPeriod;
    private int maxTimeOutFetchWorkers;

    private Map<String, WorkerStats> workers;
    private ScheduledExecutorService monitoringClusterSchedule;

    public InfrastructureManager(Config config, AlgorithmManager<R> algorithmManager, ReentrantLock sharedInfrastructureManagerLock) {
        // Load object dependencies
        this.config = config;
        this.algorithmManager = algorithmManager;
        this.autoscalingManager = new AutoscalingManager();

        // Load properties
        this.monitoringPeriod = this.config.getMonitoringPeriod();
        this.autoscaling = this.config.isAutoscalingEnabled();
        this.maxTimeOutFetchWorkers = this.config.getMaxTimeOutFetchWorkers();

        this.workers = new ConcurrentHashMap<>();
        this.monitoringClusterSchedule = Executors.newScheduledThreadPool(1);

        this.startMonitoring();
    }

    public void launchWorker(boolean async) {
        if (async) {
            new Thread(() -> {
                launchWorkerAux(config.getAwsWorkerLaunchTemplate(), config.getAwsSubnetId(), config.getAwsRegion());
            }).start();
        } else {
            launchWorkerAux(config.getAwsWorkerLaunchTemplate(), config.getAwsSubnetId(), config.getAwsRegion());
        }
    }

    public void removeWorker(WorkerStats workerStats, boolean async) {
        if (async) {
            new Thread(() -> {
                removeWorkerAux(workerStats, config.getAwsRegion());
            }).start();
        } else {
            removeWorkerAux(workerStats, config.getAwsRegion());
        }
    }

    private void startMonitoring() {
        this.monitoringClusterSchedule.scheduleAtFixedRate(() -> {
            try {
                if (this.config.isMonitoringEnabled()) {
                    try {
                        if (workers.size() == 0) {
                            // Initial state
                            for(int i = 0; i < config.getAutoscalingConfig().getMinWorkers(); i++) {
                                launchWorker(false);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error launching initial workers...");
                        e.printStackTrace();
                    }
                    Map<String, Long> mapWorkerIdLastTimeFetched = new HashMap<>();
                    this.workers.values().forEach(w -> mapWorkerIdLastTimeFetched.put(w.getWorkerId(), w.getLastTimeFetched()));
                    try {
                        if (this.algorithmManager.sqsMaster != null) {
                            // Take account of updated workers
                            long currentTime = System.currentTimeMillis();
                            log.info("\n ====================\n Monitoring workers \n ===================");
                            this.algorithmManager.fetchInfrastructureWorkers(maxTimeOutFetchWorkers);

                            this.workers.values().stream()
                                    .filter(w -> !w.isDisconnected)
                                    .forEach(workerStats -> log.info(workerStats.toString()));

                            this.workers.values().stream()
                                    .filter(w -> w.isDisconnected)
                                    .forEach(workerStats -> log.warn("DISCONNECTED: {}", workerStats.toString()));

                            if (this.autoscaling) {
                                SystemStatus systemStatus = this.getSystemStatus();
                                AutoscalingConfig asConfig = this.config.getAutoscalingConfig();
                                log.info("Evaluating autoscaling...");
                                AutoscalingResult autoscalingResult = autoscalingManager.evalAutoscaling(asConfig, systemStatus);
                                log.info("Autoscaling result: {}", autoscalingResult.toString());
                                applyAutoscalingResult(autoscalingResult);
                            }

                            double secondsExecuting = (double) (System.currentTimeMillis() - currentTime) / 1000;
                            log.info("\n ====================\n Execution time of monitoring: {} seconds \n ===================", secondsExecuting);
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
                }
            } catch (Exception e) {
                log.error("An exception ocurred during monitoring");
                e.printStackTrace();
            }

        }, 0, monitoringPeriod, TimeUnit.SECONDS);
    }

    private void applyAutoscalingResult(AutoscalingResult result) throws IOException, TimeoutException {

        log.info("Applying autoscaling");
        // Remove workers
        if (!result.isDoNothing()) {
            log.info("Autoscaling: Instances to remove: {}", result.getWorkersToTerminate().size());
            result.getWorkersToTerminate().forEach(worker -> {
                removeWorker(worker, true);
            });

            log.info("Autoscaling: Instances to launch: {}", result.getNumWorkersToLaunch());
            // Launch Workers
            for (int i = 0; i < result.getNumWorkersToLaunch(); i++) {
                launchWorker(true);
            }
        }
    }

    public Map<String, WorkerStats> getWorkers() {
        return this.workers;
    }

    private void launchWorkerAux(String launchTemplateId, String subnetId, String awsRegion) {
        try {
            ClientConfiguration clientConfiguration = new ClientConfiguration()
                    .withConnectionTimeout(Config.DEFAULT_AWS_SDK_HTTP_TIMEOUT * 1000)
                    .withClientExecutionTimeout(Config.DEFAULT_AWS_SDK_HTTP_TIMEOUT * 1000);

            AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                    .withClientConfiguration(clientConfiguration)
                    .withRegion(awsRegion).build();

            LaunchTemplateSpecification launchTemplate = new LaunchTemplateSpecification()
                    .withLaunchTemplateId(launchTemplateId);
            RunInstancesRequest runInstanceRequest = new RunInstancesRequest()
                    .withLaunchTemplate(launchTemplate)
                    .withSubnetId(subnetId)
                    .withMaxCount(1)
                    .withMinCount(1);

            RunInstancesResult run_response = ec2.runInstances(runInstanceRequest);

            String ec2InstanceId = run_response.getReservation().getInstances().get(0).getInstanceId();

            Long currentTime = System.currentTimeMillis();
            WorkerStats workerStats = new WorkerStats(currentTime, ec2InstanceId, 0, ec2InstanceId, null, 0, 0,
                    0, 0, 0, WorkerStatus.launching);
            workerStats.setLastTimeFetched(currentTime);
            workerStats.setLastTimeWorking(currentTime);

            log.info("Launching worker: {}", workerStats.toString());

            try {
                this.algorithmManager.sharedInfrastructureManagerLock.lock();
                this.workers.put(workerStats.getWorkerId(), workerStats);
            } finally {
                this.algorithmManager.sharedInfrastructureManagerLock.unlock();
            }
        } catch (Exception e) {
            log.error("Error while launching worker: {}", e.getMessage());
            e.printStackTrace();
        }

    }

    private void removeWorkerAux(WorkerStats workerStats, String awsRegion) {
        try {
            ClientConfiguration clientConfiguration = new ClientConfiguration()
                    .withConnectionTimeout(Config.DEFAULT_AWS_SDK_HTTP_TIMEOUT * 1000)
                    .withClientExecutionTimeout(Config.DEFAULT_AWS_SDK_HTTP_TIMEOUT * 1000);

            AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
                    .withClientConfiguration(clientConfiguration)
                    .withRegion(awsRegion).build();

            log.info("Terminating worker: {}", workerStats.toString());

            try {
                this.algorithmManager.sharedInfrastructureManagerLock.lock();
                this.workers.remove(workerStats.getWorkerId());
            } finally {
                this.algorithmManager.sharedInfrastructureManagerLock.unlock();
            }

            workerStats.setStatus(WorkerStatus.terminating);
            TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest().withInstanceIds(workerStats.getWorkerId());

            try {
                ec2.terminateInstances(terminateInstancesRequest);
            } catch (Exception e) {
                log.warn("Error deleting instance ({}): {}", workerStats.getWorkerId(), e.getMessage());
            }

            try {
                this.algorithmManager.sqsMaster.removeSqsQueue(workerStats.getDirectQueueUrl());
            } catch (Exception e) {
                log.warn("Error deleting sqs queue ({}): {}", workerStats.getDirectQueueUrl(), e.getMessage());
            }


        } catch (Exception e) {
            log.error("Error while removing worker: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private SystemStatus getSystemStatus() {
        int numHighPriorityQueueMessages = this.algorithmManager.sqsMaster.getNumMessagesHighPriorityQueue();
        int numLowPriorityQueueMessages = this.algorithmManager.sqsMaster.getNumMessagesLowPriorityQueue();
        List<WorkerStats> workerStats = new ArrayList<>(workers.values());
        return new SystemStatus(numHighPriorityQueueMessages, numLowPriorityQueueMessages, workerStats);
    }


}
