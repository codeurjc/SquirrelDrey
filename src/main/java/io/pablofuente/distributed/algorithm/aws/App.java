package io.pablofuente.distributed.algorithm.aws;

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
		String arg;
		if (args.length == 1) {
			arg = args[0];
		} else {
			arg = args[1];
		}
		if (arg.equals("--web=true")) {
			SpringApplication.run(Web.class, args);
		} else {
			new Node().start();
		}
	}

}
