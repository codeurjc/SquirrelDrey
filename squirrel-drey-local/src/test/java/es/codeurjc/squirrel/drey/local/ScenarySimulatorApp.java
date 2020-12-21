package es.codeurjc.squirrel.drey.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class ScenarySimulatorApp {

    public static void main(String[] args) {
        SimulationResult simulationResult = new SimulationResult();
        simulationResult.getLabelsList().addAll(new ArrayList<>(Arrays.asList("00:00:30", "00:01:00", "00:01:30", "00:02:00")));
        simulationResult.getHighPriorityTasks().addAll(new ArrayList<>(Arrays.asList(10, 12, 7, 9)));
        simulationResult.getLowPriorityTasks().addAll(new ArrayList<>(Arrays.asList(1, 2, 1, 2)));
        simulationResult.getTotalTasks().addAll(new ArrayList<>(Arrays.asList(11, 14, 8, 11)));
        simulationResult.getRunningWorkers().addAll(new ArrayList<>(Arrays.asList(1, 1, 2, 1)));
        simulationResult.getLaunchingWorkers().addAll(new ArrayList<>(Arrays.asList(1, 1, 0, 0)));
        simulationResult.getTerminatingWorkers().addAll(new ArrayList<>(Arrays.asList(0, 0, 0, 1)));
        simulationResult.getDisconnectedWorkers().addAll(new ArrayList<>(Arrays.asList(0, 0, 0, 0)));
        simulationResult.getIdleWorkers().addAll(new ArrayList<>(Arrays.asList(0, 0, 0, 0)));
        simulationResult.getMinWorkersList().addAll(new ArrayList<>(Arrays.asList(1, 1, 1, 1)));
        simulationResult.getMaxWorkersList().addAll(new ArrayList<>(Arrays.asList(3, 3, 3, 3)));
        simulationResult.getMinIdleWorkers().addAll(new ArrayList<>(Arrays.asList(1, 1, 1, 1)));
        ChartGenerator chartGenerator = new ChartGenerator();
        try {
            chartGenerator.saveSimulationResult(simulationResult);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
