package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.testcontainers.KeycloakContainer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.reactivex.core.http.HttpClientRequest;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "en", testData = TestData.REF, keycloakConfig = "/keycloak/realm.json", 
	startKeycloak = false, keycloakInitRetryPeriodSeconds = 3)
public class KeycloakDelayedInitializationTest extends AbstractEndToEndTest {

	private KeycloakContainer keycloak;
	private String realmName;

	@Before
	public void setup() {
		PortalTestSetting testSetting = getClass().getAnnotation(PortalTestSetting.class);

		keycloak = portal.keycloak();
		realmName = testSetting.keycloakRealmName();
	}

	@Test
	public void testKeycloakStartDelayed() throws InterruptedException {
		// Just loading the unprotected start-page must succeed even when Keycloak is not started yet.
		assertStartPageAvailable();
		// Login must not be available when Keycloak is not ready.
		assertLoginAvailable(false);

		keycloak.start();

		String realmUrl = String.format("http://%s:%d/realms/%s", keycloak.getHost(), keycloak.getMappedPort(8080), realmName);

		System.out.printf("Keycloak realm URL: %s\n", realmUrl);

		portal.portalConfig().getAuthOptions().getKeycloakOptions().setRealmUrl(realmUrl);
		keycloak.waitStartup();
		CountDownLatch waitForReady = new CountDownLatch(1);
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

		// Logon requests must be redirected to Keycloak after it becomes available.
		assertLoginAvailable(true);
	}

	/**
	 * Assert that the start-page is served.
	 */
	private void assertStartPageAvailable() {
		RequestOptions options = new RequestOptions()
				.setMethod(HttpMethod.GET)
				.setURI("/start.en.html");

		HttpClientRequest request = portal.portalClient().rxRequest(options).blockingGet();
		ResponseWithBody response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
				.as("Startpage response status")
				.isEqualTo(HttpResponseStatus.OK.code());

		assertThat(response.getBody().trim())
				.as("Startpage response body")
				.isEqualTo(loadResource("/assertions/ref-start.en.html").trim());

	}

	/**
	 * Assert that login is available or not.
	 *
	 * <p>
	 *     When {@code expected} is {@code true} the response from the portal must be a 404 NOT FOUND (meaning the
	 *     {@link com.gentics.mesh.portal.authentication.oauth2.KeycloakAuthenticationHandler} has not added its
	 *     routes yet). Otherwise is must be a 302 FOUND with the {@code Location} header pointing to the openid-connect
	 *     authentication endpoint for the realm.
	 * </p>
	 *
	 * @param expected Whether Keycloak is expected to work or not.
	 */
	private void assertLoginAvailable(boolean expected) {
		RequestOptions options = new RequestOptions()
				.setMethod(HttpMethod.GET)
				.setURI("/auth/login");
		HttpClientRequest request = portal.portalClient().rxRequest(options).blockingGet();
		ResponseWithBody response = getResponseWithBody(request);

		if (expected) {
			assertThat(response.getResponse().statusCode())
				.as("Login request status code")
				.isEqualTo(HttpResponseStatus.FOUND.code());

			String redirect = response.getResponse().getHeader("Location");
			String realmUrl = String.format("http://%s:%d/realms/%s", keycloak.getHost(), keycloak.getMappedPort(8080), realmName);

			assertThat(redirect)
				.as("Login response redirect")
				.startsWith(realmUrl + "/protocol/openid-connect/auth");
		} else {
			assertThat(response.getResponse().statusCode())
				.as("Login request status code")
				.isEqualTo(HttpResponseStatus.NOT_FOUND.code());
		}
	}
}
