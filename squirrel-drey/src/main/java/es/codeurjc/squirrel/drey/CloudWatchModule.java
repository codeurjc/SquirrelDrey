package es.codeurjc.squirrel.drey;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

public class CloudWatchModule {
	
	final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-1").build();
	HazelcastInstance hcClient;
	IMap<String, QueueProperty> queues;
	Thread publishMetricsThread;
	
	public CloudWatchModule(HazelcastInstance hcClient, IMap<String, QueueProperty> queues) {
		this.hcClient = hcClient;
		this.queues = queues;
		this.startPublishMetricsThread();
	}
	
	public void publishMetrics(Double nAlgorithms, Double nInstances) {

		MetricDatum datum = new MetricDatum()
		    .withMetricName("TASKS_QUEUED")
		    .withUnit(StandardUnit.None)
		    .withValue(nAlgorithms/nInstances);

		PutMetricDataRequest request = new PutMetricDataRequest()
		    .withNamespace("HAZELCAST_METRIC")
		    .withMetricData(datum);

		cw.putMetricData(request);
		
		System.out.println("METRICS PUSHED: " + nAlgorithms/nInstances);
	}
	
	public void startPublishMetricsThread() {
		this.publishMetricsThread = new Thread(() -> {
			while (true) {
				this.publishMetrics((double) this.queues.size(), (double) this.hcClient.getCluster().getMembers().size());
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
		this.publishMetricsThread.start();
	}
	
	public void stopPublishMetricsThread() {
		this.publishMetricsThread.interrupt();
	}

}
