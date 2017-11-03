package es.codeurjc.distributed.algorithm;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker {
	
	public static void main(String[] args) {
		
		final Logger logger = LoggerFactory.getLogger(Worker.class);

		// Environment variables
		String hazelcastConfigPath = "--hazelcast-config=src/main/resources/hazelcast-config.xml";
		String mode = "--mode=RANDOM";

		logger.warn(Arrays.asList(args).toString());

		if (args.length > 0 && args[0].equals("--spring.output.ansi.enabled=always")) {
			// Eclipse execution
			if (args.length > 2) hazelcastConfigPath = args[2];
			if (args.length > 3) mode = args[3];
		} else {
			// Command line execution
			if (args.length > 1) hazelcastConfigPath = args[1];
			if (args.length > 2) mode = args[2];
		}
		new Node().start(hazelcastConfigPath.substring(hazelcastConfigPath.lastIndexOf("=") + 1), mode.substring(mode.lastIndexOf("=") + 1));
	}

}
