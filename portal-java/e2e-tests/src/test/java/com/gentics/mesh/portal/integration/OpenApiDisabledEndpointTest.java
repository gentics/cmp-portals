package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.handler.SecurityHandler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

@PortalTestSetting(
		meshImage = "docker.gentics.com/gentics/mesh-hsqldb", 
		testData = TestData.REF, 
		keycloakConfig = "/keycloak/realm-with-portal-client.json", 
		startKeycloak = false, 
		keycloakInitRetryPeriodSeconds = 3, 
		configProvider = OpenApiDisabledEndpointTest.Config.class)
@RunWith(Parameterized.class)
public class OpenApiDisabledEndpointTest extends OpenApiEndpointTest {

	/**
	 * Configuration supplier, which sets up the portal configuration for the test
	 */
	public static class Config implements Supplier<PortalConfig> {

		@Override
		public PortalConfig get() {
			var portalConfig = new OpenApiEndpointTest.Config().get();

			portalConfig.getDataProviderOptions().setServeOpenApi(false);

			return portalConfig;
		}
	}

	@Override
	@Test
	public void testOpenApi() {
		var client = portal.portalClient();
		var rqOptions = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/api/openapi." + format.toString().toLowerCase() + "?version=" + version.toString().toLowerCase())
			.addHeader(SecurityHandler.HEADER_API_KEY, portal.portalConfig().getSecurityOptions().getApiKey());
		var response = getResponseWithBody(client.rxRequest(rqOptions).blockingGet());

		assertThat(response.getResponse().statusCode())
			.as("Response status code")
			.isEqualTo(HttpResponseStatus.FORBIDDEN.code());
	}
}
