package io.pablofuente.distributed.algorithm.aws.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Web {
	
	@Bean
	public ProjectManager clusterManager() {
		return new ProjectManager();
	}

}
