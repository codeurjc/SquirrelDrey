package es.codeurjc.squirrel.drey.local.simulator;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class ChartGenerator {

    Path resourceDirectory = Paths.get("src","test","resources");
    private final String TEMPLATE_RESOURCE_FILE = "chart_template.html";
    private final String RESULT_DEFAULT_DIR = "results";

    public enum TemplateKeys {

        LABELS_LIST("labelsList"),
        HIGH_PRIORITY_TASK("highPriorityTasks"),
        LOW_PRIORITY_TASKS("lowPriorityTasks"),
        TOTAL_TASKS("totalTasks"),
        RUNNING_TASKS("runningTasks"),
        RUNNING_WORKERS("runningWorkers"),
        LAUNCHING_WORKERS("launchingWorkers"),
        TERMINATING_WORKERS("terminatingWorkers"),
        DISCONNECTED_WORKERS("disconnectedWorkers"),
        IDLE_WORKERS("idleWorkers"),
        MIN_WORKERS_LIST("minWorkersList"),
        MAX_WORKERS_LIST("maxWorkersList"),
        MIN_IDDLE_WORKERS("minIdleWorkers");

        private final int INDENTATION_VARIABLES = 8;
        private String varDeclaration;

        TemplateKeys(String varDeclaration) {
            this.varDeclaration = varDeclaration;
        }

        public String getJSCode(SimulationResult simulationResult) {
            List<?> resultList = simulationResult.getResultByTemplateKeys(this);
            if (this == TemplateKeys.LABELS_LIST) {
                String JSCode = "var " + this.varDeclaration + "=[";
                Iterator<?> iterator = resultList.iterator();
                while(iterator.hasNext()) {
                    JSCode += "\"" + iterator.next() + "\"" + (iterator.hasNext() ? "," : "");
                }
                JSCode += "];\n";
                return JSCode;
            }
            String result = "";
            for(int i = 0; i < INDENTATION_VARIABLES; i++) {
                result += " ";
            }
            result += "var " + this.varDeclaration + "=" + resultList.toString() + ";\n";
            return result;
        }
    }

    private String getResourceFileAsString(String fileName) throws IOException {
        String absolutePathToFile = resourceDirectory.toAbsolutePath() + "/" + fileName;
        File file = new File(absolutePathToFile);
        try (InputStream is = new FileInputStream(file)) {
            if (is == null) return null;
            try (InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

    private void writeStringAsFile(String text, String outputDir) throws IOException {
        if (outputDir == null) {
            // Search for non existing file if not specified output dir
            String prefixSimulationFileName = "simulation_";
            String suffixSimulationFileName = ".html";
            int index = 1;
            boolean fileNotExistingFound = false;

            // Check if results absolute dir exist
            File resultsAbsoluteDir = new File(resourceDirectory.toAbsolutePath() + "/" + RESULT_DEFAULT_DIR);
            if (!resultsAbsoluteDir.exists()) {
                resultsAbsoluteDir.mkdir();
            }

            while(!fileNotExistingFound) {
                String absolutePathToFile = resourceDirectory.toAbsolutePath() + "/" + RESULT_DEFAULT_DIR + "/" + prefixSimulationFileName + index + suffixSimulationFileName;
                File f = new File(absolutePathToFile);
                if (!f.exists()) {
                    fileNotExistingFound = true;
                    outputDir = absolutePathToFile;
                } else {
                    index++;
                }
            }
        }
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputDir), "utf-8"))) {
            writer.write(text);
        }
    }

    public void saveSimulationResult(SimulationResult simulationResult) throws IOException {
        this.saveSimulationResult(simulationResult, null);
    }

    public void saveSimulationResult(SimulationResult simulationResult, String outputDir) throws IOException {
        String chartTemplate = getResourceFileAsString(this.TEMPLATE_RESOURCE_FILE);
        StringBuilder dataSetJS = new StringBuilder();
        for (TemplateKeys templateKey : TemplateKeys.values()) {
            dataSetJS.append(templateKey.getJSCode(simulationResult));
        }

        writeStringAsFile(chartTemplate.replace("{{DATASET}}", dataSetJS), outputDir);
    }
}
