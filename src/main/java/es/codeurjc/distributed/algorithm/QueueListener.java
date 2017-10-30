package es.codeurjc.distributed.algorithm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.IQueue;
import com.hazelcast.core.ItemEvent;
import com.hazelcast.core.ItemListener;

public class QueueListener implements ItemListener<Task<?>> {
	
	private static final Logger log = LoggerFactory.getLogger(QueueListener.class);

	IQueue<Task<?>> queue;
	String id;
	QueuesManager manager;
	
	Map<String, Boolean> addEventChecking = new ConcurrentHashMap<>();
	Map<String, Boolean> removedEventChecking = new ConcurrentHashMap<>();

	public QueueListener(IQueue<Task<?>> queue, QueuesManager manager) {
		this.queue = queue;
		this.manager = manager;
	}
	
	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	@Override
	public void itemAdded(ItemEvent<Task<?>> item) {
		if (this.addEventChecking.putIfAbsent(item.getItem().toString(), true) != null) {
			log.error("DUPLICATE ADD OPERATION FOR ITEM [" + item.getItem().toString() + "]");
		}

		log.info("LISTENER: Item [" + item.getItem().toString() + "] added to queue [" + this.queue.getName()
				+ "] by member [" + item.getMember() + "]");
		
		manager.lookQueuesForTask();
	}

	@Override
	public void itemRemoved(ItemEvent<Task<?>> item) {
		if (this.removedEventChecking.putIfAbsent(item.getItem().toString(), true) != null) {
			log.error("DUPLICATE REMOVE OPERATION FOR ITEM [" + item.getItem().toString() + "]");
		}

		log.info("LISTENER: Item [" + item.getItem().toString() + "] removed from queue [" + this.queue.getName()
				+ "] by member [" + item.getMember() + "]");
	}
}