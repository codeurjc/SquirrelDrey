package es.codeurjc.squirrel.drey.local.simulator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SimulationResult {

    private final String[] labelsList;
    private final Integer[] highPriorityTasks;
    private final Integer[] lowPriorityTasks;
    private final Integer[] totalTasks;
    private final Integer[] runningWorkers;
    private final Integer[] launchingWorkers;
    private final Integer[] terminatingWorkers;
    private final Integer[] disconnectedWorkers;
    private final Integer[] idleWorkers;
    private final Integer[] minWorkersList;
    private final Integer[] maxWorkersList;
    private final Integer[] minIdleWorkers;
    private final Integer[] runningTasks;

    public SimulationResult(int durationInPeriods, int secondsByPeriod, int minWorkers, int maxWorkers, int minIdleWorkers) throws ParseException {
        this.labelsList = new String[durationInPeriods];
        this.highPriorityTasks = new Integer[durationInPeriods];
        this.lowPriorityTasks = new Integer[durationInPeriods];
        this.totalTasks = new Integer[durationInPeriods];
        this.runningWorkers = new Integer[durationInPeriods];
        this.launchingWorkers = new Integer[durationInPeriods];
        this.terminatingWorkers = new Integer[durationInPeriods];
        this.disconnectedWorkers = new Integer[durationInPeriods];
        this.idleWorkers = new Integer[durationInPeriods];
        this.minWorkersList = new Integer[durationInPeriods];
        this.maxWorkersList = new Integer[durationInPeriods];
        this.minIdleWorkers = new Integer[durationInPeriods];
        this.runningTasks = new Integer[durationInPeriods];

        // Generate fixed arrays
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        Date initialDate = df.parse("00:00:00");
        for(int i = 0; i < durationInPeriods; i++) {
            // Labels
            int currentTime = i * secondsByPeriod;
            Calendar gc = new GregorianCalendar();
            gc.setTime(initialDate);
            gc.add(Calendar.SECOND, currentTime);
            this.labelsList[i] = df.format(gc.getTime());

            // Autoscaling
            this.minWorkersList[i] = minWorkers;
            this.maxWorkersList[i] = maxWorkers;
            this.minIdleWorkers[i] = minIdleWorkers;
        }
    }

    public void addNumOfHighPriorityTasks(int currentPeriod, int numOfHighPriorityTasks) {
        int index = currentPeriod - 1;
        highPriorityTasks[index] = numOfHighPriorityTasks;
    }

    public void addNumOfLowPriorityTasks(int currentPeriod, int numOfLowPriorityTasks) {
        int index = currentPeriod - 1;
        lowPriorityTasks[index] = numOfLowPriorityTasks;
    }

    public void addTotalTasks(int currentPeriod, int numTotalTasks) {
        int index = currentPeriod - 1;
        totalTasks[index] = numTotalTasks;
    }

    public void addNumRunningWorkers(int currentPeriod, int numRunningWorkers) {
        int index = currentPeriod - 1;
        runningWorkers[index] = numRunningWorkers;
    }

    public void addNumLaunchingWorkers(int currentPeriod, int numLaunchingWorkers) {
        int index = currentPeriod - 1;
        launchingWorkers[index] = numLaunchingWorkers;
    }

    public void addNumTerminatingWorkers(int currentPeriod, int numTerminatingWorkers) {
        int index = currentPeriod - 1;
        terminatingWorkers[index] = numTerminatingWorkers;
    }

    public void addNumDisconnectedWorkers(int currentPeriod, int numDisconnectedWorkers) {
        int index = currentPeriod - 1;
        disconnectedWorkers[index] = numDisconnectedWorkers;
    }

    public void addNumIdleWorkers(int currentPeriod, int numIdleWorkers) {
        int index = currentPeriod - 1;
        idleWorkers[index] = numIdleWorkers;
    }

    public void addRunningTasks(int currentPeriod, int numRunningTasks) {
        int index = currentPeriod - 1;
        runningTasks[index] = numRunningTasks;
    }

    protected List<?> getResultByTemplateKeys(ChartGenerator.TemplateKeys templateKey) {
        switch (templateKey) {
            case LABELS_LIST: return Arrays.asList(this.labelsList);
            case HIGH_PRIORITY_TASK: return Arrays.asList(this.highPriorityTasks);
            case LOW_PRIORITY_TASKS: return Arrays.asList(this.lowPriorityTasks);
            case TOTAL_TASKS: return Arrays.asList(this.totalTasks);
            case RUNNING_WORKERS: return Arrays.asList(this.runningWorkers);
            case LAUNCHING_WORKERS: return Arrays.asList(this.launchingWorkers);
            case TERMINATING_WORKERS: return Arrays.asList(this.terminatingWorkers);
            case DISCONNECTED_WORKERS: return Arrays.asList(this.disconnectedWorkers);
            case IDLE_WORKERS: return Arrays.asList(this.idleWorkers);
            case MIN_WORKERS_LIST: return Arrays.asList(this.minWorkersList);
            case MAX_WORKERS_LIST: return Arrays.asList(this.maxWorkersList);
            case MIN_IDDLE_WORKERS: return Arrays.asList(this.minIdleWorkers);
            case RUNNING_TASKS: return Arrays.asList(this.runningTasks);
            default: throw new IllegalArgumentException("Template key is not recognised");
        }
    }

}
