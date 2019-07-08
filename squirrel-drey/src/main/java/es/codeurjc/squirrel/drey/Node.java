package es.codeurjc.squirrel.drey;

import java.io.FileNotFoundException;

import com.hazelcast.config.Config;
import com.hazelcast.config.EntryListenerConfig;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public class Node {

	HazelcastInstance hc;
	QueuesManager queuesManager;

	public void start(String HAZELCAST_CONFIG, Mode mode, int idleCores) {

		Config config = new Config();
		try {
			config = new FileSystemXmlConfig(HAZELCAST_CONFIG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		int cpMemeberCount = System.getProperty("cp-member-count") != null
				? Integer.parseInt(System.getProperty("cp-member-count"))
				: 3;
		int cpSessionHeartbeat = System.getProperty("cp-session-heartbeat") != null
				? Integer.parseInt(System.getProperty("cp-session-heartbeat"))
				: 30;
		int cpSessionTTL = System.getProperty("cp-session-ttl") != null
				? Integer.parseInt(System.getProperty("cp-session-ttl"))
				: 180;
		int cpMissingMemberAutoremoval = System.getProperty("cp-missing-member-autoremoval") != null
				? Integer.parseInt(System.getProperty("cp-missing-member-autoremoval"))
				: 300;

		config.getCPSubsystemConfig().setCPMemberCount(cpMemeberCount)
				.setSessionHeartbeatIntervalSeconds(cpSessionHeartbeat).setSessionTimeToLiveSeconds(cpSessionTTL)
				.setMissingCPMemberAutoRemovalSeconds(cpMissingMemberAutoremoval);

		this.queuesManager = new QueuesManager(mode);

		MapOfQueuesListener mapOfQueuesListener = new MapOfQueuesListener(queuesManager);

		MapConfig mapConfig = new MapConfig();
		mapConfig.setName("QUEUES");
		mapConfig.addEntryListenerConfig(new EntryListenerConfig(mapOfQueuesListener, false, true));
		config.addMapConfig(mapConfig);

		this.hc = Hazelcast.newHazelcastInstance(config);

		this.queuesManager.initializeHazelcast(hc, idleCores);

	}

}
