package es.codeurjc.squirrel.drey.sampleapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.platform.runner.JUnitPlatform;

import es.codeurjc.squirrel.drey.local.AlgorithmManager;
import es.codeurjc.squirrel.drey.local.Mode;
import es.codeurjc.squirrel.drey.sampleapp.App;
import es.codeurjc.squirrel.drey.sampleapp.task.PreparationTask;

//TODO: Reimplement these tests
@RunWith(JUnitPlatform.class)
public class AppTest {
	
	Map<String, String> results = new HashMap<>();
	Map<String, CountDownLatch> latches = new HashMap<>();
	Map<String, Long> times = new HashMap<>();
	
	@AfterEach
	void dispose() {
		disposeAll();
	}
	
	@Test
	@Disabled
	public void testApp() throws InterruptedException {
		launchWorker(Mode.RANDOM);
		Thread.sleep(4000);
		launchApp();
	}
	
	@Test
	@Disabled
	public void testSimpleSolveOneAlgorithm() throws Exception {
		testAlgorithmsWorkers(1, 1, Mode.PRIORITY);
	}
	
	@Test
	public void testRandomThreeAlgorithms() throws Exception {
		testAlgorithmsWorkers(3, 1, Mode.RANDOM);
		checkTimeDifferences(0, 5, times.values());
	}
	
	@Test
	public void tesPriorityThreeAlgorithms() throws Exception {
		testAlgorithmsWorkers(3, 1, Mode.PRIORITY);
		checkTimeDifferences(7, 100, times.values());
	}
	
	private void testAlgorithmsWorkers(int nAlgorithms, int nWorkers, Mode mode) throws InterruptedException {
		
		int nTasks = 20;
		
		for (int i = 0; i < nWorkers - 1; i++) {
			launchWorker(mode);
		}
		// Last worker launched synchronously
		launchWorker(mode);
		
		AlgorithmManager<String> manager = new AlgorithmManager<>();
		
		Set<Thread> threads = new HashSet<>();
		for (int i = 1; i <= nAlgorithms; i++) {
			final int j = i;
			Thread t = new Thread(() -> {
				String algId = "testAlgorithm" + j;
				latches.put(algId, new CountDownLatch(1));
				try {
					times.put(algId, System.currentTimeMillis());
					manager.solveAlgorithm(algId, new PreparationTask("test_input_data" + j, nTasks, 5), j + 1, (r) -> {
						results.put(algId, r);
						latches.get(algId).countDown();
						times.put(algId, System.currentTimeMillis() - times.get(algId));
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
				try {
					assertTrue(latches.get(algId).await(50, TimeUnit.SECONDS));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
			threads.add(t);
		}
		
		ExecutorService es = Executors.newCachedThreadPool();
		for(Thread t : threads) {
		    es.execute(t);
		}
		es.shutdown();
		assertTrue(es.awaitTermination(60, TimeUnit.SECONDS));
		for (Entry<String, String> entry : this.results.entrySet()) {
			assertEquals(results.get(entry.getKey()), Integer.toString(nTasks*10));
		}
	}
	
	private void checkTimeDifferences(int min, int max, Iterable<Long> it){
		Iterator<Long> iterator = it.iterator();
		long prev = 0;
		if (iterator.hasNext()) prev = iterator.next();
		while (iterator.hasNext()) {
			long next = iterator.next();
			int difference = (int) Math.abs(prev/1000-next/1000);
			assertTrue((difference >= min) && (difference <= max));
			prev = next;
		}
	}
	
	private void launchWorker(Mode mode) {
		System.setProperty("mode", mode.toString());
		System.setProperty("isWorker", "true");
		App.main(new String[0]);
	}
	
	private void launchApp() {
		System.setProperty("isWorker", "false");
		App.main(new String[0]);
	}
	
	private void disposeAll() {
		System.out.println("SHUTTING DOWN ALL INSTANCES...");

		this.results.clear();
		this.latches.clear();
		this.times.clear();
		System.out.println("...ALL INSTANCES ARE DOWN");
	}
	
}
