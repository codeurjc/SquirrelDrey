package es.codeurjc.sampleapp;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import es.codeurjc.distributed.algorithm.AlgorithmManager;

@SpringBootApplication
public class Web {
	
	@Bean
	public AlgorithmManager<String> clusterManager(final ApplicationArguments args) {
		return new AlgorithmManager<>(args.getSourceArgs()[0], Boolean.valueOf(args.getSourceArgs()[1]));
	}

}
