package es.codeurjc.sampleapp;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

import es.codeurjc.distributed.algorithm.Node;

/**
 * Sample application to split the execution of one expensive algorithm into
 * different Hazelcast members. The algorithm has the following behaviour:
 * 
 * 1) Converts any string to a random stringified number
 * 
 * 2) Splits this number into groups of 5-digit numbers
 * 
 * 3) Calculates the factorial of each of these 5-digit numbers, getting the
 * result in a BigInteger Java object
 * 
 * 4) Calculates the sum of all digits in every factorial
 * 
 * 5) Calculates the sum of all these sums and returns it as an Integer
 * 
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class App {
	
	public static final Logger logger = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) {
		
		// Environment variables
		String modeOfExecution;
		String hazelcastConfigPath;
		
		App.logger.warn(Arrays.asList(args).toString());
		
		if (args[0].equals("--spring.output.ansi.enabled=always")) {
			// Eclipse execution
			modeOfExecution = args[1];
			hazelcastConfigPath = args[2];
		} else {
			// Command line execution
			modeOfExecution = args[0];
			hazelcastConfigPath = args[1];
		}
		args = new String[]{hazelcastConfigPath, hazelcastConfigPath};
		if (modeOfExecution.equals("--web=true")) {
			SpringApplication.run(Web.class, hazelcastConfigPath.substring(hazelcastConfigPath.lastIndexOf("=") + 1));
		} else {
			new Node().start(hazelcastConfigPath.substring(hazelcastConfigPath.lastIndexOf("=") + 1));
		}
	}

}
