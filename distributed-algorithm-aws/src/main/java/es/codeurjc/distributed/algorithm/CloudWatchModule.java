package es.codeurjc.distributed.algorithm;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.hazelcast.core.IMap;

public class CloudWatchModule {
	
	final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-1").build();
	IMap<String, QueueProperty> queues;
	Thread publishMetricsThread;
	
	public CloudWatchModule(IMap<String, QueueProperty> queues) {
		this.queues = queues;
		this.startPublishMetricsThread();
	}
	
	public void publishMetrics(Double value) {

		MetricDatum datum = new MetricDatum()
		    .withMetricName("TASKS_QUEUED")
		    .withUnit(StandardUnit.None)
		    .withValue(value);

		PutMetricDataRequest request = new PutMetricDataRequest()
		    .withNamespace("HAZELCAST_METRIC")
		    .withMetricData(datum);

		PutMetricDataResult response = cw.putMetricData(request);
	}
	
	public void startPublishMetricsThread() {
		this.publishMetricsThread = new Thread(() -> {
			while (true) {
				this.publishMetrics((double) this.queues.size());
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
