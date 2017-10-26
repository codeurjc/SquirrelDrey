package io.pablofuente.distributed.algorithm.aws.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Web {
	
	@Bean
	public ProjectManager clusterManager() {
		return new ProjectManager();
	}

}
