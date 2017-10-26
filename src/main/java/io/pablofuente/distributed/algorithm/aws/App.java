package io.pablofuente.distributed.algorithm.aws;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;

import io.pablofuente.distributed.algorithm.aws.web.Web;
import io.pablofuente.distributed.algorithm.aws.worker.Node;

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

	public static void main(String[] args) {
		
		// Environment variables
		String modeOfExecution;
		String hazelcastConfigPath;
		
		System.out.println(Arrays.asList(args));
		
		if (args[0].equals("--spring.output.ansi.enabled=always")) {
			// Eclipse execution
			modeOfExecution = args[1];
			hazelcastConfigPath = args[2];
		} else {
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
