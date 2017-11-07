package es.codeurjc.squirrel.drey.sampleapp.task;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import es.codeurjc.squirrel.drey.Task;

public class FileTask extends Task<String> {

	private static final long serialVersionUID = 1L;

	private int fileId;
	private String result;

	public FileTask(int fileId, String result) {
		this.fileId = fileId;
		this.result = result;
	}

	@Override
	public void process() throws Exception {
		
		Path file = Paths.get("file-" + this.algorithmId + "-" + this.fileId + ".txt");
		Files.write(file, Arrays.asList(new String[]{"FILE " + this.fileId + ". RESULT: " + result}), Charset.forName("UTF-8"));

		if (this.hazelcastInstance.getAtomicLong("file_tasks").decrementAndGet() == 0) {
			addNewTask(new SolveTask());
		}
	}

}
