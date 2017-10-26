package io.pablofuente.distributed.algorithm.aws.web;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Web {
	
	@Bean
	public ProjectManager clusterManager(final ApplicationArguments args) {
		return new ProjectManager(args.getSourceArgs()[0]);
	}

}
