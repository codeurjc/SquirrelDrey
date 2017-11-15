package es.codeurjc.squirrel.drey;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IAtomicReference;
import com.hazelcast.core.ICountDownLatch;
import com.hazelcast.core.IList;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ISemaphore;
import com.hazelcast.core.ISet;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.core.MultiMap;
import com.hazelcast.core.ReplicatedMap;
import com.hazelcast.ringbuffer.Ringbuffer;

public class Task implements Callable<Void>, Serializable, HazelcastInstanceAware {
	
	private static final long serialVersionUID = 1L;

	protected transient HazelcastInstance hazelcastInstance;
	
	protected String algorithmId;
	protected final int uniqueId = UUID.randomUUID().hashCode();
	private Object finalResult = null;
	private Map<String, String> hazelcastStructures = new HashMap<>();
	
	public int getId() {
		return this.uniqueId;
	}

	public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
		this.hazelcastInstance = hazelcastInstance;
	}
	
	public void setAlgorithm(String algorithmId) {
		this.algorithmId = algorithmId;
	}
	
	public void algorithmSolved(Object finalResult) {
		this.finalResult = finalResult;
	}
	
	public Object getFinalResult() {
		return this.finalResult;
	}
	
	public Map<String, String> getHazelcastStructures() {
		return this.hazelcastStructures;
	}
	
	public void setHazelcastStructures(Map<String, String> hazelcastStructures) {
		this.hazelcastStructures = hazelcastStructures;
	}
	
	public void process() throws Exception {
		this.call();
	}

	@Override
	public Void call() throws Exception {
		return null;
	}

	public void callback() {
		this.publishTasksQueued();
		hazelcastInstance.getTopic("task-completed").publish(new AlgorithmEvent(this.algorithmId, "task-completed", this));
	}

	protected void addNewTask(Task t) {
		t.setAlgorithm(this.algorithmId);
		t.setHazelcastStructures(this.hazelcastStructures);
		IQueue<Task> queue = hazelcastInstance.getQueue(this.algorithmId);
		queue.add(t);
		
		hazelcastInstance.getTopic("task-added").publish(new AlgorithmEvent(this.algorithmId, "task-added", t));
		
		// Update last addition time
		IMap<String, QueueProperty> map = hazelcastInstance.getMap("QUEUES");
		QueueProperty properties = map.get(this.algorithmId);
		properties.lastTimeUpdated.set((int) System.currentTimeMillis());
		map.set(this.algorithmId, properties);
	}
	
	protected void publishTasksQueued() {
		IQueue<Task> queue = hazelcastInstance.getQueue(this.algorithmId);

		hazelcastInstance.getTopic("tasks-queued")
				.publish(new AlgorithmEvent(this.algorithmId, "tasks-queued", queue.size()));
	}
	
	@Override
	public int hashCode() {
		return this.uniqueId;
	}
	
	@Override
	public boolean equals(Object o) {
		return (this.uniqueId == ((Task)o).uniqueId);
	}
	
	private String getStructureId(String customId, HazelcastStructure structure) {
		return this.algorithmId + "-" + customId + "-" + structure.toString();
	}
	
	protected IMap<?, ?> getMap(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.MAP);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getMap(id);
	}
	
	protected IQueue<?> getQueue(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.QUEUE);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getQueue(id);
	}
	
	protected Ringbuffer<?> getRingbuffer(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.RINGBUFFER);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getRingbuffer(id);
	}
	
	protected ISet<?> getSet(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.SET);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getSet(id);
	}
	
	protected IList<?> getList(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.LIST);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getList(id);
	}
	
	protected MultiMap<?, ?> getMultiMap(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.MULTI_MAP);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getMultiMap(id);
	}
	
	protected ReplicatedMap<?, ?> getReplicatedMap(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.REPLICATED_MAP);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getReplicatedMap(id);
	}
	
	/*protected CardinalityEstimator getCardinalityEstimator(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.CARDINALITY_ESTIMATOR);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCardinalityEstimator(id);
	}*/
	
	protected ITopic<?> getTopic(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.TOPIC);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getTopic(id);
	}
	
	protected ILock getLock(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.LOCK);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getLock(id);
	}
	
	protected ISemaphore getSemaphore(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.SEMAPHORE);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getSemaphore(id);
	}
	
	protected IAtomicLong getAtomicLong(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.ATOMIC_LONG);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getAtomicLong(id);
	}
	
	protected IAtomicReference<?> getAtomicReference(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.ATOMIC_REFERENCE);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getAtomicReference(id);
	}
	
	protected IdGenerator getIdGenerator(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.ID_GENERATOR);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getIdGenerator(id);
	}
	
	protected ICountDownLatch getCountDownLatch(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.COUNTDOWN_LATCH);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCountDownLatch(id);
	}
}
