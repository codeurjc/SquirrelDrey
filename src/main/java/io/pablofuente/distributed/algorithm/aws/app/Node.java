package io.pablofuente.distributed.algorithm.aws.app;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

import io.pablofuente.distributed.algorithm.aws.project.MyEvent;
import io.pablofuente.distributed.algorithm.aws.project.ProjectTask;
import io.pablofuente.distributed.algorithm.aws.project.QueueListener;

public class Node {
	
	ExecutorService executor;
	Map<String, QueueListener> listeners;
	HazelcastInstance hc;

	public void start() {

		int processors = Runtime.getRuntime().availableProcessors();
		System.out.println("Number of cores: " + processors);
		
		this.executor = Executors.newFixedThreadPool(processors);
		
		this.listeners = new HashMap<>();
		
		this.hc = Hazelcast.newHazelcastInstance();
		this.hc.getTopic("new-project").addMessageListener((message) -> {
			MyEvent ev = (MyEvent) message.getMessageObject();
			String queueId = (String) ev.getContent();
			
			System.out.println("NEW PROJECT: id [" + queueId + "]");
			
			IQueue<ProjectTask> queue = this.hc.getQueue(queueId);
			QueueListener listener = new QueueListener(queue, executor, listeners, this.hc);
			String listenerId = queue.addItemListener(listener, true);
			listener.setId(listenerId);
			listeners.put(queueId, listener);
		});

		/*Config config = new Config();
		ExecutorConfig executorConfig = config.getExecutorConfig("task-runner");
		executorConfig.setPoolSize(processors).setStatisticsEnabled(true);
		config.addExecutorConfig(executorConfig);

		Hazelcast.newHazelcastInstance(config);*/
	}

}
