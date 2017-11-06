package es.codeurjc.distributed.algorithm;

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

	public void start(String HAZELCAST_CONFIG, Mode mode) {

		Config cfg = new Config();
		try {
			cfg = new FileSystemXmlConfig(HAZELCAST_CONFIG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		this.queuesManager = new QueuesManager(mode);

		MapOfQueuesListener mapOfQueuesListener = new MapOfQueuesListener(queuesManager);

		MapConfig mapConfig = new MapConfig();
		mapConfig.setName("QUEUES");
		mapConfig.addEntryListenerConfig(new EntryListenerConfig(mapOfQueuesListener, false, true));
		cfg.addMapConfig(mapConfig);
		
		this.hc = Hazelcast.newHazelcastInstance(cfg);
		this.queuesManager.initializeHazelcast(hc);
		
	}

}
