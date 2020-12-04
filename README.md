SquirrelDrey
===

## Table of contents

* [Introduction](#introduction)
* [Code example](#code-example-squirrel-drey-hello-world)
* [Running sample applications](#running-sample-applications)
* [Building your own app](#building-your-own-app)
* [API](#api)
* [Running on Amazon ECS](#running-on-amazon-ecs)

----------

## Introduction

SquirrelDrey is a Java framework aimed to support distributed execution of algorithms. SquirrelDrey is a **Task-Driven Framework**: the logic of any custom algorithm must be contained in its custom tasks. This means that the entrypoint for the algorithm is no more than one initial Task.

The initial Task will generate as much other tasks as needed. In the same manner, these can also generate other tasks.

Whenever SquirrelDrey founds that the number of tasks sent to be executed matches the number of completed tasks for one algorithm, it will be terminated. If any task has called method `Task.algorithmSolved(result)`, that will be the final result of the algorithm (`null` if not).

SquirrelDrey distinguishes between 2 types of work:  

- Master: Sends the algorithms to run to the workers using an input SQS FIFO queue, then waits for the results polling an output SQS FIFO queue and running the algorithm callback.
- Worker: Runs algorithms sent by the master (received by polling the input SQS FIFO queue). When finished, returns the results to the master (sending them to the output SQS FIFO queue) to run the callback.

As described above, the framework makes use of SQS FIFO queues: two input queues (low and high priority), an output queue and direct queues:

- An input queue and a low priority input queue are used for communications from the master to the workers.
  - The low priority input queue will be listened to when there are no algorithms running and no messages in the input queue.
- The output queue is used for communications from the workers to the master.
- For each worker there is a direct queue between the master and that worker.
They can be created by the user or by the framework. The queues will be created by the master when needed if they don't exist (the names can be configured by passing arguments to the console).

Note: At the moment devmode is enabled by default (this mode does not require SQS and runs the algorithms in the master).

----------

## Code example (*squirrel-drey-hello-world-local*)

We will explain our _hello-world_ sample app (***squirrel-drey-hello-world-local***). This app runs an algorithm with 3 types of tasks: **PreparationTask**, **AtomicTask** and **SolveTask**.

Our **PreparationTask** will act as the initial task for the algorithm. It generates 10 **AtomicTask**, that simply wait for 5 seconds and set their result as '1'. The last executed **AtomicTask** will generate one **SolveTask**, which sums all the results from all **AtomicTask** and ends the algorithm (the final result will be the number of AtomicTasks executed).

To control which **AtomicTask** should generate the only **SolveTask**, we make use of an *AtomicLong* managed by SquirrelDrey. **PreparationTask** initilizes this parameter to the number of **AtomicTasks** and each **AtomicTask** decrements it at the end of its `process()` method. When any **AtomicTask** decrements the value to 0 it will mean that it is indeed the last of its kind and it will add one **SolveTask**.

This flow means that both **PreparationTask** and **SolveTask** block the execution: **PreparationTask** will always be the first Task executed and **SolveTask** the last one. In principle, we don't know (and we don't mind) the order of execution of the **AtomicTasks**.

```java
public class App {

 public static void main(String[] args) throws Exception {
  if (!Boolean.valueOf(System.getProperty("worker"))) {
   AlgorithmManager<String> manager = new AlgorithmManager<>();
   Task initialTask = new PreparationTask(10);

   manager.solveAlgorithm("sample_algorithm", initialTask, 1, (result) -> {
    System.out.println("MY RESULT: " + result);
    System.exit(0);
   });
  } else {
   Worker.launch();
  }
 }
}
```

```java
public class PreparationTask extends Task {

 private Integer numberOfAtomicTasks;

 public PreparationTask(Integer numberOfAtomicTasks) {
  this.numberOfAtomicTasks = numberOfAtomicTasks;
 }

 @Override
 public void process() throws Exception {
  AtomicLong atomicLong = this.getAtomicLong("my_countdown");
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

 public AtomicTask() {
 }

 @Override
 public void process() throws Exception {
  Thread.sleep(5000);

  Map<Integer, Integer> results = (Map<Integer, Integer>) this.getMap("my_results");
  AtomicLong atomicLong = this.getAtomicLong("my_countdown");
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
  Map<Integer, Integer> results = (Map<Integer, Integer>) this.getMap("my_results");

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

Note: development has been done using [Localstack](https://github.com/localstack/localstack) to simulate AWS SQS queues.

### squirrel-drey-hello-world-local

**Clone and build the project**

```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey-hello-world-local
mvn -DskipTests=true clean package
```

**Launch a worker**

```
java -Ddevmode=false -Dworker=true -Daws-region=us-east-1 -Dendpoint-url=http://localhost:4566 -jar target/squirrel-drey-hello-world-*.jar
```

**Launch app** *(different console window)*

```
java -Ddevmode=false -Dworker=false -Daws-region=us-east-1 -Dendpoint-url=http://localhost:4566 -jar target/squirrel-drey-hello-world-*.jar
```

The output of the app will show the solving process, displaying the state of the workers in real time, and will end showing the final result.

### squirrel-drey-sample-app-local

**Clone and build the project**

```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey-sampleapp-local
mvn -DskipTests=true package
```

**Launch a worker**

```
java -Ddevmode=false -Dworker=true \
    -Daws-region=us-east-1 \
    -Dendpoint-url=http://localhost:4566 \
    -Dsqs-listener-timer=1 \ -jar target/squirrel-drey-sampleapp-*.jar
```

**Launch sampleapp** *(different console window)*

```
java -Ddevmode=false -Dworker=false \
    -Daws-region=us-east-1 \
    -Dendpoint-url=http://localhost:4566 \
    -Dsqs-listener-timer=1 -jar target/squirrel-drey-sampleapp-*.jar
```

You will have the web app available at [localhost:5000](http://localhost:5000). You can launch different algorithms with different configurations at the same time, and they will execute making use of all the launched workers. You can dinamically add or remove workers and see the behaviour and performance of the algorithm's execution.

----------

## Using SNAPSHOT versions

Note: Currently local version isn't on Maven Central, this steps are mandatory for installing.

Do you want the latest version on *master* branch of SquirrelDrey, but it isn't on Maven Central yet? Just compile it locally. For example, for *squirrel-drey-hello-world*:

```
git clone https://github.com/codeurjc/SquirrelDrey.git
cd SquirrelDrey/squirrel-drey-local
mvn clean install
cd ../squirrel-drey-hello-world-local
mvn -DskipTests=true clean package
```

----------

## Building your own app

Note: Currently local version isn't on Maven Central, use the steps above to install it.

Your project must have the following dependency:

```xml
<dependency>
 <groupId>es.codeurjc</groupId>
 <artifactId>squirrel-drey-local</artifactId>
 <version>...</version>
</dependency>
```

Your application will have to be responsible of launching the workers. An easy way of managing this situation is by using command line options to choose whether to launch you application or a worker (using the -Dworker system property).

*squirrel-drey-sampleapp* does it just like this. Summarizing its `main` method:

```java
public static void main(String[] args) {

 boolean isWorker = System.getProperty("worker") != null ? Boolean.valueOf(System.getProperty("worker")) : true;
 boolean isDevMode = System.getProperty("devmode") != null ? Boolean.valueOf(System.getProperty("devmode")) : false;

 if (!isWorker || isDevMode) {
  SpringApplication.run(Web.class);
 } else {
  Worker.launch();
 }

}
```

So, our **application** will start up if we launch the JAR the following way:

```
java -Ddevmode=false -Dworker=false \
    -Daws-region=us-east-1 \
    -Dendpoint-url=http://localhost:4566 \
    -Dsqs-listener-timer=1 -jar target/squirrel-drey-sampleapp-*.jar
```

But one **worker** will be launched if done like this:

```
java -Ddevmode=false -Dworker=true \
    -Daws-region=us-east-1 \
    -Dendpoint-url=http://localhost:4566 \
    -Dsqs-listener-timer=1 -jar target/squirrel-drey-sampleapp-*.jar
```

----------

## API

| Class                 | Description                                                                                                                                                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `AlgorithmManager<T>` | Centralized manager object for launching algorithms and getting their result. `T` is the class of the algorithm's final result. Must be a Serializable object                                                                  |
| `Algorithm<T>`        | Represents a project with one initial Task as entry point for its execution. Stores valuable information about the Tasks added, completed and queued. Algorithms will be run by the workers (one or more algorithms by worker) |
| `Task`                | Callable objects that will be executed asynchronously in a worker. All classes extending it must have serializable attributes                                                                                                  |

#### AlgorithmManager< T >

| Method                          | Params (*italics* are optional)                                                                       | Returns                      | Description                                                                                                                                                                                                                                                                                                                                     |
| ------------------------------- | ----------------------------------------------------------------------------------------------------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `solveAlgorithm`                | `String:algorithmId`<br>`Task:initialTask`<br>`Integer:priority`<br>*`Consumer<T>:callback`* <br>*`isLowPriority:boolean`*        | `String`                     | Master sends the algorithm  to be solved by a worker, identified by `algorithmId`, with `initialTask` as the first Task to be executed, with certain `priority` (1 > 2 > 3...) and running `callback` function when the final result is available. If the algorithm id is not valid (was previously used) a new one is returned. If isLowPriority is true (defaults to false), the algorithm will be sent to the low priority queue                |
| `solveAlgorithm`                | `String:algorithmId`<br>`Task:initialTask`<br>`Integer:priority`<br>*`AlgorithmCallback<T>:callback`*<br>*`isLowPriority:boolean`* | `String`                     | Master sends the algorithm  to be solved by a worker, identified by `algorithmId`, with `initialTask` as the first Task to be executed, with certain `priority` (1 > 2 > 3...) and executing `callback` success/error function when the final result is available. If the algorithm id is not valid (was previously used) a new one is returned. If isLowPriority is true (defaults to false), the algorithm will be sent to the low priority queue |
| `terminateAlgorithms`           |                                                                                                       | void                         | Stops the execution of all running algorithms, forcing their termination                                                                                                                                                                                                                                                                        |
| `blockingTerminateAlgorithms`   |                                                                                                       | void                         | Stops the execution of all running algorithms, forcing their termination. The method will not return until all the  structures are not clean and properly stopped on all workers                                                                                                                                                                |
| `blockingTerminateOneAlgorithm` | `String:algorithmId`                                                                                  | void                         | Stops the execution of algorithm with id `algorithmId`, forcing its termination. The method will not return until all the structures related to this algorithm are not clean and properly stopped on all workers                                                                                                                                |
| `getAlgorithm`                  | `String:algorithmId`                                                                                  | `Algorithm`                  | Get running algorithm with id `algorithmId`. This method will return null for a finished algorithm                                                                                                                                                                                                                                              |
| `getAllAlgorithms`              |                                                                                                       | `Collection<Algorithm>`      | Get all running algorithms                                                                                                                                                                                                                                                                                                                      |
| `getWorkers`                    | *`int:maxSecondsToWait`*                                                                              | `Map<String, WorkerStats>`   | Fetches the stats of all workers under a master. For a key, the value can be `null` if the stats could not be retrieved. If *maxSecondsToWait* (60 default) seconds pass without a response from the workers, a TimeoutException is thrown.                                                                                                     |
| `getAlgorithmInfo`              | `int:maxSecondsToWait`                                                                                | `Map<String, AlgorithmInfo>` | Fetches the information of all running algorithms of all workers under a master. If *maxSecondsToWait* (60 default) seconds pass without a response from the workers, a TimeoutException is thrown.                                                                                                                                             |

#### Algorithm< T >

| Method                | Params (*italics* are optional) | Returns            | Description                                                                                                                                                                                             |
| --------------------- | ------------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `getResult`           |                                 | `T`                | Get the final result of the algorithm. Only available when the algorithm is done (same value is received by callback parameter `Consumer<T>:callback` on method `AlgorithmManager.solveAlgorithm`)      |
| `getStatus`           |                                 | `Algorithm.Status` | Returns the status of the algorithm                                                                                                                                                                     |
| `getTasksAdded`       |                                 | `int`              | Get the total number of tasks that have been added to the algorithm by the time this method is called (including the initial Task)                                                                      |
| `getTasksCompleted`   |                                 | `int`              | Get the total number of tasks that have succefully finished its execution by the time this method is called (including the initial Task)                                                                |
| `getTasksQueued`      |                                 | `int`              | Get the total number of tasks waiting in the algorithm's queue                                                                                                                                          |
| `getTimeOfProcessing` |                                 | `int`              | Seconds that the algorithm has been executing                                                                                                                                                           |
| `getInitialTask`      |                                 | `Task`             | Entrypoint task of the algorithm (task passed to method `AlgorithmManager.solveAlgorithm`)                                                                                                              |
| `getErrorTasks`       |                                 | `List<Task>`       | Every task of the algorithm that has triggered an error. In the current version, only possible errors that tasks can throw are timeouts. So this method returns all tasks that have triggered a timeout |

##### Algorithm.Status (enum)

- `STARTED`: Algorithm has started (method `AlgorithmManager.solveAlgorithm` has been called)
- `COMPLETED`: Algorithm has successfully finished
- `TERMINATED`: Algorithm has been manually cancelled by calling any of the termination methods of `AlgorithmManager`}
- `TIMEOUT`: Algorithm has been forcibly finished by a task that didn't manage to complete within its specified timeout. Any Task that throws a timeout will be stopped, or at least will try. The responsibility of stopping the thread belongs to the designer of the Task, specifically `Task#process` method. It should be designed following Java best practices for running concurrent threads: allow your Task to throw `InterruptedException` when possible and explicitly check if the current Thread is interrupted regularly during the process method, returning if so.

#### Task

| Method            | Params (*italics* are optional) | Returns       | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ----------------- | ------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `setMaxDuration`  | `long:milliseconds`             | void          | Set the timeout of the Task. If this time elapses, the algorithm will be stopped with status `TIMEOUT`. Any Task that throws a timeout will be stopped, or at least will try. The responsibility of stopping the thread belongs to the designer of the Task, specifically `Task#process` method. It should be designed following Java best practices for running concurrent threads: allow your Task to throw `InterruptedException` when possible and explicitly check if the current Thread is interrupted regularly during the process method, returning if so. |
| `addNewTask`      | `Task:task`                     | void          | Add a new Task to the algorithm                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `process`         |                                 | void          | Main code of the distributed task                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `algorithmSolved` | `R:finalResult`                 | void          | This method will finish the Algorithm< R >, setting `finalResult` as the global final result for the algorithm                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `getId`           |                                 | `int`         | Returns the unique identifier for this task                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `getStatus`       |                                 | `Task.Status` | Returns the status of the task                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `getMap`          | String:id                       | `Map`         | Returns a Map associated to the Algorithm of this Task                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `getAtomicLong`   | String:id                       | `AtomicLong`  | Returns an AtomicLong associated to the Algorithm of this Task                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |

> All `get[DATA_STRUCTURE]` methods above are a simple encapsulation that allows SquirrelDrey to properly dispose all the data structures associated to one algorithm when it is over. Users can always use their own data structures instead of `Task.get[DATA_STRUCTURE]`, but **they are responsible of cleaning them up at some time during the execution**.

##### Task.Status (enum)

- `QUEUED`: Task is waiting in the algorithm's distributed queue
- `RUNNING`: Task is running on some worker
- `COMPLETED`: Task has successfully finished
- `TIMEOUT`: Task didn't manage to finish within its specified timeout

#### System properties

- **aws-region**: AWS Region of the SQS queues. Required unless in devmode.
- **endpoint-url**: Endpoint URL of the AWS SQS queues. Required unless in devmode.
- **worker**: `false` if the instance is a master, `true` if it is a worker. Required.
- **input-queue**: Name of the input queue. If the queue does not exist a queue with this name will be created. As the queue must be a FIFO queue, SquirrelDrey will append `.fifo` to the name if necessary.
- **low-priority-input-queue**: Name of the low priority input queue. If the queue does not exist a queue with this name will be created. As the queue must be a FIFO queue, SquirrelDrey will append `.fifo` to the name if necessary.
- **output-queue**: Name of the output queue. If the queue does not exist a queue with this name will be created. As the queue must be a FIFO queue, SquirrelDrey will append `.fifo` to the name if necessary.
- **direct-queue**: Only used by workers. Name of the direct queue between this worker and the master. If the queue does not exist a queue with this name will be created. As the queue must be a FIFO queue, SquirrelDrey will append `.fifo` to the name if necessary. Warning: remember to use unique names for the queues.
- **idle-cores-app**: number of cores that will remain idle. Default is 1 (0 for devmode), so ideally worker communications will never get blocked, but this property can be increased to ensure it.
- **devmode**: if true the instance is at the same time master and worker, not needing queues to run the algorithm called by using `algorithmManager.solveAlgorithm()`. Enabled by default.
- **parallelization-grade**: Only used by workers. Maximum number of algorithms to be run at the same time by a single worker. Defaults to 1. Note that, when polling the SQS queue looking for algorithms, the worker will try to pick up as many as it can up to this maximum (there is not an even distribution).
- **mode**: Only used by workers. PRIORITY (default) or RANDOM. Defines the strategy followed by SquirrelDrey to select the next task to solve.

#### AutoScaling TODO

- [x] Wait for messages instead of polling SQS.
- [x] Prepare clases for Autoscaling.
- [x] Implement autoscaling algorithm.
- [ ] **(In Progress)** Create simulations to test algorithm.
- [ ] Implement InfrastructureManager and update ECS.
- [ ] Update CF and test in real environment