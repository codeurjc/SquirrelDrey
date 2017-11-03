package es.codeurjc.distributed.algorithm;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;

public class CloudWatchModule {

	final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-1").build();

	public void publishMetrics(Double value) {

		MetricDatum datum = new MetricDatum()
				.withMetricName("TASKS_QUEUED")
				.withUnit(StandardUnit.None)
				.withValue(value);

		PutMetricDataRequest request = new PutMetricDataRequest()
				.withNamespace("HAZELCAST_METRIC")
				.withMetricData(datum);

		cw.putMetricData(request);
	}

}
