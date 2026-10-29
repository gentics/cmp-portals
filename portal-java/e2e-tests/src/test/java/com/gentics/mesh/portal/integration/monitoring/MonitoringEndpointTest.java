package com.gentics.mesh.portal.integration.monitoring;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the monitoring endpoints.
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb")
public class MonitoringEndpointTest extends AbstractEndToEndTest {

	/** Check the <code>live</code> endpoint. */
	@Test
	public void testLiveEndpoint() {
		HttpClientResponse response = request(portal.monitoringClient(), "/health/live");

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());
	}

	/** Check the <code>ready</code> endpoint. */
	@Test
	public void testReadyEndpoint() {
		HttpClientResponse response = request(portal.monitoringClient(), "/health/ready");

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());
	}

	/**
	 * Check that the metrics from the <code>metrics</code> endpoint contain Vert.x metrics.
	 */
	@Test
	public void testMetricsEndpoint() {
		request(portal.monitoringClient(), "/health/ready");

		// Fetch the response together with its body in a single reactive chain (rather than requesting the response
		// first and consuming the body afterwards). This prevents a race condition that can lead to a
		// "IllegalStateException: Response already ended" error.
		RequestOptions options = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/metrics");
		HttpClientRequest request = portal.monitoringClient().rxRequest(options).blockingGet();
		ResponseWithBody response = getResponseWithBody(request);

		System.out.println(response.getBody());
		assertThat(response.getBody())
			.as("Response body")
			.contains("vertx_eventbus_handlers");

		assertThat(response.getResponse().statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());
	}
}
