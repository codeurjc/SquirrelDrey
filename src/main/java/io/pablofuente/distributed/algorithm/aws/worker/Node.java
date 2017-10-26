package io.pablofuente.distributed.algorithm.aws.worker;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

public class Node {
		
	ThreadPoolExecutor executor;
	Map<String, QueueListener> listeners;
	HazelcastInstance hc;
	IMap<String, String> QUEUES;

	public void start(String HAZELCAST_CONFIG) {

		int processors = Runtime.getRuntime().availableProcessors();
		System.out.println("Number of cores: " + processors);
		
		this.executor = new ThreadPoolExecutor(processors, processors,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
		
		Executors.newFixedThreadPool(processors);
		
		this.listeners = new HashMap<>();
		
		Config cfg = new Config();
		try {
			cfg = new FileSystemXmlConfig(HAZELCAST_CONFIG);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		this.hc = Hazelcast.newHazelcastInstance(cfg);
		this.QUEUES = hc.getMap("QUEUES");
		
		// Add a listener to every existing queue
		MapOfQueuesListener mapListener = new MapOfQueuesListener(hc, executor, listeners);
		for (String queueId : QUEUES.values()) {
			mapListener.addQueueListener(queueId);
		}
		
		// Add a listener to the map of queues to listen for new queues
		QUEUES.addEntryListener(mapListener, true);
	}

}
