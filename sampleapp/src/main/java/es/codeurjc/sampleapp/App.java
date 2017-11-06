package es.codeurjc.sampleapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

import es.codeurjc.distributed.algorithm.Worker;

/**
 * @author Pablo Fuente (pablo.fuente@urjc.es)
 */
public class App {
	
	public static final Logger logger = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) {
		
		boolean isWorker = System.getProperty("worker") != null ? Boolean.valueOf(System.getProperty("worker")) : true;
		
		if (!isWorker) {
			SpringApplication.run(Web.class);
		} else {
			Worker.launch();
		}
	}
}
