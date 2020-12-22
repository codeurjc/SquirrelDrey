package es.codeurjc.squirrel.drey.local.simulator;

import es.codeurjc.squirrel.drey.local.simulator.core.Simulation;

public class ScenarySimulatorApp {

    public static void main(String[] args) {
        try {
            Simulation simulation = new Simulation();
            SimulationResult simulationResult = simulation.getSimulationResult();
            ChartGenerator chartGenerator = new ChartGenerator();
            chartGenerator.saveSimulationResult(simulationResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
