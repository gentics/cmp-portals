package com.gentics.mesh.portal.integration.monitoring;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.junit.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	defaultLanguage = "de",
	configProvider = ConservativePortalStateReadyTest.Config.class,
	startupChecks = TestContextStartupCheckMode.SHOULD_START,
	testData = TestData.REF)
public class ConservativePortalStateReadyTest extends AbstractEndToEndTest {

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig config = new DefaultPortalConfig().get();

			config.setNetworkTimeout(5);
			config.getCachingOptions().setEnabled(false);
			config.getMonitoringOptions().setConservativePortalState(true);
			config.getMonitoringOptions().setMaxTimeouts(3);

			return config;
		}
	}

	private static final String URL_HEALTH_READY = "/health/ready";
	private static final String URL_HEALTH_LIVE = "/health/live";
	private static final String URL_MESH_INFO = "/api/meshInfo";
	private static final String URL_STARTPAGE = "/start.de.html";

	/** Check the {@code ready} endpoint. */
	@Test
	public void testReadyEndpoint() {
		HttpClientResponse response = request(portal.monitoringClient(), URL_HEALTH_READY);

		assertThat(response.statusCode())
			.as("First ready response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		response = request(portal.portalClient(), URL_STARTPAGE);
		assertThat(response.statusCode())
			.as("First content response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		portal.meshServer().stop();

		response = request(portal.monitoringClient(), URL_HEALTH_READY);
		assertThat(response.statusCode())
			.as("Second ready response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		response = request(portal.portalClient(), URL_STARTPAGE);
		assertThat(response.statusCode())
			.as("Second content response status")
			.isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

		response = request(portal.monitoringClient(), URL_HEALTH_READY);
		assertThat(response.statusCode())
			.as("Third ready response status")
			.isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
	}
}
