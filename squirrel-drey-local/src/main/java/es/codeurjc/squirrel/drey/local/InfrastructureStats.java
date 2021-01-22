package es.codeurjc.squirrel.drey.local;

import es.codeurjc.squirrel.drey.local.autoscaling.AutoscalingConfig;
import es.codeurjc.squirrel.drey.local.autoscaling.SystemStatus;

import java.util.List;

public class InfrastructureStats {
    private AutoscalingConfig autoscalingConfig;
    private SystemStatus systemStatus;

    public InfrastructureStats(AutoscalingConfig autoscalingConfig, SystemStatus systemStatus, List<WorkerStats> workers) {
        this.autoscalingConfig = autoscalingConfig;
        this.systemStatus = systemStatus;
    }

    public AutoscalingConfig getAutoscalingConfig() {
        return autoscalingConfig;
    }

    public SystemStatus getSystemStatus() {
        return systemStatus;
    }
}
