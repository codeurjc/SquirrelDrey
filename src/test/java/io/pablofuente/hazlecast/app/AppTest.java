package io.pablofuente.hazlecast.app;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import es.codeurjc.distributed.algorithm.AlgorithmManager;
import es.codeurjc.sampleapp.App;
import es.codeurjc.sampleapp.SamplePreparationTask;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AppTest extends TestCase {
	
	String result;

	public AppTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(AppTest.class);
	}
	
	public void testSimpleSolveAlgorithm() throws Exception {
		launchWorker();
		Thread.sleep(5000);
		
		CountDownLatch latch = new CountDownLatch(1);
		
		AlgorithmManager<String> manager = new AlgorithmManager<>("src/main/resources/hazelcast-client-config.xml", false);
		manager.solveAlgorithm("testAlgorithm", new SamplePreparationTask("test_input_data", 10, 3, 0, "test_atomic_long_id"), 1, (r) -> {
			result = r;
			latch.countDown();
		});
		
		assertTrue(latch.await(15, TimeUnit.SECONDS));
		assertEquals(result, "10");
		
	}
	
	private void launchWorker() {
		String[] args = {"--web=false", "--hazelcast-config=src/main/resources/hazelcast-config.xml", "--mode=RANDOM"};
		App.main(args);
	}
	
	private void launchApp() {
		String[] args = {"--web=true", "--hazelcast-client-config=src/main/resources/hazelcast-client-config.xml", "--aws=false"};
		App.main(args);
	}
}
