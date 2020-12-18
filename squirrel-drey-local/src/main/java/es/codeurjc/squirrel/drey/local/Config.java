package es.codeurjc.squirrel.drey.local;

import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingConfig;
import es.codeurjc.squirrel.drey.local.utils.EnvironmentIdGenerator;
import es.codeurjc.squirrel.drey.local.utils.EnvironmentIdGeneratorAws;
import es.codeurjc.squirrel.drey.local.utils.EnvironmentIdGeneratorDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    // Environment
    private final boolean devmode;

    // Aws variables
    private final String awsEnpointUrl;
    private final String awsRegion;

    // True if is worker
    private final boolean isMaster;

    // Num of parallel algorithms to run
    private final int parallelizationGrade;

    // Master and workers common queues
    private final String sqsInputQueueSuffix;
    private final String sqsLowPriorityQueueSuffix;
    private final String sqsOutputQueueSuffix;

    // Polling period after sqsMaxTimeout is reached
    // or succesfull message is received
    private final int sqsListenerPeriod;

    // Timeout for long polling in SQS queues
    private final int sqsMaxTimeout;

    // Direct queue workers
    private final String directQueueSuffix;

    // Num of iddle cores to handle sqs requests
    private final int idleCores;

    // Master Monitoring and Autoscaling
    private final boolean monitoringEnabled;
    private final boolean autoscalingEnabled;
    private final int monitoringPeriod;
    private final int maxTimeOutFetchWorkers;

    // Execution mode
    private final Mode mode;

    // Environment id
    private String environmentId;

    private AutoscalingConfig autoscalingConfig;

    public Config() {
        // Defaults
        int DEFAULT_PARALLELIZATION_GRADE = 1;
        int DEFAULT_SQS_LISTENER_PERIOD_MS = 250;
        int DEFAULT_SQS_MAX_TIMEOUT = 20;
        int DEFAULT_MONITORING_PERIOD = 10;
        int DEFAULT_MAX_TIMEOUT_FETCH_WORKERS = 10;

        // Parameters
        this.devmode = System.getProperty("devmode") != null && Boolean.parseBoolean(System.getProperty("devmode"));
        this.awsEnpointUrl = System.getProperty("endpoint-url");
        this.awsRegion = System.getProperty("aws-region") != null ? System.getProperty("aws-region") : "eu-west-1";
        this.isMaster = System.getProperty("worker") != null && !Boolean.parseBoolean(System.getProperty("worker"));
        this.parallelizationGrade = System.getProperty("parallelization-grade") != null ? Integer.parseInt(System.getProperty("parallelization-grade")) : DEFAULT_PARALLELIZATION_GRADE;
        this.sqsInputQueueSuffix = System.getProperty("input-queue-suffix");
        this.sqsLowPriorityQueueSuffix = System.getProperty("low-priority-input-queue-suffix");
        this.sqsOutputQueueSuffix = System.getProperty("output-queue-suffix");
        this.directQueueSuffix = System.getProperty("direct-queue-suffix");
        this.sqsListenerPeriod = System.getProperty("sqs-listener-period") != null ? Integer.parseInt(System.getProperty("sqs-listener-period")) : DEFAULT_SQS_LISTENER_PERIOD_MS;
        this.sqsMaxTimeout = System.getProperty("sqs-timeout") != null ? Integer.parseInt(System.getProperty("sqs-timeout")) : DEFAULT_SQS_MAX_TIMEOUT;
        this.monitoringEnabled = System.getProperty("enable-monitoring") == null || Boolean.parseBoolean(System.getProperty("enable-monitoring"));
        this.autoscalingEnabled = System.getProperty("enable-autoscaling") == null || Boolean.parseBoolean(System.getProperty("enable-autoscaling"));
        this.monitoringPeriod = System.getProperty("monitoring-period") != null ? Integer.parseInt(System.getProperty("monitoring-period")) : DEFAULT_MONITORING_PERIOD;
        this.maxTimeOutFetchWorkers = System.getProperty("monitoring-max-timeout-fetch-workers") != null ? Integer.parseInt(System.getProperty("monitoring-max-timeout-fetch-workers")) : DEFAULT_MAX_TIMEOUT_FETCH_WORKERS;
        this.mode = (System.getProperty("mode") != null) ? Mode.valueOf(System.getProperty("mode")) : Mode.RANDOM;
        String stringIdleCores = System.getProperty("idle-cores-app");
        if (stringIdleCores != null) {
            idleCores = Integer.parseInt(stringIdleCores);
        } else if (this.devmode) {
            idleCores = 0;
        } else {
            idleCores = 1; // 1 idle core for comunications with SQS
        }

        EnvironmentIdGenerator envIdGen;
        if (awsEnpointUrl != null || devmode) {
            envIdGen = new EnvironmentIdGeneratorDefault();
        } else {
            envIdGen = new EnvironmentIdGeneratorAws();
        }
        try {
            this.environmentId = envIdGen.generateEnvironmentId();
        } catch (Exception e) {
            log.error("Error getting environment id: {}", e.getMessage());
        }
        this.printStartingInfo();

        this.autoscalingConfig = loadAutoscalingConfig();
    }

    public boolean isDevmode() {
        return devmode;
    }

    public String getAwsEnpointUrl() {
        return awsEnpointUrl;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public boolean isMaster() {
        return isMaster;
    }

    public int getParallelizationGrade() {
        return parallelizationGrade;
    }

    public String getSqsInputQueueSuffix() {
        return sqsInputQueueSuffix;
    }

    public String getSqsLowPriorityQueueSuffix() {
        return sqsLowPriorityQueueSuffix;
    }

    public String getSqsOutputQueueSuffix() {
        return sqsOutputQueueSuffix;
    }

    public int getSqsListenerPeriod() {
        return sqsListenerPeriod;
    }

    public int getSqsMaxTimeout() {
        return sqsMaxTimeout;
    }

    public String getDirectQueueSuffix() {
        return directQueueSuffix;
    }

    public int getIdleCores() {
        return idleCores;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public boolean isAutoscalingEnabled() {
        return autoscalingEnabled;
    }

    public int getMonitoringPeriod() {
        return monitoringPeriod;
    }

    public int getMaxTimeOutFetchWorkers() {
        return maxTimeOutFetchWorkers;
    }

    public Mode getMode() {
        return mode;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    private AutoscalingConfig loadAutoscalingConfig() {
        AutoscalingConfig.Builder asBuilder = new AutoscalingConfig.Builder();
        if (System.getProperty("autoscaling-min-workers") != null) {
            asBuilder.minWorkers(Integer.parseInt(System.getProperty("autoscaling-min-workers")));
        }
        if (System.getProperty("autoscaling-max-workers") != null) {
            asBuilder.maxWorkers(Integer.parseInt(System.getProperty("autoscaling-max-workers")));
        }
        if (System.getProperty("autoscaling-min-idle-workers") != null) {
            asBuilder.minIdleWorkers(Integer.parseInt(System.getProperty("autoscaling-min-idle-workers")));
        }
        if (System.getProperty("autoscaling-max-parallelization") != null) {
            asBuilder.maxParalleization(Integer.parseInt(System.getProperty("autoscaling-max-parallelization")));
        }
        if (System.getProperty("autoscaling-workers-by-max-parallelization") != null) {
            asBuilder.workersByMaxParallelization(Integer.parseInt(System.getProperty("autoscaling-workers-by-max-parallelization")));
        }
        if (System.getProperty("autoscaling-max-seconds-idle") != null) {
            asBuilder.maxSecondsIdle(Integer.parseInt("autoscaling-max-seconds-idle"));
        }
        if (System.getProperty("autoscaling-max-seconds-non-responding") != null) {
            asBuilder.maxSecondsNonRespondingWorker(Integer.parseInt(System.getProperty("autoscaling-max-seconds-non-responding")));
        }
        return asBuilder.build();
    }

    private void printStartingInfo() {
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String message = "\n";
        message += "===========================\n";
        message += "Launching " + ((this.isMaster) ? "Master...\n" : "Worker...\n");
        message += "===========================\n";
        message += "STARTING AS: " + (this.isMaster ? "MASTER" : "WORKER") + "\n";
        message += "DEPLOYING AS: " + ((this.devmode) ? "DEV" : "PROD") + "\n";
        message += "AWS REGION: " + this.awsRegion + "\n";
        message += "MONITORING ENABLED: " + this.isMonitoringEnabled() + "\n";
        message += "AUTOSCALING ENABLED: " + this.isAutoscalingEnabled() + "\n";
        message += "Environment ID: " + ((this.environmentId != null ? this.environmentId : "none")) + "\n";
        if (!this.isMaster) {
            message += "PARALLELIZATION GRADE: " + this.parallelizationGrade + "\n";
        }
        message += "===========================\n";
        log.info(message);
    }

}
