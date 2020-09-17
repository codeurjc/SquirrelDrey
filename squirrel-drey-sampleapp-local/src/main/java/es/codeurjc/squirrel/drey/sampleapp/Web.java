package es.codeurjc.squirrel.drey.sampleapp;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import es.codeurjc.squirrel.drey.AlgorithmManager;

@SpringBootApplication
public class Web {
	
	@Bean
	public AlgorithmManager<String> clusterManager(final ApplicationArguments args) {
		return new AlgorithmManager<>();
	}

}
