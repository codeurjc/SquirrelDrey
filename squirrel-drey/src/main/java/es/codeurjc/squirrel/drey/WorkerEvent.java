package es.codeurjc.squirrel.drey;

import java.io.Serializable;

public class WorkerEvent implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	String workerId;
	String name;
	Object content;
	
	public WorkerEvent(String workerId, String name, Object content) {
		this.workerId = workerId;
		this.name = name;
		this.content = content;
	}
	
	public String getWorkerId() {
		return this.workerId;
	}
	
	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}

}
