package es.codeurjc.distributed.algorithm;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;

public class CloudWatchModule {
	
	final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();
	
	public void publishMetrics(Double value) {
		/*Dimension dimension = new Dimension()
			    .withName("UNIQUE_PAGES")
			    .withValue("URLS");*/

		MetricDatum datum = new MetricDatum()
		    .withMetricName("TASKS_QUEUED")
		    .withUnit(StandardUnit.None)
		    .withValue(value)
		    /*.withDimensions(dimension)*/;

		PutMetricDataRequest request = new PutMetricDataRequest()
		    .withNamespace("HAZELCAST_METRIC")
		    .withMetricData(datum);

		PutMetricDataResult response = cw.putMetricData(request);
	}

}
