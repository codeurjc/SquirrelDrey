package es.codeurjc.distributed.algorithm;

import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

import es.codeurjc.sampleapp.App;

public class ClusterMembershipListener implements MembershipListener {

	@Override
	public void memberAdded(MembershipEvent membershipEvent) {
		App.logger.info("MEMBER [" + membershipEvent.getMember().toString() + "] ADDED TO CLUSTER");
	}

	@Override
	public void memberAttributeChanged(MemberAttributeEvent membershipEvent) {
		App.logger.info("MEMBER [" + membershipEvent.getMember().toString() + "] CHANGED AN ATTRIBUTE");
	}

	@Override
	public void memberRemoved(MembershipEvent membershipEvent) {
		App.logger.info("MEMBER [" + membershipEvent.getMember().toString() + "] REMOVED FROM CLUSTER");
	}

}
