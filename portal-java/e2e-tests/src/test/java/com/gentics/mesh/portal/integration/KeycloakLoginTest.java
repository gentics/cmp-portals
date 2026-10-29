package com.gentics.mesh.portal.integration;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.testcontainers.KeycloakContainer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.ClientForm;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", testData = TestData.REF, keycloakConfig = "/keycloak/realm-with-portal-client.json", startKeycloak = false, keycloakInitRetryPeriodSeconds = 3)
public class KeycloakLoginTest extends AbstractEndToEndTest {

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
		assertTrue("KeycloakAuthenticationHandler should be ready", waitForReady.await(30,TimeUnit.SECONDS));
	}

	@Test
	public void testLogin() {
		var requestOptions = new RequestOptions().setURI("/api/authenticated");
		var request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		var response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Authenticated request status code")
			.isEqualTo(HttpResponseStatus.UNAUTHORIZED.code());

		// Login request to portal.
		requestOptions = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/auth/login");
		request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Login request status code")
			.isEqualTo(HttpResponseStatus.FOUND.code());

		// Perform Keycloak login.
		var callbackUrl = performKeycloakLogin(response.getResponse().getHeader(HttpHeaders.LOCATION));

		// Request to the login callback URL.
		requestOptions.setURI(callbackUrl);
		request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Callback request status code")
			.isEqualTo(HttpResponseStatus.SEE_OTHER.code());

		var cookies = getCookies(response.getResponse());

		assertThat(cookies)
			.as("Callback redirect response contains auth cookie")
			.containsIgnoringCase(AuthenticationHandler.COOKIE_AUTH);

		// Request to logged in endpoint.
		requestOptions
			.setURI(response.getResponse().getHeader(HttpHeaders.LOCATION))
			.addHeader(HttpHeaders.COOKIE, cookies);
		request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		response = getResponseWithBody(request);
		cookies = getCookies(response.getResponse());

		assertThat(response.getResponse().statusCode())
			.as("Logged in request status code")
			.isEqualTo(HttpResponseStatus.SEE_OTHER.code());
		assertThat(response.getResponse().getHeader(HttpHeaders.LOCATION))
			.as("Logged in redirect URL")
			.isEqualTo("/");

		// Login procedure is finished, now make a final request to make sure the user is authenticated correctly
		// (the /api/authenticated endpoint will return 200 when authentication succeeds and 401 otherwise).
		requestOptions
			.setURI("/api/authenticated")
			.addHeader(HttpHeaders.COOKIE, cookies);
		request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Authenticated request status code")
			.isEqualTo(HttpResponseStatus.OK.code());
	}

}
