package es.codeurjc.distributed.algorithm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.hazelcast.core.EntryEvent;

import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;

import es.codeurjc.sampleapp.App;

public class MapOfQueuesListener implements EntryAddedListener<String, String>, EntryRemovedListener<String, String> {
	
	QueuesManager manager;
	
	public MapOfQueuesListener (QueuesManager manager) {
		this.manager = manager;
	}

	@Override
	public void entryAdded(EntryEvent<String, String> event) {
		String queueId = (String) event.getKey();
		App.logger.info("Queue added [" + queueId + "]");
		Set<String> set = new HashSet<>(Arrays.asList(queueId));
		manager.subscribeToQueues(set);
		
		/*// If the worker joins the cluster when there are no more task additions to the queue, 
		// it wouldn't poll any from the queue (it only does when 'itemAdded' event fires)
		l.checkQueue();*/
	}

	@Override
	public void entryRemoved(EntryEvent<String, String> event) {
		App.logger.info("Queue removed [" + event.getKey() + "]");
	}

}
