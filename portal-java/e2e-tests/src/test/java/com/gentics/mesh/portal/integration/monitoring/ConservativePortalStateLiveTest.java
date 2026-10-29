package com.gentics.mesh.portal.integration.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.junit.Test;

@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	defaultLanguage = "de",
	configProvider = AbstractPortalStateLiveTest.Config.class,
	startupChecks = TestContextStartupCheckMode.SHOULD_START,
	testData = TestData.REF)
public class ConservativePortalStateLiveTest extends AbstractPortalStateLiveTest {


	/** Check the live endpoint of the portal. */
	@Test
	public void testLiveEndpoint() {
		// Make sure everything works before stopping Mesh.
		HttpClientResponse response = request(portal.monitoringClient(), URL_HEALTH_LIVE);

		assertThat(response.statusCode())
			.as("First ready response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		// The /api/meshInfo endpoint will return the response of Mesh's /api/v1 endpoint.
		response = request(portal.portalClient(), URL_MESH_INFO);
		assertThat(response.statusCode())
			.as("First Mesh info response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		portal.meshServer().stop();

		exhaustAllowedTimeouts();

		response = request(portal.monitoringClient(), URL_HEALTH_LIVE);
		assertThat(response.statusCode())
			.as("Final live response status")
			.isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
	}
}
