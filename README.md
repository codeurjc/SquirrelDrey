distributed-algorithm-aws
===

This project is a Java framework aimed to support distributed execution of algorithms thanks to Hazelcast technology. It is a **Task-Driven Framework**: the logic of any custom algorithm must be contained in its custom tasks. This means:

- Every **Algorithm** must have one initial **Task**
- Every **Algorithm** must have one solving **Task**

The initial Task will generate as much other tasks as needed. In the same manner, these can also generate other tasks. All of them will be executed in a distributed cluster. To control the internal logic of the custom algorithm Hazelcast objects may be used. For example, a distributed Latch can be used to make certain task generate another different one only when certain condition is met. Or a distributed Map can be useful for storing any intermediate task's result. For further information, see [Hazelcast Docs](http://docs.hazelcast.org/docs/latest-development/manual/html/Preface/Hazelcast_IMDG_Editions.html).

Any Task can act as a solving task just by calling `Task.algorithmSolved()` method.

## API

| Class  | T | Description  |
|---|---|---|
| `AlgorithmManager<T>`  | Class of the algorithm's final result. Must be a Serializable object  | Centralized manager object for launching algorithms and getting their result   |
| `Task<T>`  | Class of the task's final result. Must be a Serializable object  | Callable objects that will be executed asynchronously in a distributed cluster |


#### AlgorithmManager< T > 

| Method  | Params | Returns  | Description |
|---|---|---|---|
| `solveAlgorithm`  | `String:algorithmId`<br>`Task<?>:initialTask`<br>`Integer:priority`<br>`Consumer<R>:callback`  | void | Solves the algorithm identified by `algorithmId`, with `initialTask` as the first Tassk to be executed, with certain `priority` (1 > 2 > 3...) and running `callback` function when the final result is available |
| `terminateAlgorithms`  |  | void | Stops the execution of all running algorithms, forcing their termination |
| `blockingTerminateAlgorithms`  |  | void | Stops the execution of all running algorithms, forcing their termination. The method will not return until all the distributed structures are not empty and properly stopped |
| `blockingTerminateOneAlgorithm`  | `String:algorithmId` | void | Stops the execution of algorithm with id `algorithmId`, forcing its termination. The method will not return until all the distributed structures related to this algorithm are not empty and properly stopped |


#### Task< T > 

| Method  | Params | Returns  | Description |
|---|---|---|---|
| `addNewTask`  | `Task<?>:task` | void | Add a new Task to the algorithm |
| `process`  |  | void | Main code of the distributed task |
| `setResult`  | `T:result` | void | Sets the final result for this task. Usually this method is called inside `Task.process` method |
| `algorithmSolved`  | `R:finalResult` | void | This method will finish the Algorithm< R >, setting `finalResult` as the global final result for the algorithm |
| `getId`  | void | `int` | Returns the unique identifier for this task |

## Code example

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

## Running *sampleapp*

To run the sample application (**sampleapp**), execute the following commands:

**Clone and build the project**
```
git clone https://github.com/codeurjc/distributed-algorithm-aws.git
cd distributed-algorithm-aws/distributed-algorithm-aws
mvn install
cd ../sampleapp
mvn -DskipTests=true package
```

**Launch a worker**
```
java -jar target/distributed-algorithm-aws-sampleapp-0.0.1-SNAPSHOT.jar "--worker=true" "--hazelcast-config=src/main/resources/hazelcast-config.xml" "--mode=RANDOM"
```

**Launch sampleapp** *(different console window)*
```
java -jar target/distributed-algorithm-aws-sampleapp-0.0.1-SNAPSHOT.jar "--worker=false" "--hazelcast-client-config=src/main/resources/hazelcast-client-config.xml" "--aws=false"
```

You will have the app available at [localhost:5000](http://localhost:5000). You can launch different algorithms with different configurations at the same time, and they will execute making use of all the launched workers. You can dinamically add or remove workers and see the behaviour and performance of the algorithm's execution.

## Building your own app

Your project must have the following dependency:

```
<dependency>
	<groupId>es.codeurjc</groupId>
	<artifactId>distributed-algorithm-aws</artifactId>
	<version>0.0.1</version>
</dependency>
```

Because of the way Hazelcast manages distributed objects, your application will have to be responsible of launching the workers (for security reasons, custom objects cannot be sent between nodes if their classes are not purposely declared and available on the node's classpath).
An easy way of managing this situation is by using command line options to choose whether to launch you application or a worker (with static method `es.codeurjc.distributed.algorithm.Worker.main()`).

*sampleapp* does it just like this. Summarizing its `main` method:

```
public static void main(String[] args) {

	String modeOfExecution = args[0];
	if (modeOfExecution.equals("app")) {
		SpringApplication.run(Web.class);
	} else if (modeOfExecution.equals("worker")) {
		Worker.main(args);
	}
	
}
```

So, our **application** will start up if we launch the JAR the following way:

`java -jar sampleapp.jar "app"`

But one **worker** will be launched if done like this:

`java -jar sampleapp.jar "worker"`
