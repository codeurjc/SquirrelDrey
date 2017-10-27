package es.codeurjc.distributed.algorithm;

import java.io.FileNotFoundException;

import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;


public class Node {
		
	HazelcastInstance hc;
	QueuesManager queuesManager;

	public void start(String HAZELCAST_CONFIG) {
		
		Config cfg = new Config();
		try {
			cfg = new FileSystemXmlConfig(HAZELCAST_CONFIG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		this.hc = Hazelcast.newHazelcastInstance(cfg);
		this.queuesManager = new QueuesManager(hc);
	}

}
