package es.codeurjc.squirrel.drey.local;

import java.util.concurrent.atomic.AtomicLong;

public class QueueProperty implements Comparable<QueueProperty> {

    Integer priority;
    AtomicLong lastTimeUpdated;

    public QueueProperty(Integer priority, long lastTimeUpdated) {
        this.priority = priority;
        this.lastTimeUpdated = new AtomicLong(lastTimeUpdated);
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public AtomicLong getLastTimeUpdated() {
        return lastTimeUpdated;
    }

    public void setLastTimeUpdated(long lastTimeUpdated) {
        this.lastTimeUpdated.set(lastTimeUpdated);
    }

    @Override
    public String toString() {
        return "(priority=" + this.priority + ", lastTimeUpdated=" + this.lastTimeUpdated.get() + ")";
    }

    @Override
    public int compareTo(QueueProperty o) {
        if (this.priority.equals(o.priority)) {
            double random = Math.random();
            if (random >= 0.5) {
                return -1;
            } else {
                return 1;
            }
        } else {
            return (this.priority < o.priority) ? -1 : 1;
        }
    }

}
