package es.codeurjc.squirrel.drey.local.autoscaling;

import es.codeurjc.squirrel.drey.local.SQSConnectorMaster;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class monitor state of workers. The functions of this scheduled class are:
 * 1. Remove non working sqs urls to not send pings after first failure.
 * 2. Autoscale cluster if autoscaling is enabled
 */
public class ClusterMonitoring {

    private int DEFAULT_MONITORING_PERIOD = 10;

    private boolean autoscaling = false;
    private int monitoringPeriod;
    private SQSConnectorMaster sqsMaster;
    private ScheduledExecutorService monitoringClusterSchedule; // Local scheduled executor for running listener thread

    public ClusterMonitoring(SQSConnectorMaster sqsMaster) {
        this.monitoringPeriod = System.getProperty("monitoring-period") != null
                ? Integer.parseInt(System.getProperty("monitoring-period"))
                : DEFAULT_MONITORING_PERIOD;

        this.autoscaling = System.getProperty("enable-autoscaling") != null
                || Boolean.parseBoolean(System.getProperty("enable-autoscaling"));

        this.sqsMaster = sqsMaster;
        this.monitoringClusterSchedule = Executors.newScheduledThreadPool(1);
    }

    public void startMonitoring() {
        this.monitoringClusterSchedule.scheduleAtFixedRate(() -> {
            // TODO
        }, 0, monitoringPeriod, TimeUnit.SECONDS);
    }



}
