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

## Running on Amazon ECS

If you want to run this software on Amazon ECS follow this steps.

Keep in mind that there are two apps inside the jar, one is the worker and the other one is the sample app. So, you have to create basically the same infraestructure twice.

#### Build the Docker container.

This step is common for the sample app and the worker app.

First of all, we need to create the container image and upload it to the Amazon Repository. Hazelcast is ready for running on ECS you just need to configure it. So, add this stanzas to Hazelcast config files.

* `hazelcast-client-config.xml`

```
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

```
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