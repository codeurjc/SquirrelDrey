package es.codeurjc.squirrel.drey;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Task implements Callable<Void> {
    public enum Status {
        /**
         * Task is waiting in the algorithm's queue
         */
        QUEUED,
        /**
         * Task is running on some worker
         */
        RUNNING,
        /**
         * Task has successfully finished
         */
        COMPLETED,
        /**
         * Task didn't manage to finish within its specified timeout
         */
        TIMEOUT
    }

    private enum Structures {
        MAP, ATOMICLONG
    }

    protected AlgorithmManager<?> algorithmManager;

    protected Status status;

    protected String algorithmId;
    private final int uniqueId = Math.abs(UUID.randomUUID().hashCode());
    private Object finalResult = null;

    private long timeStarted;
    private long maxDuration;

    private Map<String, String> structures = new ConcurrentHashMap<>();

    public void setAlgorithmManager(AlgorithmManager<?> algorithmManager) {
        this.algorithmManager = algorithmManager;
    }

    public int getId() {
        return this.uniqueId;
    }

    public Status getStatus() {
        return this.status;
    }

    public long getTimeStarted() {
        return this.timeStarted;
    }

    public void process() throws Exception {
        this.call();
    }

    @Override
    public Void call() throws Exception {
        return null;
    }

    public void setAlgorithm(String algorithmId) {
        this.algorithmId = algorithmId;
    }

    public long getMaxDuration() {
        return this.maxDuration;
    }

    public void setMaxDuration(long milliseconds) {
        this.maxDuration = milliseconds;
    }

    public Map<String, String> getStructures() {
        return this.structures;
    }

    public void setStructures(Map<String, String> structures) {
        this.structures = structures;
    }

    public void algorithmSolved(Object finalResult) {
        this.finalResult = finalResult;
    }

    public Object getFinalResult() {
        return this.finalResult;
    }

    final void initializeTask() {
        this.timeStarted = System.currentTimeMillis();
        this.status = Status.RUNNING;
    }

    public final void callback() {
        this.status = Status.COMPLETED;
        this.algorithmManager.taskCompleted(new AlgorithmEvent(this.algorithmId, "task-completed", this));
    }

    protected final void addNewTask(Task t) {
        t.setAlgorithm(this.algorithmId);
        t.setStructures(this.structures);
        Queue<Task> queue = this.algorithmManager.algorithmQueues.get(this.algorithmId);

        t.status = Status.QUEUED;
        queue.add(t);
        this.algorithmManager.algorithmAddedTasks.get(this.algorithmId).incrementAndGet();

        // Update last addition time
        Map<String, QueueProperty> map = this.algorithmManager.QUEUES;
        QueueProperty properties = map.get(this.algorithmId);
        properties.lastTimeUpdated.set(System.currentTimeMillis());
        map.put(this.algorithmId, properties);

        this.algorithmManager.taskAdded(t, this.algorithmId);
    }

    @Override
    public int hashCode() {
        return this.uniqueId;
    }

    @Override
    public boolean equals(Object o) {
        return (this.uniqueId == ((Task) o).uniqueId);
    }

    @Override
    public String toString() {
        return this.algorithmId + "@" + this.getClass().getSimpleName() + "@" + this.getId();
    }

    private String getStructureId(String customId, Structures structure) {
        return this.algorithmId + "-" + customId + "-" + structure.toString();
    }

    public Map<?, ?> getMap(String customId) {
        String id = this.getStructureId(customId, Structures.MAP);
        this.structures.putIfAbsent(id, id);
        Map<?, ?> map = (Map<?, ?>) TaskStructures.mapOfStructures.get(id);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            TaskStructures.mapOfStructures.put(id, map);
        }
        return map;
    }

    public AtomicLong getAtomicLong(String customId) {
        String id = this.getStructureId(customId, Structures.ATOMICLONG);
        this.structures.putIfAbsent(id, id);
        AtomicLong atomicLong = (AtomicLong) TaskStructures.mapOfStructures.get(id);
        if (atomicLong == null) {
            atomicLong = new AtomicLong();
            TaskStructures.mapOfStructures.put(id, atomicLong);
        }
        return atomicLong;
    }
}