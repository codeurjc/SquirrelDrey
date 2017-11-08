SquirrelDrey
===

## Table of contents

* [Introduction](#introduction)
* [Code example](#code-example-squirrel-drey-hello-world)
* [Running sample applications](#running-sample-applications)
* [Building your own app](#building-your-own-app)
* [API](#api)
* [Some thoughts about Hazelcast approach compared to other alternatives](#some-thoughts-about-hazelcast-approach-compared-to-other-alternatives)

----------

## Introduction

SquirrelDrey is a Java framework aimed to support distributed execution of algorithms thanks to Hazelcast technology. SquirrelDrey is a **Task-Driven Framework**: the logic of any custom algorithm must be contained in its custom tasks. This means:

- Every **Algorithm** must have one initial **Task**
- Every **Algorithm** must have one solving **Task**

The initial Task will generate as much other tasks as needed. In the same manner, these can also generate other tasks. All of them will be executed in a distributed cluster. To control the internal logic of the custom algorithm Hazelcast objects may be used. For example, a distributed Latch can be used to make certain task generate another different one only when certain condition is met. Or a distributed Map can be useful for storing any intermediate task's result. For further information, see [Hazelcast Docs](http://docs.hazelcast.org/docs/latest-development/manual/html/Preface/Hazelcast_IMDG_Editions.html).

Any Task can act as a solving task just by calling `Task.algorithmSolved()` method.

----------

## Code example (*squirrel-drey-hello-world*)

We will explain our _hello-world_ sample app (***squirrel-drey-hello-world***). This app runs an algorithm with 3 types of tasks: **PreparationTask**, **AtomicTask** and **SolveTask**. 

Our **PreparationTask** will act as the initial task for the algorithm. It generates 10 **AtomicTask**, that simply wait for 5 seconds and set their result as '1'. The last executed **AtomicTask** will generate one **SolveTask**, which sums all the results from all **AtomicTask** and ends the algorithm (the final result will be the number of AtomicTasks executed).

<p align="center">
  <img src="https://docs.google.com/uc?id=1AQIGutVrqJLyq4JgezMAmTmoaeK65p8o">
</p>

To control which **AtomicTask** should generate the only **SolveTask**, we make use of a distributed *AtomicLong*, provided by Hazelcast. **PreparationTask** initilizes this parameter to the number of **AtomicTasks** and each **AtomicTask** decrements it at the end of its `process()` method. When any **AtomicTask** decrements the value to 0 it will mean that it is indeed the last of its kind and it will add one **SolveTask**.

This flow means that both **PreparationTask** and **SolveTask** block the execution: **PreparationTask** will always be the first Task executed and **SolveTask** the last one. In principle, we don't know (and we don't mind) the order of execution of the **AtomicTasks**.

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

----------

## Running sample applications

### squirrel-drey-hello-world

**Clone and build the project**
```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey-hello-world
mvn -DskipTests=true package
```

**Launch a worker**
```
java -Dworker=true -jar target/squirrel-drey-hello-world-0.0.1.jar
```

**Launch app** *(different console window)*
```
java -Dworker=false -jar target/squirrel-drey-hello-world-0.0.1.jar
```

The output of the app will show the solving process, displaying the state of the workers in real time, and will end showing the final result.


### squirrel-drey-sample-app

**Clone and build the project**
```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey-sampleapp
mvn -DskipTests=true package
```

**Launch a worker**
```
java -Dworker=true -Dhazelcast-config=src/main/resources/hazelcast-config.xml -Dmode=PRIORITY -jar target/squirrel-drey-sampleapp-0.0.1.jar
```

**Launch sampleapp** *(different console window)*
```
java -Dworker=false -Dhazelcast-client-config=src/main/resources/hazelcast-client-config.xml -Daws=false -jar target/squirrel-drey-sampleapp-0.0.1.jar
```

You will have the web app available at [localhost:5000](http://localhost:5000). You can launch different algorithms with different configurations at the same time, and they will execute making use of all the launched workers. You can dinamically add or remove workers and see the behaviour and performance of the algorithm's execution.

----------

## Building your own app

Your project must have the following dependency:

```
<dependency>
	<groupId>es.codeurjc</groupId>
	<artifactId>squirrel-drey</artifactId>
	<version>0.0.1</version>
</dependency>
```

Because of the way Hazelcast manages distributed objects, your application will have to be responsible of launching the workers (for security reasons, custom objects cannot be sent between nodes if their classes are not purposely declared and available on the node's classpath).
An easy way of managing this situation is by using command line options to choose whether to launch you application or a worker (with static method `es.codeurjc.distributed.algorithm.Worker.main()`).

*squirrel-drey-sampleapp* does it just like this. Summarizing its `main` method:

```
public static void main(String[] args) {

	boolean isWorker = Boolean.valueOf(System.getProperty("worker"));

	if (!isWorker) {
		SpringApplication.run(Web.class);
	} else {
		Worker.launch();
	}
	
}
```

So, our **application** will start up if we launch the JAR the following way:

`java -Dworker=false -jar sampleapp.jar`

But one **worker** will be launched if done like this:

`java -Dworker=true -jar sampleapp.jar`

----------

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

## Some thoughts about Hazelcast approach compared to other alternatives

SquirrelDrey framework relies on Hazelcast, but other alternatives could be used. In this section we will compare the two current main options available to deal with distribution of algorithms on clusters, taking Hazelcast and Apache Flink as representatives for each approach. We will also discuss why Hazelcast is the final chosen technology.

First of all, both frameworks share similar architecures. Users can launch slave nodes to build one cluster, and clients that can communicate with the cluster are available to use in Java applications.

*Hazelcast* stands for the **imperative** approach, while *Apache Flink* represents the **declarative** approach. A good analogy to ilustrate this statement can be set with Java 8 Stream API. These code snippets will return the same result *("4", "16", "36")* :

```
public List<Double> imperative() {
	List<Double> sourceList = Arrays.asList("1", "2", "3", "4", "5", "6");
	List<Double> resultList = new ArrayList<>();
    for (Integer i : input) {
        if (i % 2 == 0){
            resultList.add(Math.sqrt(i));
        }
    }
    return result;
}

public List<Double> declarative() {
	List<Double> sourceList = Arrays.asList("1", "2", "3", "4", "5", "6");
    return sourceList.stream()
            .filter(i -> i % 2 == 0)
            .map(Math::sqrt)
            .collect(Collectors.toCollection(
                () -> new ArrayList<>()));
}
```

Both functions return the same list, but the second one makes use of the Stream API and lambda functions as compared with the traditional loop of the first one.

Now let's outline the distribution of a list of tasks (`Callable` objects) on a cluster. Let's suppose our tasks are:

```
public class Task implements Callable<Void>, Serializable {
	@Override
	public Void call() throws Exception {
		System.out.println("Task running!");
		return null;
	}
}
```

#### With Hazelcast

The client may insert tasks on a distributed queue (got thanks to a `HazelcastInstance` object):

```
public void imperativeHAZELCAST(List<Task> tasks) {
	Queue<Task> distributedQueue = hazelcastInstance.getQueue("queue");
    for (Task task : tasks) {
       distributedQueue.put(task);
    }
}
```

Slave nodes just need to indefinitely poll from the distributed queue and run the `call` method of the task.

#### With Apache Flink

```
public void declarativeFLINK(List<Task> tasks) {
	ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
	DataSet<Task> tasks = environment.fromCollection(tasks);
    tasks.flatMap((task) -> {
		task.call();
	});
	env.execute("my_app");
}
```

Client uses the Java API offered by Flink to build the execution pipeline and launch the job.

For this extremely simple code, Flink option may seem like a good, clean choice, but things get much tougher when extending the pipeline and implementing scalability:

- With Hazelcast approach users have **total control over the tasks**. It is a mandatory requirement to implement some logic in order to let slave nodes know when to poll from the queue of tasks, but that's precisely what SquirrelDrey offers. Just by implementing the task's logic, users can dynamically build pipelines to be distributed among nodes. And because Hazelcast offers a *In Memory Data Grid* framework, sharing information between nodes is very easy. Do you want the 10th *MyTask* to generate a *MyOtherTask*? Hazelcast offers an `AtomicLong` object that can be set to 10 and can be decremented by every *MyTask*, and directly after in the code check if that value is 0. If so, just make *MyTask* push a new *MyOtherTask* to the distributed queue. Or maybe you want to store the results of every *MyTask* to be consumed by a future task. No problem: just store them in the same code of *MyTask* in a distributed Map.  In short: *MyTask* custom code can handle all this logic in an easy, traditional way.
- On the other hand, Apache Flink is at first much more limited regarding the control users have. Because of the declarative format, our pipeline must be fully declared on the client so Flink can build its internal DAG ([Directed Acyclic Graph](https://en.wikipedia.org/wiki/Directed_acyclic_graph)). The previous examples would work in a different way: Flink pipeline needs to receive a list of 10 *MyTask*, and thanks to a *reduce* function wait to all of them to be finished. To store the results, every *MyTask* should add its own to a *Collector* object, and the inner magic of Flink can handle the retrieval of the final result on the *reduce* function.

To sum up,  for algorithms implemented with Java, using the imperative Hazelcast approach means to have a comfortable, dynamic and object-oriented way of building the execution pipeline, while by using the functional Apache Flink approach means to deal with a framework ideal for processing huge data streams, such as search algorithms or word processing applications (in fact any algorithm based on a huge amount of similar and simple inputs on which to apply some transformation or reduction). But not so convenient for algorithms of other nature, containing tasks with more complex inputs, processing and communications among them.

To conclude, it is also worth mentioning the state of **scalability** and **fault tolerance** on both technologies. 

#### Fault tolerance
Both of them support **fault tolerance**: Apache Flink can store the execution state of one pipeline, and SquirrelDrey adds some logic to ensure that if one node unexpectedly goes down, other node will execute the running tasks lost on the terminated node. The main difference between them is that Apache Flink requires a hard restart in order to be able to resume the execution. Our Hazelcast approach is implemented so the cluster status doesn't change in the event of a node crash: every other node will smoothly continue its execution, and the lost tasks will be executed (with high priority) over the next iterations of the remaining nodes.

#### Scalability
In terms of **scalability**, Apache Flink has a very important restriction: the parallelism must be declared on the cluster configuration or on the pipeline code. This means that when any node is dynamically added to the cluster, a whole reset of the cluster and re-configuration is needed for the new node to be fully exploited. This is supposed in a AWS scenario using a cluster made up of simple EC2 machines. That being said, Amazon offers a service called EMR (Elastic MapReduce) that can be used along Apache Flink and suitable for (returning to what has been said before) some specific kind of algorithms. SquirrelDrey behaviour makes scalability pretty easy: since nodes simply poll from the distributed queue, a new node will start polling when launched. Nobody cares about configuring parallelism: nodes are configured by default to accept as many tasks as cores to maximize performance and CPU usage. The only difference to other nodes is that maybe the task that one of them was going to poll can now be taken by the new node.


As a final thought, both Hazelcast and Apache offer opposite frameworks: Apache Ignite is very similar to Hazelcast IMDG, and Hazelcast Jet is very similar to Apache Flink.

