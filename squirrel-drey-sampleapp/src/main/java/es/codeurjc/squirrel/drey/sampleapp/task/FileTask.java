package es.codeurjc.squirrel.drey.sampleapp.task;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import es.codeurjc.squirrel.drey.Task;

public class FileTask extends Task {

	private static final long serialVersionUID = 1L;

	private int fileId;
	private String result;

	public FileTask(int fileId, String result) {
		this.fileId = fileId;
		this.result = result;
	}

	@Override
	public void process() throws Exception {
		
		Thread.sleep(2000);
		
		Path file = Paths.get("file-" + this.algorithmId + "-" + this.fileId + ".txt");
		Files.write(file, Arrays.asList(new String[]{"FILE " + this.fileId + " - ALGORITHM " + this.algorithmId + " - RESULT: " + result}), Charset.forName("UTF-8"));
		
		System.out.println("WRITING FILE " + "file-" + this.algorithmId + "-" + this.fileId + ".txt");
		
		if (this.getAtomicLong("file_tasks").decrementAndGet() == 0) {
			addNewTask(new SolveTask());
		}
	}

}
