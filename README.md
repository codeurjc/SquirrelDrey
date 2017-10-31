distributed-algorithm-aws
===

This project is a Java framework aimed to support distributed execution of algorithms thanks to Hazelcast technology. It is a **Task-Driven Framework**: the logic of any custom algorithm must be contained in its custom tasks. This means:

- Every **Algorithm** must have one initial **Task**
- Every **Algorithm** must have one solving **Task**

The initial Task will generate as much other tasks as needed. In the same manner, these can also generate other tasks. All of them will be executed in a distributed cluster. To control the internal logic of the custom algorithm Hazelcast objects may be used. For example, a distributed Latch can be used to make certain task generate another different one. Or a distributed Map can be useful for storing results. For further information, see [Hazelcast Docs](http://docs.hazelcast.org/docs/latest-development/manual/html/Preface/Hazelcast_IMDG_Editions.html).

Any Task can act as a solving task just by calling `Task.algorithmSolved()` method.

## API

| Class  | T | Description  |
|---|---|---|
| `AlgorithmManager<T>`  | Class of the algorithm's final result. Must be a Serializable object  | Centralized manager object for launching algorithms and getting their result   |
| `Task<T>`  | Class of the task's final result. Must be a Serializable object  | Callable objects that will be executed asynchronously in a distributed cluster |


#### AlgorithmManager< T > 

| Method  | Params | Returns  | Description |
|---|---|---|
| `solveAlgorithm`  | `String algorithmId`<br>`Task<?> initialTask`<br>`Integer priority`<br>`Consumer<R> callback`  | void | Solves the algorithm identified by `algorithmId`, with `initialTask` as the first Tassk to be executed, with certain `priority` (1 > 2 > 3...) and running `callback` function when the final result is available |
| `terminateAlgorithms`  |  | void | Stops the execution of all running algorithms, forcing their termination |
| `blockingTerminateAlgorithms`  |  | void | Stops the execution of all running algorithms, forcing their termination. The method will not return until all the distributed structures are not empty and properly stopped |

#### Task< T > 

| Method  | Params | Returns  | Description |
|---|---|---|
| `addNewTask`  | `Task<?> task` | void | Add a new Task to the algorithm |
| `process`  |  | void | Main code of the distributed task |
| `setResult`  | `T result` | void | Sets the final result for this task. Usually this method is called inside `Task.process` method |
| `algorithmSolved`  | `R finalResult` | void | This method will finish the Algorithm< R >, setting `finalResult` as the global final result for the algorithm |

## Example

This is an Algorithm with 3 types of tasks: **PreparationTask**, **AtomicTask** and **SolveTask**. Our PreparationTask will act as the initial task for the algorithm. It generates 10 SampleTask, that simply wait for 5 seconds and set their result as '1'. The last SampleTask will generate one SolveTask, which sums all the results from all AtomicTasks and ends the algorithm (the final result will be the number of AtomicTasks executed).

```
AlgorithmManager<String> manager = new AlgorithmManager<>();
Task<Void> initialTask = new PreparationTask(10);

manager.solveAlgorithm("sample_algorithm", initialTask, 1, (result) -> {
	System.out.println("MY RESULT: " + result);
});
```

```
public class PreparationTask extends Task<Void> {
	
	private Integer numberOfAtomicTasks;

	public PreparationTask(Integer numberOfAtomicTasks) {
		this.numberOfAtomicTasks = numberOfAtomicTasks;
	}

	@Override
	public void process() throws Exception {
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong("my_countdown");
		atomicLong.set(this.numberOfTasks);
		
		List<AtomicTask> atomicTasks = new ArrayList<>();
		for (int i = 0; i < this.numberOfAtomicTasks; i++) {
			atomicTasks.add(new AtomicTask());
		}
		
		for (AtomicTask t : atomicTasks) {
			try {
				addNewTask(t);
				publishQueueStats();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
```

```
public class AtomicTask extends Task<Integer> {

	public AtomicTask() {	}

	@Override
	public void process() throws Exception {
		Thread.sleep(5000);
		
		this.setResult(1);
		
		IMap<Integer, Integer> results = hazelcastInstance.getMap("my_results");
		IAtomicLong atomicLong = hazelcastInstance.getAtomicLong("my_countdown");
		results.put(this.getId(), this.getResult());
		
		if (atomicLong.decrementAndGet() == 0L) {
			System.out.println("ADDING SOLVE TASK FOR ALGORITHM " + this.algorithmId);
			addNewTask(new SolveTask());
		}
	}
}
```

```
public class SolveTask extends Task<String> {

	@Override
	public void process() throws Exception {
		Map<Integer, Integer> results = hazelcastInstance.getMap("my_results");
		
		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}
		
		this.setResult(Integer.toString(finalResult));
		this.algorithmSolved(Integer.toString(finalResult));
	}
}
```
