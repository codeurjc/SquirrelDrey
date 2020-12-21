package es.codeurjc.squirrel.drey.local.simulator;

import es.codeurjc.squirrel.drey.local.simulator.core.Simulation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class ScenarySimulatorApp {

    public static void main(String[] args) {
        try {
            Simulation simulation = new Simulation();
            simulation.simulate();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
