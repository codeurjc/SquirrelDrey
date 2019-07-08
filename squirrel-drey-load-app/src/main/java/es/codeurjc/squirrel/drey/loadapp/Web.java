package es.codeurjc.squirrel.drey.loadapp;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import es.codeurjc.squirrel.drey.AlgorithmManager;

@SpringBootApplication
public class Web {
	
	@Bean
	public AlgorithmManager<String> clusterManager(final ApplicationArguments args) {
		
		String hazelcastConfigPath = System.getProperty("hazelcast-config") != null ? 
				System.getProperty("hazelcast-config") : 
				"src/main/resources/hazelcast-config.xml";
		boolean withAWS = System.getProperty("aws") != null ? Boolean.valueOf(System.getProperty("aws")) : false;
		
		return new AlgorithmManager<>(hazelcastConfigPath, withAWS);
	}

}
