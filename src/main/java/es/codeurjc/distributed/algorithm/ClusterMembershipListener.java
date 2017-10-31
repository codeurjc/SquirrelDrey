package es.codeurjc.distributed.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

public class ClusterMembershipListener implements MembershipListener {
	
	private static final Logger log = LoggerFactory.getLogger(ClusterMembershipListener.class);
	
	private AlgorithmManager<?> manager;

	public ClusterMembershipListener(AlgorithmManager<?> manager) {
		this.manager = manager;
	}

	@Override
	public void memberAdded(MembershipEvent membershipEvent) {
		String memberId = membershipEvent.getMember().getAddress().toString();
		log.info("MEMBER [" + memberId + "] ADDED TO CLUSTER");
		this.manager.workers.put(memberId, new WorkerStats());
	}

	@Override
	public void memberAttributeChanged(MemberAttributeEvent membershipEvent) {
		log.info("MEMBER [" +  membershipEvent.getMember().getAddress().toString() + "] CHANGED AN ATTRIBUTE");
	}

	@Override
	public void memberRemoved(MembershipEvent membershipEvent) {
		String memberId = membershipEvent.getMember().getAddress().toString();
		log.info("MEMBER [" +  memberId + "] REMOVED FROM CLUSTER");
		
		// If the removed node was running tasks, they are inserted in MAX_PRIORITY_QUEUE
		IMap<Integer, Task<?>> runningTasks = this.manager.hzClient.getMap("RUNNING_TASKS_" + memberId);
		
		System.out.println(runningTasks);
		System.out.println(runningTasks.size());
		
		if (!runningTasks.isEmpty()) {
			IQueue<Task<?>> maxPriorityQueue = this.manager.hzClient.getQueue("MAX_PRIORITY_QUEUE");
			maxPriorityQueue.addAll(runningTasks.values());

			log.info("Moved {} running tasks to max priority queue: {}", runningTasks.size(), runningTasks.values());
			runningTasks.clear();
		}
		
		this.manager.workers.remove(memberId);
	}

}
