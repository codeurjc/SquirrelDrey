SquirrelDrey
===

## Table of contents

* [Introduction](#introduction)
* [Code example](#code-example-squirrel-drey-hello-world)
* [Running sample applications](#running-sample-applications)
* [Building your own app](#building-your-own-app)
* [API](#api)
* [Some thoughts about Hazelcast approach compared to other alternatives](#some-thoughts-about-hazelcast-approach-compared-to-other-alternatives)
* [Running on Amazon ECS](#running-on-amazon-ecs)
* [Local scaling version](#local-scaling-version)

----------

## Introduction

SquirrelDrey is a Java framework aimed to support distributed execution of algorithms thanks to Hazelcast technology. SquirrelDrey is a **Task-Driven Framework**: the logic of any custom algorithm must be contained in its custom tasks. This means that the entrypoint for the algorithm is no more than one initial Task.

The initial Task will generate as much other tasks as needed. In the same manner, these can also generate other tasks. All of them will be executed in a distributed cluster. To control the internal logic of the custom algorithm Hazelcast objects may be used. For example, a distributed Latch can be used to make certain task generate another different one only when certain condition is met. Or a distributed Map can be useful for storing any intermediate task's result. For further information, see [Hazelcast Docs](http://docs.hazelcast.org/docs/latest-development/manual/html/Preface/Hazelcast_IMDG_Editions.html).

Whenever SquirrelDrey founds that the number of tasks sent to be executed matches the number of completed tasks for one algorithm, it will be terminated. If any task has called method `Task.algorithmSolved(result)`, that will be the final result of the algorithm (`null` if not).

----------

## Code example (*squirrel-drey-hello-world*)

We will explain our _hello-world_ sample app (***squirrel-drey-hello-world***). This app runs an algorithm with 3 types of tasks: **PreparationTask**, **AtomicTask** and **SolveTask**. 

Our **PreparationTask** will act as the initial task for the algorithm. It generates 10 **AtomicTask**, that simply wait for 5 seconds and set their result as '1'. The last executed **AtomicTask** will generate one **SolveTask**, which sums all the results from all **AtomicTask** and ends the algorithm (the final result will be the number of AtomicTasks executed).

<p align="center">
  <img src="https://docs.google.com/uc?id=1AQIGutVrqJLyq4JgezMAmTmoaeK65p8o">
</p>

To control which **AtomicTask** should generate the only **SolveTask**, we make use of a distributed *AtomicLong*, provided by Hazelcast. **PreparationTask** initilizes this parameter to the number of **AtomicTasks** and each **AtomicTask** decrements it at the end of its `process()` method. When any **AtomicTask** decrements the value to 0 it will mean that it is indeed the last of its kind and it will add one **SolveTask**.

This flow means that both **PreparationTask** and **SolveTask** block the execution: **PreparationTask** will always be the first Task executed and **SolveTask** the last one. In principle, we don't know (and we don't mind) the order of execution of the **AtomicTasks**.

```java
AlgorithmManager<String> manager = new AlgorithmManager<>();
Task initialTask = new PreparationTask(10);

manager.solveAlgorithm("sample_algorithm", initialTask, 1, (result) -> {
	System.out.println("MY RESULT: " + result);
});
```

```java
public class PreparationTask extends Task {
	
	private Integer numberOfAtomicTasks;

	public PreparationTask(Integer numberOfAtomicTasks) {
		this.numberOfAtomicTasks = numberOfAtomicTasks;
	}

	@Override
	public void process() throws Exception {
		IAtomicLong atomicLong = this.getAtomicLong("my_countdown");
		atomicLong.set(this.numberOfAtomicTasks);
		
		for (int i = 0; i < this.numberOfAtomicTasks; i++) {
			try {
				addNewTask(new AtomicTask());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}		
	}
}
```

```java
public class AtomicTask extends Task {

	public AtomicTask() {	}

	@Override
	public void process() throws Exception {
		Thread.sleep(5000);
		
		IMap<Integer, Integer> results = (IMap<Integer, Integer>) this.getMap("my_results");
		IAtomicLong atomicLong = this.getAtomicLong("my_countdown");
		results.put(this.getId(), 1);
		
		if (atomicLong.decrementAndGet() == 0L) {
			System.out.println("ADDING SOLVE TASK FOR ALGORITHM " + this.algorithmId);
			addNewTask(new SolveTask());
		}
	}
}
```

```java
public class SolveTask extends Task {

	@Override
	public void process() throws Exception {
		IMap<Integer, Integer> results = (IMap<Integer, Integer>) this.getMap("my_results");
		
		Integer finalResult = 0;
		for (Entry<Integer, Integer> e : results.entrySet()) {
			finalResult += e.getValue();
		}
		
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
java -Dworker=true -jar target/squirrel-drey-hello-world-*.jar
```

**Launch app** *(different console window)*
```
java -Dworker=false -jar target/squirrel-drey-hello-world-*.jar
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
java -Dworker=true -Dhazelcast-config=src/main/resources/hazelcast-config.xml -Dmode=PRIORITY -jar target/squirrel-drey-sampleapp-*.jar
```

**Launch sampleapp** *(different console window)*
```
java -Dworker=false -Dhazelcast-client-config=src/main/resources/hazelcast-client-config.xml -Daws=false -jar target/squirrel-drey-sampleapp-*.jar
```

You will have the web app available at [localhost:5000](http://localhost:5000). You can launch different algorithms with different configurations at the same time, and they will execute making use of all the launched workers. You can dinamically add or remove workers and see the behaviour and performance of the algorithm's execution.

> We provide a development mode for ***squirrel-drey-sample-app***. To quickly  launch both a worker and the application at the same time on the same process, just run `java -Ddevmode=true -jar target/squirrel-drey-sampleapp-*.jar`.

----------

## Using SNAPSHOT versions

Do you want the latest version on *master* branch of SquirrelDrey, but it isn't on Maven Central yet? Just compile it locally. For example, for *squirrel-drey-hello-world*:

```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey
mvn install
cd ../squirrel-drey-hello-world
mvn -DskipTests=true clean package
```

----------

## Building your own app

Your project must have the following dependency:

```xml
<dependency>
	<groupId>es.codeurjc</groupId>
	<artifactId>squirrel-drey</artifactId>
	<version>...</version>
</dependency>
```

Because of the way Hazelcast manages distributed objects, your application will have to be responsible of launching the workers (for security reasons, custom objects cannot be sent between nodes if their classes are not purposely declared and available on the node's classpath).
An easy way of managing this situation is by using command line options to choose whether to launch you application or a worker (with static method `es.codeurjc.distributed.algorithm.Worker.launch()`).

*squirrel-drey-sampleapp* does it just like this. Summarizing its `main` method:

```java
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

| Class  | Description  |
|---|---|
| `AlgorithmManager<T>`  | Centralized manager object for launching algorithms and getting their result. `T` is the class of the algorithm's final result. Must be a Serializable object |
| `Algorithm<T>` | Represents a project with one initial Task as entry point for its execution. Stores valuable information about the Tasks added, completed and queued |
| `Task` | Callable objects that will be executed asynchronously in a distributed cluster. All classes extending it must have serializable attributes |


#### AlgorithmManager< T > 

| Method  | Params (*italics* are optional) | Returns  | Description |
|---|---|---|---|
| *constructor* | `String:hazelcastClientConfig`<br>`boolean:withAwsCloudWatch` | | New AlgorithmManager, searching for configuration file on path `hazelcastClientConfig` (default one if not found) and initializing the AWS CloudWatch module if `withAWSCloudWatch` is true (false by default) |
| `solveAlgorithm`  | `String:algorithmId`<br>`Task:initialTask`<br>`Integer:priority`<br>*`Consumer<T>:callback`*  | `String` | Solves the algorithm identified by `algorithmId`, with `initialTask` as the first Task to be executed, with certain `priority` (1 > 2 > 3...) and running `callback` function when the final result is available. If the algorithm id is not valid (was previously used) a new one is returned |
| `solveAlgorithm`  | `String:algorithmId`<br>`Task:initialTask`<br>`Integer:priority`<br>*`AlgorithmCallback<T>:callback`*  | `String` | Solves the algorithm identified by `algorithmId`, with `initialTask` as the first Task to be executed, with certain `priority` (1 > 2 > 3...) and executing `callback` success/error function when the final result is available. If the algorithm id is not valid (was previously used) a new one is returned |
| `terminateAlgorithms`  |  | void | Stops the execution of all running algorithms, forcing their termination |
| `blockingTerminateAlgorithms`  |  | void | Stops the execution of all running algorithms, forcing their termination. The method will not return until all the distributed structures are not clean and properly stopped |
| `blockingTerminateOneAlgorithm`  | `String:algorithmId` | void | Stops the execution of algorithm with id `algorithmId`, forcing its termination. The method will not return until all the distributed structures related to this algorithm are not clean and properly stopped |
| `getAlgorithm` | `String:algorithmId` | `Algorithm` | Get running algorithm with id `algorithmId`. This method will return null for a finished algorithm |
| `getAllAlgorithms` |  | `Collection<Algorithm>` | Get all running algorithms |

#### Algorithm< T > 

| Method  | Params (*italics* are optional) | Returns  | Description |
|---|---|---|---|
| `getResult` |  | `T` | Get the final result of the algorithm. Only available when the algorithm is done (same value is received by callback parameter `Consumer<T>:callback` on method `AlgorithmManager.solveAlgorithm`) |
| `getStatus`  |  | `Algorithm.Status` | Returns the status of the algorithm |
| `getTasksAdded` |  | `int` | Get the total number of tasks that have been added to the algorithm by the time this method is called (including the initial Task) |
| `getTasksCompleted` |  | `int` | Get the total number of tasks that have succefully finished its execution by the time this method is called (including the initial Task) |
| `getTasksQueued` |  | `int` | Get the total number of tasks waiting in the algorithm's queue |
| `getTimeOfProcessing` |  | `int` | Seconds that the algorithm has been executing |
| `getInitialTask` |  | `Task` | Entrypoint task of the algorithm (task passed to method `AlgorithmManager.solveAlgorithm`) |
| `getErrorTasks` |  | `List<Task>` | Every task of the algorithm that has triggered an error. In the current version, only possible errors that tasks can throw are timeouts. So this method returns all tasks that have triggered a timeout |


##### Algorithm.Status (enum)

- `STARTED`: Algorithm has started (method `AlgorithmManager.solveAlgorithm` has been called)
- `COMPLETED`: Algorithm has successfully finished
- `TERMINATED`: Algorithm has been manually cancelled by calling any of the termination methods of `AlgorithmManager`}
- `TIMEOUT`: Algorithm has been forcibly finished by a task that didn't manage to complete within its specified timeout. Any Task that throws a timeout will be stopped, or at least will try. The responsibility of stopping the thread belongs to the designer of the Task, specifically `Task#process` method. It should be designed following Java best practices for running concurrent threads: allow your Task to throw `InterruptedException` when possible and explicitly check if the current Thread is interrupted regularly during the process method, returning if so.

#### Task

| Method  | Params (*italics* are optional) | Returns  | Description |
|---|---|---|---|
| `setMaxDuration` | `long:milliseconds` | void | Set the timeout of the Task. If this time elapses, the algorithm will be stopped with status `TIMEOUT`. Any Task that throws a timeout will be stopped, or at least will try. The responsibility of stopping the thread belongs to the designer of the Task, specifically `Task#process` method. It should be designed following Java best practices for running concurrent threads: allow your Task to throw `InterruptedException` when possible and explicitly check if the current Thread is interrupted regularly during the process method, returning if so. |
| `addNewTask`  | `Task:task` | void | Add a new Task to the algorithm |
| `process`  |  | void | Main code of the distributed task |
| `algorithmSolved`  | `R:finalResult` | void | This method will finish the Algorithm< R >, setting `finalResult` as the global final result for the algorithm |
| `getId`  |  | `int` | Returns the unique identifier for this task |
| `getStatus`  |  | `Task.Status` | Returns the status of the task |
| `getMap`  | String:id | `IMap` | Returns a distributed Hazelcast Map associated to the Algorithm of this Task |
| `getQueue`  | String:id | `IQueue` | Returns a distributed Hazelcast Queue associated to the Algorithm of this Task |
| `getRingbuffer`  | String:id | `Ringbuffer` | Returns a distributed Hazelcast Ringbuffer associated to the Algorithm of this Task |
| `getSet`  | String:id | `ISet` | Returns a distributed Hazelcast Set associated to the Algorithm of this Task |
| `getList`  | String:id | `IList` | Returns a distributed Hazelcast List associated to the Algorithm of this Task |
| `getMultiMap`  | String:id | `MultiMap` | Returns a distributed Hazelcast MultiMap associated to the Algorithm of this Task |
| `getReplicatedMap`  | String:id | `ReplicatedMap` | Returns a distributed Hazelcast ReplicatedMap associated to the Algorithm of this Task |
| `getTopic`  | String:id | `ITopic` | Returns a distributed Hazelcast Topic associated to the Algorithm of this Task |
| `getLock`  | String:id | `ILock` | Returns a distributed Hazelcast Lock associated to the Algorithm of this Task |
| `getSemaphore`  | String:id | `ISemaphore` | Returns a distributed Hazelcast Semaphore associated to the Algorithm of this Task |
| `getAtomicLong`  | String:id | `IAtomicLong` | Returns a distributed Hazelcast AtomicLong associated to the Algorithm of this Task |
| `getAtomicReference`  | String:id | `IAtomicReference` | Returns a distributed Hazelcast AtomicReference associated to the Algorithm of this Task |
| `getIdGenerator`  | String:id | `IdGenerator` | Returns a distributed Hazelcast IdGenerator associated to the Algorithm of this Task |
| `getCountDownLatch`  | String:id | `ICountDownLatch` | Returns a distributed Hazelcast CountDownLatch associated to the Algorithm of this Task |

> All `get[DATA_STRUCTURE]` methods above are a simple encapsulation that allows SquirrelDrey to properly dispose all the distributed data structures associated to one algorithm when it is over. Users can always get any Hazelcast distributed object by calling `Task.hazelcastInstance.get[DATA_STRUCTURE]` instead of `Task.get[DATA_STRUCTURE]`, but **they are responsible of destroying them at some time during the execution**. You may prefer doing this when you want a distributed object to be **common to every algorithm** and not just to one.

##### Task.Status (enum)

- `QUEUED`: Task is waiting in the algorithm's distributed queue
- `RUNNING`: Task is running on some worker
- `COMPLETED`: Task has successfully finished
- `TIMEOUT`: Task didn't manage to finish within its specified timeout

#### System properties

- **hazelcast-config**: path to hazelcast configuration file. Default to `"src/main/resources/hazelcast-config.xml"`, which ultimately leads to [this file](https://github.com/codeurjc/SquirrelDrey/blob/master/squirrel-drey/src/main/resources/hazelcast-config.xml).
- **mode**: `PRIORITY` (default) or `RANDOM`. Defines the strategy followed by SquirrelDrey to select the next task to solve.
- **idle-cores-worker**: number of cores that will remain idle per worker. Default is 1, so ideally worker communications will never get blocked, but this property can be increased to ensure it.
- **idle-cores-app**: number of cores that will remain idle in the application. Default is 3/4 of the cores.
- **init-timeout**: minutes that a worker node will wait for the HazelCast CP subsystem cluster to be up and ready. If it elapses, then the worker Java process will be terminated.
- **devmode**: if true 2 worker nodes will be automatically launched by the library when initializing a new `AlgorithmManager`.
- **cp-member-count** _(CP Hazelcast config property)_: number of CP members. Default: 3 (MINIMUM)
- **cp-session-heartbeat** _(CP Hazelcast config property)_: interval in seconds for the periodically-committed CP session heartbeats. Default: 30
- **cp-session-ttl** _(CP Hazelcast config property)_: duration in seconds for a CP session to be kept alive after the last heartbeat. Default: 180
- **cp-missing-member-autoremoval**: _(CP Hazelcast config property)_: duration in seconds to wait before automatically removing a missing CP member from the CP subsystem. For a normal-operating system this property is not necessary in principle, as master node will immediately remove the CP member from the CP group on member disconnection. Default: 300

## Some thoughts about Hazelcast approach compared to other alternatives

SquirrelDrey framework relies on Hazelcast, but other alternatives could be used. In this section we will compare the two current main options available to deal with distribution of algorithms on clusters, taking Hazelcast and Apache Flink as representatives for each approach. We will also discuss why Hazelcast is the final chosen technology.

First of all, both frameworks share similar architecures. Users can launch slave nodes to build one cluster, and clients that can communicate with the cluster are available to use in Java applications.

*Hazelcast* stands for the **imperative** approach, while [Apache Flink](https://flink.apache.org/) represents the **declarative** approach. A good analogy to ilustrate this statement can be set with Java 8 Stream API. These code snippets will return the same result *("4", "16", "36")* :

```java
public List<Double> imperative() {
	List<Double> sourceList = Arrays.asList("1", "2", "3", "4", "5", "6");
	List<Double> resultList = new ArrayList<>();
	for (Integer i : input) {
		if (i % 2 == 0) {
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
		.collect(Collectors.toCollection(() -> new ArrayList<>()));
}
```

Both functions return the same list, but the second one makes use of the Stream API and lambda functions as compared with the traditional loop of the first one.

Now let's outline the distribution of a list of tasks (`Callable` objects) on a cluster. Let's suppose our tasks are:

```java
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

```java
public void imperativeHAZELCAST(List<Task> tasks) {
	Queue<Task> distributedQueue = hazelcastInstance.getQueue("queue");
	for (Task task : tasks) {
		distributedQueue.put(task);
	}
}
```

Slave nodes just need to indefinitely poll from the distributed queue and run the `call` method of the task.

#### With Apache Flink

```java
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

## Running on Amazon ECS

If you want to run this software on Amazon ECS follow this steps.

Keep in mind that there are two apps inside the jar, one is the worker and the other one is the sample app. So, you have to create basically the same infraestructure twice.

#### Build the Docker container.

This step is common for the sample app and the worker app.

First of all, we need to create the container image and upload it to the Amazon Repository. Hazelcast is ready for running on ECS you just need to configure it. So, add this stanzas to Hazelcast config files.

* `hazelcast-client-config.xml`

```xml
...
<properties>
    <property name="hazelcast.discovery.enabled">true</property>
    <property name="hazelcast.discovery.public.ip.enabled">true</property>
  </properties>
...
  <network>
    <discovery-strategies>
        <discovery-strategy enabled="true" class="com.hazelcast.aws.AwsDiscoveryStrategy">
            <properties>
                      <property name="iam-role">IAM_ROLE</property>
                      <property name="region">REGION</property>
                      <property name="security-group-name">SECURITY_GROUP_NAME</property>
                      <property name="tag-key">TAG_KEY</property>
                      <property name="tag-value">TAG_VALUE</property>
                      <property name="hz-port">HZ_PORT</property>
            </properties>
        </discovery-strategy>
    </discovery-strategies>
...
```

* `hazelcast-config.xml`

```xml
...
 <properties>
     <property name="hazelcast.discovery.enabled">true</property>
  </properties>
...
            <discovery-strategies>
                <discovery-strategy enabled="true" class="com.hazelcast.aws.AwsDiscoveryStrategy">
                <properties>
                      <property name="iam-role">IAM_ROLE</property>
                      <property name="region">REGION</property>
                      <property name="security-group-name">SECURITY_GROUP_NAME</property>
                      <property name="tag-key">TAG_KEY</property>
                      <property name="tag-value">TAG_VALUE</property>
                      <property name="hz-port">HZ_PORT</property>
                </properties>
                </discovery-strategy>
            </discovery-strategies>
...
        <interfaces enabled="true">
            <interface>INTERFACE</interface>
        </interfaces>
...
```

For more information on this values check out the official [documentation](https://github.com/hazelcast/hazelcast-aws).

To set up those values in run time a script is run and, as usual, the values get the container as environment variables.

Then, go to ECS console and [follow the steps](http://docs.aws.amazon.com/AmazonECR/latest/userguide/docker-push-ecr-image.html) to register your image in the repository.

#### IAM Role

You must create an **IAM Role** to grant permissions over your AWS infraestructure. According to Hazelcast docs, you need at least `ec2:DescribeInstances` policy for your role but in fact you also need:

```
"cloudwatch:PutMetricData",
"ecs:CreateCluster",
"ecs:DeregisterContainerInstance",
"ecs:DiscoverPollEndpoint",
"ecs:Poll",
"ecs:RegisterContainerInstance",
"ecs:StartTelemetrySession",
"ecs:Submit*",
"ecr:GetAuthorizationToken",
"ecr:BatchCheckLayerAvailability",
"ecr:GetDownloadUrlForLayer",
"ecr:BatchGetImage",
"logs:CreateLogStream",
"logs:PutLogEvents",
"ecs:DescribeServices",
"ecs:UpdateService"
```

To manage CloudWatch and ECS.

#### Security Group

You must create a **Security Group** with the port 5701 open for the worker and the port 5701 and 5000 for the sample app.

#### Task Definition

**Task definition** is similar to Docker Compose. You need to set up the environment where your container is going to live. So, go to the ECS console and create a new **Task Definition**.

Basically you need to set up **Task name**, the **Role** you created previously and the **Network mode** this one to **host**. Then add the container and set the **Name**, **Image** which is the one you created previously, **Memory Limits** is a Java app so be generous and then the environment variable, it should look like:

| KEY | VALUE | COMMENTS |
|---|---|---|
| HZ_PORT |	5701 | This port is used by hazelcast to talk to the workers |
| IAM_ROLE |	hazelcastrole | This role grant the policies |
| INTERFACE |	10.0.0.* | Depends on your VPC CIDR |
| MODE |	RANDOM | Optios are *random* or *priority* |
| REGION |	eu-west-1 | Your AWS Region |
| SECURITY_GROUP_NAME |	hazelcast-sg | |
| TAG_KEY |	aws-test-cluster | Hazelcast uses this pair to identify other cluster members |
| TAG_VALUE | 	cluster1 | |
| TYPE	| worker | Options are *worker* or *web* |

### Cluster

Now you have to create the cluster which is a group of **EC2 instances**. On the console you can fill up the information for the new instances ECS will create. You will create a cluster for the sample app and other for the workers. The values are basically the same.

| KEY | VALUE | COMMENTS |
|---|---|---|
|Name | hazelcast-CLUSTER | Name of the cluster, replace CLUSTER by workers or web to identify in the future |
|EC2 instance type| m4.xlarge | We recommend m4.xlarge. The sample app can use a smaller instance type |
|Number of instances | 1 | 1 instance at the begining |
|Key pair | | Depends on if you want to ssh your instances |
|Networking | | Complete upon your needs |
|Container instance IAM role| | The role you created before |

When you press **Create** an EC2 instance will be created, now you have to edit that instance to add the key/value tag. 

**This only apply for workers cluster**. After that, go back to ECS -> Clusters and click on **ECS Instances** and **Scale ECS Instances** then you will see a window with a link to **AWS Auto Scaling Group** you have to click there. 

Now you should see **AWS Auto Scaing Pane**, if not, go throught **EC2 main pane** on the left you shoud see **Auto Scaling** and **Auto Scaling Groups**. Here, you select the new one and in **Details** you set the following information: 

Min: 1
Max: 5
Termination Policy: NewestInstance

Under **Tags** add the key and value you set on the Hazelcast configuration. 

#### Service

You have to create a **Service** for the sample app and the worker. The diference between them is the name and the number of desired tasks of the worker.

Back to **ECS** you can now configure the **Service** from the **Task Definition**, go to the console and create a **Service** from the last revision of your **Task Definition** by selecting it and pressing **Actions** then **Create new Service**.

On the next screen you have to configure the service. Setting a name, number of tasks (same tasks number as instances Max you set before.) and task placemente as **One task per host**. You don't need a Load Balancer neither a Scaling policy for the service. As you press **Create Service** a container will appear in your infraestructure with the app running.

#### Alarms

While your app is running it sends metrics to Amazon Cloud Watch. Now you can create alarms based on this metrics values. You need two alarms, one for scale up and another for scale down.

* Scale up

Go to Amazon Cloud Watch pane and click on the left menu on **Alarms** and **Create a new Alarm**. Find the metric **HAZELCAST_METRIC** and choose **TASKS_QUEUE** then click next. Pick up a name for the alarm and set **is: >= 2** then in **Actions** remove that notification and in **Period** choose 10 seconds. Finish by presing **Create Alarm**

* Scale down

Go to Amazon Cloud Watch pane and click on the left menu on **Alarms** and **Create a new Alarm**. Find the metric **HAZELCAST_METRIC** and choose **TASKS_QUEUE** then click next. Pick up a name for the alarm and set **is: < 1** then in **Actions** remove that notification and in **Period** choose 10 seconds. Finish by presing **Create Alarm**

With those configuration, you can go back to the **EC2 pane** in **Auto Scaling**, **Auto Scaling Group** you can set the **Scaling Policies**.

* Scale In

| Key | Value |
| --- | --- |
| Policy type | Simple scaling|
| Execute policy when | Alarm Scale Down |
| Take action | Remove 1 instance |
| And wait | 60 seconds |

* Scale Out

| Key | Value |
| --- | --- |
| Policy type | Simple scaling|
| Execute policy when | Alarm Scale Down |
| Take action | Remove 1 instance |
| And wait | 60 seconds |

#### Summing up

At this poing you should be able to access the [web](http://IP_EC2_INSTANCE:5000) and run the algorithm. The IP you are looking for is the one attached to the sample app instance.

#### CloudFormation

You can find a CloudFormation recipe in this repo which make easier to deploy this infreastructure. We encourage you to use that rather than a configuration by hand.

## Local scaling version

This version doesn't use Hazelcast, and is intended to be for using the framework on a single machine.

Differences with the main version:

- There are no longer differences between worker and app as there is only one machine (no need to use the -Dworker property).
- No Hazelcast, which implies the following methods from the API are not implemented:
	- Task: All `get[DATA_STRUCTURE]` methods.
	- AlgorithmManager<T>: constructor no longer needs parameters.
- The following previous system properties are ignored:
	- **hazelcast-config**
	- **idle-cores-worker**
	- **init-timeout**
	- **devmode**
	- **cp-member-count**
	- **cp-session-heartbeat**
	- **cp-session-ttl**
	- **cp-missing-member-autoremoval**
- CloudWatch not implemented.

To use this version, you can compile and use it running the following commands: 

```
cd SquirrelDrey/squirrel-drey-local
mvn clean install
```

### Code example (*squirrel-drey-hello-world-local*)

This example works exactly the same as the [hello world code example](#code-example-squirrel-drey-hello-world), the main difference is that the structures from the tasks previously provided and managed by Hazelcast have been created in the **Structures** class.

To run this example run the following commands:

```
cd SquirrelDrey/squirrel-drey-local
mvn install
cd ../squirrel-drey-hello-world
mvn -DskipTests=true clean package
```