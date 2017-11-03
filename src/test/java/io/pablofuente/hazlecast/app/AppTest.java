package io.pablofuente.hazlecast.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.platform.runner.JUnitPlatform;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import es.codeurjc.distributed.algorithm.AlgorithmManager;
import es.codeurjc.distributed.algorithm.Mode;
import es.codeurjc.sampleapp.App;
import es.codeurjc.sampleapp.SamplePreparationTask;

@RunWith(JUnitPlatform.class)
public class AppTest {
	
	Map<String, String> results = new ConcurrentHashMap<>();
	Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();
	
	@AfterEach
	void dispose() {
		disposeAll();
	}
	
	@Test
	public void testApp() throws InterruptedException {
		launchWorker(Mode.RANDOM);
		Thread.sleep(4000);
		launchApp();
	}
	
	@Test
	public void testSimpleSolveOneAlgorithm() throws Exception {
		testAlgorithmsWorkers(1, 1, Mode.PRIORITY);
	}
	
	@Test
	public void testRandomThreeAlgorithms() throws Exception {
		testAlgorithmsWorkers(3, 3, Mode.RANDOM);
	}
	
	private void testAlgorithmsWorkers(int nAlgorithms, int nWorkers, Mode mode) throws InterruptedException {
		
		for (int i = 0; i < nWorkers; i++) {
			launchWorker(mode);
		}
		
		AlgorithmManager<String> manager = new AlgorithmManager<>("src/main/resources/hazelcast-client-config.xml", false);
		
		Set<Thread> threads = new HashSet<>();
		for (int i = 1; i <= nAlgorithms; i++) {
			final int j = i;
			Thread t = new Thread(() -> {
				String algId = "testAlgorithm" + j;
				latches.put(algId, new CountDownLatch(1));
				try {
					manager.solveAlgorithm(algId, new SamplePreparationTask("test_input_data" + j, 10, 3, 0, "test_atomic_long_id" + j), 1, (r) -> {
						results.put(algId, r);
						latches.get(algId).countDown();
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					assertTrue(latches.get(algId).await(30, TimeUnit.SECONDS));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				assertEquals(results.get(algId), "100");
			});
			threads.add(t);
		}
		
		ExecutorService es = Executors.newCachedThreadPool();
		for(Thread t : threads) {
		    es.execute(t);
		}
		es.shutdown();
		assertTrue(es.awaitTermination(40, TimeUnit.SECONDS));
	}
	
	private void launchWorker(Mode mode) {
		String[] args = {"--web=false", "--hazelcast-config=src/main/resources/hazelcast-config.xml", "--mode=" + mode};
		App.main(args);
	}
	
	private void launchApp() {
		String[] args = {"--web=true", "--hazelcast-client-config=src/main/resources/hazelcast-client-config.xml", "--aws=false"};
		App.main(args);
	}
	
	private void disposeAll() {
		System.out.println("SHUTTING DOWN ALL INSTANCES...");
		for (HazelcastInstance hc : Hazelcast.getAllHazelcastInstances()){
			hc.shutdown();
		}
		while(!Hazelcast.getAllHazelcastInstances().isEmpty()){
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("...ALL INSTANCES ARE DOWN");
		this.results.clear();
		this.latches.clear();
	}
}
