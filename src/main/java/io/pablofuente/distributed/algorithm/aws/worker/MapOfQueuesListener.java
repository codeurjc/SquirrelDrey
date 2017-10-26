package io.pablofuente.distributed.algorithm.aws.worker;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;

import io.pablofuente.distributed.algorithm.aws.project.ProjectTask;

public class MapOfQueuesListener implements EntryAddedListener<String, String>, EntryRemovedListener<String, String> {
	
	HazelcastInstance hc;
	ThreadPoolExecutor executor;
	Map<String, QueueListener> listeners;
	
	public MapOfQueuesListener (HazelcastInstance hc, ThreadPoolExecutor executor, Map<String, QueueListener> listeners) {
		this.hc = hc;
		this.executor = executor;
		this.listeners = listeners;
	}

	@Override
	public void entryAdded(EntryEvent<String, String> event) {
		String queueId = (String) event.getKey();
		
		System.out.println("NEW PROJECT: id [" + queueId + "]");
		
		addQueueListener(queueId);
	}

	@Override
	public void entryRemoved(EntryEvent<String, String> event) {
		System.out.println("Queue removed [" + event.getKey() + "]");
	}
	
	public void addQueueListener(String queueId){
		IQueue<ProjectTask> queue = this.hc.getQueue(queueId);
		QueueListener listener = new QueueListener(queue, executor, listeners, this.hc);
		String listenerId = queue.addItemListener(listener, true);
		listener.setId(listenerId);
		listeners.put(queueId, listener);
		
		// If the worker joins the cluster when there are no more task additions to the queue, 
		// it wouldn't poll any from the queue (it only does when 'itemAdded' event fires) 
		listener.checkQueue();
	}

}
