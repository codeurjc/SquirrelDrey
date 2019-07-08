package es.codeurjc.squirrel.drey.loadapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

import es.codeurjc.squirrel.drey.Worker;

/**
 * @author Pablo Fuente (pablofuenteperez@gmail.com)
 */
public class App {
	
	public static final Logger logger = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) {
		
		boolean isWorker = System.getProperty("worker") != null ? Boolean.valueOf(System.getProperty("worker")) : true;
		boolean isDevMode = System.getProperty("devmode") != null ? Boolean.valueOf(System.getProperty("devmode")) : false;
		
		if (!isWorker || isDevMode) {
			SpringApplication.run(Web.class);
		} else {
			Worker.launch();
		}
	}
}
