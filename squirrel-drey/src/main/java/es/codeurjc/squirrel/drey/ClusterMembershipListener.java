package es.codeurjc.squirrel.drey;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import com.hazelcast.cp.CPSubsystemManagementService;

public class ClusterMembershipListener implements MembershipListener {

	private static final Logger log = LoggerFactory.getLogger(ClusterMembershipListener.class);

	private AlgorithmManager<?> manager;
	private HazelcastInstance hc;

	public ClusterMembershipListener(AlgorithmManager<?> manager, HazelcastInstance hc) {
		this.manager = manager;
		this.hc = hc;
	}

	@Override
	public void memberAdded(MembershipEvent membershipEvent) {
		String memberId = membershipEvent.getMember().getAddress().toString();
		log.info("MEMBER [" + memberId + "] ADDED TO CLUSTER");
		this.manager.workers.put(memberId, new WorkerStats());
	}

	@Override
	public void memberAttributeChanged(MemberAttributeEvent membershipEvent) {
		log.info("MEMBER [" + membershipEvent.getMember().getAddress().toString() + "] CHANGED AN ATTRIBUTE");
	}

	@Override
	public void memberRemoved(MembershipEvent membershipEvent) {
		String memberId = membershipEvent.getMember().getAddress().toString();
		log.info("MEMBER [" + memberId + "] REMOVED FROM CLUSTER");

		// If the removed node was running tasks, they are inserted in
		// MAX_PRIORITY_QUEUE
		IMap<Integer, Task> runningTasks = this.manager.hzClient.getMap("RUNNING_TASKS_" + memberId);

		if (!runningTasks.isEmpty()) {
			IQueue<Task> maxPriorityQueue = this.manager.hzClient.getQueue("MAX_PRIORITY_QUEUE");
			maxPriorityQueue.addAll(runningTasks.values());

			log.info("Moved {} running tasks to max priority queue from worker {}. Tasks: {}", runningTasks.size(),
					memberId, runningTasks.values());

			runningTasks.clear();
		}

		this.manager.workers.remove(memberId);

		log.info("Removing member {} from CP Group", memberId);
		CPSubsystemManagementService cpManager = this.hc.getCPSubsystem().getCPSubsystemManagementService();
		cpManager.removeCPMember(membershipEvent.getMember().getUuid());

		if (membershipEvent.getMembers().size() == 1) {
			// CP Subsystem will not be available anymore, as it has lost its majority (for
			// CP Subsystem of 3 members, 2 is majority). Must be manually restarted after
			// CP Subsystem is available again

			log.warn(
					"There's only the master worker in the Hazelcast cluster. CP Subsytem has lost its availability and must be restarted");

			int minutes = System.getProperty("init-timeout") != null
					? Integer.parseInt(System.getProperty("init-timeout"))
					: 3;
			final int waitInterval = 5;
			int numberOfRounds = minutes * 60 / waitInterval;

			while (numberOfRounds > 0) {
				int numberOfMembers = this.hc.getCluster().getMembers().size();
				if (numberOfMembers < 3) {
					log.warn(
							"The number of members ({}) is too low to restart the CP Subsystem. Waiting for at most {} minutes until there are 3 members at least",
							numberOfMembers, minutes);
					try {
						Thread.sleep(waitInterval * 1000);
						numberOfRounds--;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					try {
						log.info("Restarting CP Subsystem form master member");
						hc.getCPSubsystem().getCPSubsystemManagementService().restart().get();
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
					}
					break;
				}
			}
		}
	}

}
