package es.codeurjc.squirrel.drey.local.simulator;

import es.codeurjc.squirrel.drey.local.simulator.core.Simulation;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ScenarySimulatorApp {

    public static void main(String[] args) {
        Path resourceInputDirectory = Paths.get("src","test","resources", "input");
        Path resourceResultsDirectory = Paths.get("src", "test", "resources", "results");

        // Create simulation directories
        File resourceInputDirectoryFile = resourceInputDirectory.toFile();
        File resourceResultsDirectoryFile = resourceResultsDirectory.toFile();
        if (!resourceInputDirectoryFile.exists()) {
            resourceInputDirectoryFile.mkdirs();
        }
        if (!resourceResultsDirectoryFile.exists()) {
            resourceResultsDirectoryFile.mkdirs();
        }

        Map<String, String> inputSimulations = new HashMap<>();
        Arrays.stream(resourceInputDirectory.toFile().listFiles()).forEach(file -> {
            String simulationName = file.getName().substring(0, file.getName().lastIndexOf("."));
            String absolutePathInput = file.getAbsolutePath();
            inputSimulations.put(simulationName, absolutePathInput);
        });
        try {
            for(Map.Entry<String, String> inputSimulation: inputSimulations.entrySet()) {
                String simulationName = inputSimulation.getKey();
                String absolutePathInput = inputSimulation.getValue();
                String resultSimulationAbsolutePath = resourceResultsDirectory.toFile().getAbsolutePath() + "/" + simulationName + ".html";
                Simulation simulation = new Simulation(absolutePathInput);
                SimulationResult simulationResult = simulation.getSimulationResult();
                ChartGenerator chartGenerator = new ChartGenerator();

                // Check if output file exists
                File resultSimulationFile = new File(resultSimulationAbsolutePath);
                if (resultSimulationFile.exists()) {
                    resultSimulationFile.delete();
                }

                chartGenerator.saveSimulationResult(simulationResult, absolutePathInput, resultSimulationAbsolutePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
