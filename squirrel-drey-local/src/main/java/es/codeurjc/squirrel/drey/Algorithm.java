package es.codeurjc.squirrel.drey;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Algorithm<R> {
    public enum Status {
        /**
         * Algorithm has started (method
         * {@link AlgorithmManager#solveAlgorithm(String, Task, Integer)} has been
         * called)
         */
        STARTED,
        /**
         * Algorithm has successfully finished
         */
        COMPLETED,
        /**
         * Algorithm has been manually cancelled by calling any of the termination
         * methods of {@link AlgorithmManager}
         */
        TERMINATED,
        /**
         * Algorithm has been forcibly finished by a task that didn't manage to complete
         * within its specified timeout
         */
        TIMEOUT
    }

    private String id;
    private Integer priority;
    private Status status;

    private R result;

    private int finalTasksAdded;
    private int finalTasksCompleted;
    private int finalTasksQueued;
    private int finalTasksTimeout;
    private AtomicBoolean finished = new AtomicBoolean(false);

    private Long initTime;
    private Long finishTime;

    private Task initialTask;
    private List<Task> errorTasks;
    private Consumer<R> callback;
    private AlgorithmCallback<R> algorithmCallback;

    private final AlgorithmManager<R> algManager;

    public Algorithm(AlgorithmManager<R> algManager, String id, Integer priority, Task initialTask) {
        this.algManager = algManager;
        this.id = id;
        this.priority = priority;
        initialTask.setAlgorithm(this.getId());
        this.initialTask = initialTask;
        this.errorTasks = new ArrayList<>();
    }

    public Algorithm(AlgorithmManager<R> algManager, String id, Integer priority, Task initialTask,
            Consumer<R> callback) {
        this.algManager = algManager;
        this.id = id;
        this.priority = priority;
        initialTask.setAlgorithm(this.getId());
        this.initialTask = initialTask;
        this.callback = callback;
        this.errorTasks = new ArrayList<>();
    }

    public Algorithm(AlgorithmManager<R> algManager, String id, Integer priority, Task initialTask,
            AlgorithmCallback<R> callback) {
        this.algManager = algManager;
        this.id = id;
        this.priority = priority;
        initialTask.setAlgorithm(this.getId());
        this.initialTask = initialTask;
        this.algorithmCallback = callback;
        this.errorTasks = new ArrayList<>();
    }

    public Algorithm(AlgorithmManager<R> algManager, String id, Algorithm<R> otherAlgorithm) {
        this.algManager = algManager;
        this.id = id;
        this.priority = otherAlgorithm.getPriority();
        this.initialTask = otherAlgorithm.getInitialTask();
        this.initialTask.setAlgorithm(this.getId());
        if (otherAlgorithm.callback != null) {
            this.callback = otherAlgorithm.callback;
        } else if (otherAlgorithm.algorithmCallback != null) {
            this.algorithmCallback = otherAlgorithm.algorithmCallback;
        }
        this.errorTasks = new ArrayList<>();
    }

    public String getId() {
        return this.id;
    }

    public Integer getPriority() {
        return this.priority;
    }

    public Status getStatus() {
        return this.status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    public Task getInitialTask() {
        return this.initialTask;
    }

    public void solve(Queue<Task> queue) throws Exception {
        this.status = Status.STARTED;
        this.initTime = System.currentTimeMillis();

        this.initialTask.status = Task.Status.QUEUED;

        queue.add(this.initialTask);
        this.algManager.taskAdded(this.initialTask, this.id);
    }

    public void addErrorTask(Task errorTask) {
        this.errorTasks.add(errorTask);
    }

    public R getResult() {
        return result;
    }

    public void setResult(R result) {
        this.result = result;
    }

    public boolean hasSuccessullyFinished(Long numberOfTaskCompletedEvents) {
        final int tasksAdded = this.getTasksAdded();
        final int tasksCompleted = this.getTasksCompleted();
        final int tasksQueued = this.getTasksQueued();
        final int tasksTimeout = this.getTasksTimeout();
        boolean hasSuccessullyFinished = (tasksAdded == tasksCompleted) && (tasksTimeout == 0)
                && (tasksCompleted == numberOfTaskCompletedEvents) && (tasksQueued == 0);
        if (hasSuccessullyFinished) {
            this.finalTasksAdded = tasksAdded;
            this.finalTasksCompleted = tasksCompleted;
            this.finalTasksQueued = tasksQueued;
            this.finalTasksTimeout = tasksTimeout;
            this.finished.compareAndSet(false, true);
        }
        return hasSuccessullyFinished;
    }

    public int getTasksAdded() {
        if (this.finished.get()) {
            return this.finalTasksAdded;
        }
        return Math.toIntExact(this.algManager.algorithmAddedTasks.get(this.id).get());
    }

    public int getTasksCompleted() {
        if (this.finished.get()) {
            return this.finalTasksCompleted;
        }
        return Math.toIntExact(this.algManager.algorithmCompletedTasks.get(this.id).get());
    }

    public int getTasksQueued() {
        if (this.finished.get()) {
            return this.finalTasksQueued;
        }
        return this.algManager.algorithmQueues.get(this.id).size();
    }

    public int getTasksTimeout() {
        if (this.finished.get()) {
            return this.finalTasksTimeout;
        }
        return Math.toIntExact(this.algManager.algorithmTimeoutTasks.get(this.id).get());
    }

    public int getTasksFinished() {
        return this.getTasksCompleted() + this.getTasksTimeout();
    }

    public void runCallbackSuccess() throws Exception {
        this.finishTime = System.currentTimeMillis();
        this.status = Status.COMPLETED;
        if (this.callback != null) {
            this.callback.accept(this.result);
        } else if (this.algorithmCallback != null) {
            this.algorithmCallback.onSuccess(this.result, this);
        }
    }

    public void runCallbackError(Status status) {
        this.finishTime = System.currentTimeMillis();
        this.status = status;
        if (this.algorithmCallback != null) {
            this.algorithmCallback.onError(this);
        }
    }

    public boolean hasFinishedRunningTasks(int addedMinusQueued) {
        final int finished = this.getTasksFinished();
        System.out.println("Added minus queued: " + addedMinusQueued);
        System.out.println("Tasks finished: " + finished);
        return finished == addedMinusQueued;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (this.id.equals(((Algorithm<?>) o).id));
    }

    public Long getTimeOfProcessing() {
        if (this.finishTime != null) {
            return (this.finishTime - this.initTime) / 1000;
        } else {
            return 0L;
        }
    }
}