package com.gentics.mesh.portal.integration.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MonitoringOptions;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.core.http.HttpClientResponse;

import java.util.function.Supplier;

public class AbstractPortalStateLiveTest extends AbstractEndToEndTest {
	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig config = new DefaultPortalConfig().get();
			MonitoringOptions monitoringOptions = config.getMonitoringOptions();

			config.setNetworkTimeout(5);
			config.getCachingOptions().setEnabled(false);
			monitoringOptions.setConservativePortalState(true);
			monitoringOptions.setMaxTimeouts(3);
			monitoringOptions.setLiveUpdateInterval(5);

			return config;
		}
	}

	protected static final String URL_HEALTH_LIVE = "/health/live";
	protected static final String URL_MESH_INFO = "/api/meshInfo";

	/**
	 * Make enough requests to the portal that the limit for failed requests
	 * to mesh is exhausted.
	 */
	protected void exhaustAllowedTimeouts() {
		HttpClientResponse response;
		int maxTimeouts = portal.portalConfig().getMonitoringOptions().getMaxTimeouts();

		// The first maxTimeouts - 1 requests to the /api/meshInfo endpoint must
		// not change the portal state to NETWORK_ERROR.
		for (int ii = 0; ii < maxTimeouts - 1; ii++) {
			response = request(portal.portalClient(), URL_MESH_INFO);
			assertThat(response.statusCode())
				.as(String.format("Failing Mesh request #%d/%d status", ii, maxTimeouts - 1))
				.isNotEqualTo(HttpResponseStatus.OK.code());

			response = request(portal.monitoringClient(), URL_HEALTH_LIVE);
			assertThat(response.statusCode())
				.as(String.format("Live response status #%d/%d", ii, maxTimeouts - 1))
				.isEqualTo(HttpResponseStatus.OK.code());
		}

		// The next request should trigger the state change to NETWORK_ERROR.
		response = request(portal.portalClient(), URL_MESH_INFO);
		assertThat(response.statusCode())
			.as("Last failing Mesh request status")
			.isNotEqualTo(HttpResponseStatus.OK.code());
	}
}
