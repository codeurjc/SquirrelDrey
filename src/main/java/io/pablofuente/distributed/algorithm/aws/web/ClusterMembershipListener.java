package io.pablofuente.distributed.algorithm.aws.web;

import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

public class ClusterMembershipListener implements MembershipListener {

	@Override
	public void memberAdded(MembershipEvent membershipEvent) {
		System.out.println("MEMBER [" + membershipEvent.getMember().toString() + "] ADDED TO CLUSTER");
	}

	@Override
	public void memberAttributeChanged(MemberAttributeEvent membershipEvent) {
		System.out.println("MEMBER [" + membershipEvent.getMember().toString() + "] CHANGED AN ATTRIBUTE");
	}

	@Override
	public void memberRemoved(MembershipEvent membershipEvent) {
		System.out.println("MEMBER [" + membershipEvent.getMember().toString() + "] REMOVED FROM CLUSTER");
	}

}
