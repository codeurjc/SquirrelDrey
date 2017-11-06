package es.codeurjc.squirrel.drey;

import java.util.Arrays;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.EntryEvent;

import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;

public class MapOfQueuesListener implements EntryAddedListener<String, String>, EntryRemovedListener<String, String> {
	
	private static final Logger log = LoggerFactory.getLogger(MapOfQueuesListener.class);
	
	QueuesManager manager;
	
	public MapOfQueuesListener (QueuesManager manager) {
		this.manager = manager;
	}

	@Override
	public void entryAdded(EntryEvent<String, String> event) {
		String queueId = (String) event.getKey();
		log.info("LISTENER: Queue added [{}]", queueId);
		
		if (this.manager.hasAvailableProcessors()) {
			manager.subscribeToQueues(new HashSet<>(Arrays.asList(queueId)));
		}
	}

	@Override
	public void entryRemoved(EntryEvent<String, String> event) {
		String queueId = (String) event.getKey();
		log.info("LISTENER: Queue removed [{}]", queueId);
		
		manager.unsubscribeFromQueues(new HashSet<>(Arrays.asList(queueId)));
	}

}
