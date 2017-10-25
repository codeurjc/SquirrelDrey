package io.pablofuente.distributed.algorithm.aws.project;

import java.io.Serializable;

public class MyEvent implements Serializable {

	private static final long serialVersionUID = 1L;

	String projectId;
	String name;
	Object content;

	public MyEvent(String projectId, String name, Object content) {
		this.projectId = projectId;
		this.name = name;
		this.content = content;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
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
