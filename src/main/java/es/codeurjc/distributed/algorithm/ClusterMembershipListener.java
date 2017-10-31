package es.codeurjc.distributed.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

public class ClusterMembershipListener implements MembershipListener {
	
	private static final Logger log = LoggerFactory.getLogger(ClusterMembershipListener.class);
	
	private HazelcastInstance hzClient;

	public ClusterMembershipListener(HazelcastInstance hzClient) {
		this.hzClient = hzClient;
	}

	@Override
	public void memberAdded(MembershipEvent membershipEvent) {
		log.info("MEMBER [" + membershipEvent.getMember().toString() + "] ADDED TO CLUSTER");
	}

	@Override
	public void memberAttributeChanged(MemberAttributeEvent membershipEvent) {
		log.info("MEMBER [" + membershipEvent.getMember().toString() + "] CHANGED AN ATTRIBUTE");
	}

	@Override
	public void memberRemoved(MembershipEvent membershipEvent) {
		log.info("MEMBER [" + membershipEvent.getMember().toString() + "] REMOVED FROM CLUSTER");
		
		// If the removed node was running tasks, they are inserted in MAX_PRIORITY_QUEUE
		IMap<Integer, Task<?>> runningTasks = this.hzClient.getMap("RUNNING_TASKS_" + membershipEvent.getMember().getAddress().toString());
		
		System.out.println(runningTasks);
		System.out.println(runningTasks.size());
		
		if (!runningTasks.isEmpty()) {
			IQueue<Task<?>> maxPriorityQueue = this.hzClient.getQueue("MAX_PRIORITY_QUEUE");
			maxPriorityQueue.addAll(runningTasks.values());

			log.info("Moved {} running tasks to max priority queue: {}", runningTasks.size(), runningTasks.values());
			runningTasks.clear();
		}
	}

}
