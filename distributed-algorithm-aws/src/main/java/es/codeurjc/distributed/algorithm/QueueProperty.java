package es.codeurjc.distributed.algorithm;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueProperty implements Serializable, Comparable<QueueProperty> {

	private static final long serialVersionUID = 1L;
	Integer priority;
	AtomicInteger lastTimeUpdated;

	public QueueProperty(Integer priority, AtomicInteger lastTimeUpdated) {
		this.priority = priority;
		this.lastTimeUpdated = lastTimeUpdated;
	}

	public Integer getPriority() {
		return priority;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public AtomicInteger getLastTimeUpdated() {
		return lastTimeUpdated;
	}

	public void setLastTimeUpdated(AtomicInteger lastTimeUpdated) {
		this.lastTimeUpdated = lastTimeUpdated;
	}
	
	@Override
	public String toString() {
		return "(priority=" + this.priority + ", lastTimeUpdated=" + this.lastTimeUpdated.get() + ")";
	}

	@Override
	public int compareTo(QueueProperty o) {
		if (this.priority == o.priority) {
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
