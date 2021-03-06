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
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ISemaphore;
import com.hazelcast.core.ISet;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.MultiMap;
import com.hazelcast.core.ReplicatedMap;
import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.flakeidgen.FlakeIdGenerator;
import com.hazelcast.ringbuffer.Ringbuffer;

public class Task implements Callable<Void>, Serializable, HazelcastInstanceAware {

	public enum Status {
		/**
		 * Task is waiting in the algorithm's distributed queue
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

	private static final long serialVersionUID = 1L;

	protected transient HazelcastInstance hazelcastInstance;

	protected Status status;

	protected String algorithmId;
	private final int uniqueId = Math.abs(UUID.randomUUID().hashCode());
	private Object finalResult = null;
	private Map<String, String> hazelcastStructures = new HashMap<>();

	private long timeStarted;
	private long maxDuration;

	public int getId() {
		return this.uniqueId;
	}

	public Status getStatus() {
		return this.status;
	}

	public long getTimeStarted() {
		return this.timeStarted;
	}

	public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
		this.hazelcastInstance = hazelcastInstance;
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

	final void initializeTask() {
		this.timeStarted = System.currentTimeMillis();
		this.status = Status.RUNNING;
	}

	public final void callback() {
		this.status = Status.COMPLETED;
		hazelcastInstance.getTopic("task-completed")
				.publish(new AlgorithmEvent(this.algorithmId, "task-completed", this));
	}

	protected final void addNewTask(Task t) {
		t.setAlgorithm(this.algorithmId);
		t.setHazelcastStructures(this.hazelcastStructures);
		IQueue<Task> queue = hazelcastInstance.getQueue(this.algorithmId);

		t.status = Status.QUEUED;
		queue.add(t);
		this.hazelcastInstance.getCPSubsystem().getAtomicLong("added" + this.algorithmId).incrementAndGet();

		// Update last addition time
		IMap<String, QueueProperty> map = hazelcastInstance.getMap("QUEUES");
		QueueProperty properties = map.get(this.algorithmId);
		properties.lastTimeUpdated.set(System.currentTimeMillis());
		map.set(this.algorithmId, properties);
	}

	@Override
	public int hashCode() {
		return this.uniqueId;
	}

	@Override
	public boolean equals(Object o) {
		return (this.uniqueId == ((Task) o).uniqueId);
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

	protected ITopic<?> getTopic(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.TOPIC);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getTopic(id);
	}

	protected FencedLock getLock(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.LOCK);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCPSubsystem().getLock(id);
	}

	protected ISemaphore getSemaphore(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.SEMAPHORE);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCPSubsystem().getSemaphore(id);
	}

	protected IAtomicLong getAtomicLong(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.ATOMIC_LONG);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCPSubsystem().getAtomicLong(id);
	}

	protected IAtomicReference<?> getAtomicReference(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.ATOMIC_REFERENCE);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCPSubsystem().getAtomicReference(id);
	}

	protected FlakeIdGenerator getIdGenerator(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.ID_GENERATOR);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getFlakeIdGenerator(id);
	}

	protected ICountDownLatch getCountDownLatch(String customId) {
		String id = this.getStructureId(customId, HazelcastStructure.COUNTDOWN_LATCH);
		this.hazelcastStructures.putIfAbsent(id, id);
		return this.hazelcastInstance.getCPSubsystem().getCountDownLatch(id);
	}

	@Override
	public String toString() {
		return this.algorithmId + "@" + this.getClass().getSimpleName() + "@" + this.getId();
	}
}
