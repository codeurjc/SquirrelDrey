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
import java.util.stream.Collectors;

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

    private Map<String, WorkerStats> workers;
    private ScheduledExecutorService monitoringClusterSchedule;
    private ScheduledFuture<?> monitoringClusterScheduleManager;

    private InfrastructureStats infrastructureStats;

    private int currentParallelizationGrade;

    public InfrastructureManager(Config config, AlgorithmManager<R> algorithmManager, ReentrantLock sharedInfrastructureManagerLock) {
        // Load object dependencies
        this.config = config;
        this.algorithmManager = algorithmManager;
        this.autoscalingManager = new AutoscalingManager();

        // Load properties
        this.autoscaling = this.config.isAutoscalingEnabled();

        this.workers = new ConcurrentHashMap<>();
        this.monitoringClusterSchedule = Executors.newScheduledThreadPool(1);

        this.infrastructureStats = new InfrastructureStats(config.getAutoscalingConfig(), getSystemStatus(), new ArrayList<>(workers.values()));
        this.currentParallelizationGrade = config.getParallelizationGrade();
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
        monitoringClusterScheduleManager = this.monitoringClusterSchedule.scheduleWithFixedDelay(() -> {
            try {
                if (this.config.isMonitoringEnabled()) {
                    Map<String, Long> mapWorkerIdLastTimeFetched = new HashMap<>();
                    this.workers.values().forEach(w -> mapWorkerIdLastTimeFetched.put(w.getWorkerId(), w.getLastTimeFetched()));
                    try {
                        if (this.algorithmManager.sqsMaster != null) {
                            // Take account of updated workers
                            long currentTime = System.currentTimeMillis();
                            log.info("\n ====================\n Monitoring workers \n ===================");
                            this.algorithmManager.fetchWorkersInfrastructure(config.getMaxTimeOutFetchWorkers());

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

                            // Check parallelization grade
                            updateParallelizationGrade();

                            this.infrastructureStats = new InfrastructureStats(config.getAutoscalingConfig(), getSystemStatus(), new ArrayList<>(workers.values()));

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

        }, 0, config.getMonitoringPeriod(), TimeUnit.SECONDS);
    }

    private void applyAutoscalingResult(AutoscalingResult result) throws IOException, TimeoutException {


        // Remove workers
        if (!result.isDoNothing()) {
            log.info("Applying autoscaling");
            //Disconnected workers to remove
            if (result.getWorkersDisconnectedToTerminate().size() > 0) {
                result.getWorkersDisconnectedToTerminate().forEach(worker -> {
                    removeWorker(worker, true);
                });
            }

            if (result.getWorkersToTerminate().size() > 0) {
                log.info("Autoscaling: Instances candidates to remove: {}", result.getWorkersToTerminate());
                List<WorkerStats> workersToRemoveGracefully = this.workersToRemoveGracefully(result.getWorkersToTerminate());

                log.info("Autoscaling: Instances to remove gracefully: {}", workersToRemoveGracefully.size());
                workersToRemoveGracefully.forEach(worker -> {
                    removeWorker(worker, true);
                });
            }

            log.info("Autoscaling: Instances to launch: {}", result.getNumWorkersToLaunch());
            // Launch Workersç
            if (result.getNumWorkersToLaunch() > 0) {
                for (int i = 0; i < result.getNumWorkersToLaunch(); i++) {
                    launchWorker(true);
                }
            }

        } else {
            log.info("Autoscaling has nothing to do");
        }
    }

    public Map<String, WorkerStats> getWorkers() {
        return this.workers;
    }

    private List<WorkerStats> workersToRemoveGracefully(List<WorkerStats> workers) throws IOException {
        long currentTime = System.currentTimeMillis();
        log.info("Checking workers to remove gracefully");
        List<WorkerStats> workersToRemoveGracefully = new ArrayList<>();
        try {
            // Disable input of candidates workers and wait
            this.algorithmManager.disableInputWorkers(workers, config.getMaxTimeOutFetchWorkers());
        } catch (Exception e) {
            log.error("Exception: '{}', while disabling Input for workers: {}", e.getMessage(), workers);
            log.info("Enabling input again because of exception");
            this.algorithmManager.enableInputWorkers(workers);
            return workersToRemoveGracefully;
        }

        try {
            log.info("Fetching workers after disabling input for workers: {}", workers);
            // Fetch all workers to know exactly what workers can be removed
            this.algorithmManager.fetchWorkersInfrastructure(config.getMaxTimeOutFetchWorkers());
        } catch (Exception e) {
            log.error("Some workers can't be fetched after disabling input. Exception: {}", e.getMessage());
            log.info("Enabling input again because of exception");
            this.algorithmManager.enableInputWorkers(workers);
            return workersToRemoveGracefully;
        }

        // Return workers which can be deleted gracefully
        for (WorkerStats workerStats: workers) {
            if (workerStats.getTasksRunning() == 0) {
                log.info("Worker {} can be deleted safely with {} running tasks", workerStats.getWorkerId(), workerStats.getTasksRunning());
                workersToRemoveGracefully.add(workerStats);
            } else {
                log.info("Enabling again input for worker {} which can't be deleted safely with {} running Tasks", workerStats.getWorkerId(), workerStats.getTasksRunning());
                this.algorithmManager.enableInputWorkers(workers);
            }
        }
        double secondsExecuting = (double) (System.currentTimeMillis() - currentTime) / 1000;
        log.info("Workers to remove safely checked in {} seconds ", secondsExecuting);
        return workersToRemoveGracefully;
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
                    0, 0, 0, 0, WorkerStatus.launching);
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
        int numHighPriorityQueueMessages;
        int numLowPriorityQueueMessages;
        try {
            numHighPriorityQueueMessages = this.algorithmManager.sqsMaster.getNumMessagesHighPriorityQueue();
            numLowPriorityQueueMessages = this.algorithmManager.sqsMaster.getNumMessagesLowPriorityQueue();
        } catch (Exception e) {
            numHighPriorityQueueMessages = 0;
            numLowPriorityQueueMessages = 0;
        }

        List<WorkerStats> workerStats = new ArrayList<>(workers.values());
        return new SystemStatus(numHighPriorityQueueMessages, numLowPriorityQueueMessages, workerStats);
    }

    private void updateParallelizationGrade() throws IOException {
        this.currentParallelizationGrade = config.getParallelizationGrade();
        List<WorkerStats> workerStatsToUpdate = this.workers.values().stream()
                .filter(w -> !w.isDisconnected)
                .filter(w -> w.status == WorkerStatus.running)
                .filter(w -> w.getParallelizationGrade() != this.currentParallelizationGrade)
                .collect(Collectors.toList());

        for (WorkerStats workerStats: workerStatsToUpdate) {
            this.algorithmManager.sqsMaster.updateParallelizationGradeOfWorker(workerStats);
        }
    }

    public InfrastructureStats getInfrastructureStats() {
        return infrastructureStats;
    }

    public int getCurrentParallelizationGrade() {
        return currentParallelizationGrade;
    }

    public void restartMonitoring(){
        //change to hourly update
        if (monitoringClusterScheduleManager != null) {
            log.info("Updating monitoring scheduler...");
            monitoringClusterScheduleManager.cancel(true);
            startMonitoring();
        }

    }


}
