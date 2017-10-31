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
	
	public void publishMetrics() {
		/*Dimension dimension = new Dimension()
			    .withName("UNIQUE_PAGES")
			    .withValue("URLS");

		MetricDatum datum = new MetricDatum()
		    .withMetricName("PAGES_VISITED")
		    .withUnit(StandardUnit.None)
		    .withValue(data_point)
		    .withDimensions(dimension);

		PutMetricDataRequest request = new PutMetricDataRequest()
		    .withNamespace("SITE/TRAFFIC")
		    .withMetricData(datum);

		PutMetricDataResult response = cw.putMetricData(request);*/
	}

}
