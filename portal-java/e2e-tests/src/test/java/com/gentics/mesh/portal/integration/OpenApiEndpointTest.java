package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.handler.SecurityHandler;
import com.gentics.mesh.portal.openapi.OpenApiVersion;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.testcontainers.KeycloakContainer;
import com.gentics.vertx.openapi.model.Format;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", testData = TestData.REF, keycloakConfig = "/keycloak/realm-with-portal-client.json", startKeycloak = false, keycloakInitRetryPeriodSeconds = 3, configProvider = OpenApiEndpointTest.Config.class)
@RunWith(Parameterized.class)
public class OpenApiEndpointTest extends AbstractEndToEndTest {

	/**
	 * Configuration supplier, which sets up the portal configuration for the test
	 */
	public static class Config implements Supplier<PortalConfig> {

		@Override
		public PortalConfig get() {
			var portalConfig = new DefaultPortalConfig().get();

			portalConfig.getSecurityOptions().setApiKey("test-key");
			portalConfig.getDataProviderOptions().setServeOpenApi(true);

			return portalConfig;
		}
	}

	@Parameters(name = "{index}: format = {0}, version = {1}")
	public static Collection<Object[]> parameters() throws Exception {
		var params = new ArrayList<Object[]>();

		for (Format format : Format.values()) {
			for (OpenApiVersion version : OpenApiVersion.values()) {
				params.add(new Object[] { format, version });
			}
		}

		return params;
	}

	@Parameter(0)
	public Format format;

	@Parameter(1)
	public OpenApiVersion version;

	private final List<Pair<String, String>> testedPaths = Arrays.asList(
		Pair.of("/api/version", "Version information"),
		Pair.of("/auth/logout", "Logout"));

	private KeycloakContainer keycloak;
	private String realmName;

	@Before
	public void setup() throws InterruptedException {
		var testSetting = getClass().getAnnotation(PortalTestSetting.class);

		realmName = testSetting.keycloakRealmName();

		keycloak = portal.keycloak();
		keycloak.start();
		keycloak.waitStartup();

		var realmUrl = String.format("http://%s:%d/realms/%s", keycloak.getHost(), keycloak.getMappedPort(8080), realmName);

		portal.portalConfig().getAuthOptions().getKeycloakOptions().setRealmUrl(realmUrl);

		var waitForReady = new CountDownLatch(1);

		Executors.newFixedThreadPool(1).execute(() -> {
			while (!((TestBootstrapInitializer) portal.portalServerComponent().boot()).getKeycloakAuthenticationHandler().isReady()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
			}
			waitForReady.countDown();
		});

		assertTrue("Portal | Java should be running", ((TestBootstrapInitializer) portal.portalServerComponent().boot()).isRunning());
		assertTrue("KeycloakAuthenticationHandler should be ready", waitForReady.await(30, TimeUnit.SECONDS));
	}

	@Test
	public void testOpenApi() {
		var api = getOpenApi();
		var paths = api.getPaths();

		// Check some paths, to make sure standard Portal paths and authentication paths are present.

		for (var testedPath: testedPaths) {
			var path = paths.get(testedPath.getKey());

			assertThat(path)
				.as("Path '%s' present".formatted(testedPath))
				.isNotNull();
			assertThat(path.getSummary())
				.as("Path '%s' description correct".formatted(testedPath))
				.isEqualTo(testedPath.getValue());
		}
	}

	protected OpenAPI getOpenApi() {
		var parser = new OpenAPIV3Parser();
		var options = new ParseOptions();

		options.setResolve(true);
		options.setResolveFully(true);

		var auth = new AuthorizationValue(SecurityHandler.HEADER_API_KEY, portal.portalConfig().getSecurityOptions().getApiKey(), SecurityScheme.Type.APIKEY.toString());
		var client = portal.portalClient();
		var rqOptions = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/api/openapi." + format.toString().toLowerCase() + "?version=" + version.toString().toLowerCase())
			.addHeader(SecurityHandler.HEADER_API_KEY, portal.portalConfig().getSecurityOptions().getApiKey());
		var response = getResponseWithBody(client.rxRequest(rqOptions).blockingGet());

		assertThat(response.getResponse().statusCode())
			.as("Response status code")
			.isEqualTo(HttpResponseStatus.OK.code());

		System.out.println(response.getBody());

		var result = parser.readContents(response.getBody(), Collections.singletonList(auth), options);

		assertThat(result.getMessages()).as("Error messages").isNullOrEmpty();
		assertThat(result.getOpenAPI()).as("Parsed API").isNotNull();
		assertThat(result.getOpenAPI().getOpenapi()).as("Spec version").isEqualTo((version == OpenApiVersion.V31) ? "3.1.0" : "3.0.1");

		return result.getOpenAPI();
	}
}
