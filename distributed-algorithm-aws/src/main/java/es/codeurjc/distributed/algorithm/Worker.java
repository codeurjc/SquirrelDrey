package es.codeurjc.distributed.algorithm;

public class Worker {

	public static void main(String[] args) {
		
		// Environment variables
		String hazelcastConfigPath = System.getProperty("hazelcast-config") != null ? 
				System.getProperty("hazelcast-config") : 
				"src/main/resources/hazelcast-config.xml";
				
		Mode mode = System.getProperty("mode") != null ? 
				Mode.valueOf(System.getProperty("mode")) : 
				Mode.RANDOM;
		
		new Node().start(hazelcastConfigPath, mode);
	}

	public static void launch() {
		Worker.main(new String[0]);
	}

}
