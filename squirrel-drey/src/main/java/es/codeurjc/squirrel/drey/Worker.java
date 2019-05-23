package es.codeurjc.squirrel.drey;

public class Worker {

	public static void main(String[] args) {

		// Environment variables
		String hazelcastConfigPath = System.getProperty("hazelcast-config") != null
				? System.getProperty("hazelcast-config")
				: "src/main/resources/hazelcast-config.xml";

		Mode mode = System.getProperty("mode") != null ? Mode.valueOf(System.getProperty("mode")) : Mode.RANDOM;
		int idleCores = System.getProperty("idle-cores-worker") != null
				? Integer.parseInt(System.getProperty("idle-cores-worker"))
				: 1;

		System.out.println("Worker starting...");

		new Node().start(hazelcastConfigPath, mode, idleCores);
	}

	public static void launch() {
		Worker.main(new String[0]);
	}

}
