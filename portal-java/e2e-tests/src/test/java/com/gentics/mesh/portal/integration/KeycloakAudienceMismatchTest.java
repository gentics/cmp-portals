package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
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
import io.vertx.core.json.JsonObject;

/**
 * Tests that tokens whose {@code aud} claim does not contain the configured
 * client ID are not accepted as a valid login (SUP-19986).
 *
 * <p>
 *     The realm used here has an audience mapper on the {@code portal} client,
 *     which puts {@code mesh} instead of {@code portal} into the access token
 *     audience. The client is confidential, because only then can the Portal
 *     introspect tokens from the authentication cookie.
 * </p>
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", testData = TestData.REF, keycloakConfig = "/keycloak/realm-with-portal-client-foreign-audience.json", startKeycloak = false, keycloakInitRetryPeriodSeconds = 3)
public class KeycloakAudienceMismatchTest extends AbstractEndToEndTest {

	/** Client secret of the {@code portal} client in the realm JSON. */
	private static final String CLIENT_SECRET = "sup-19986-test-secret";

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

		portal.portalConfig().getAuthOptions().getKeycloakOptions()
			.setRealmUrl(realmUrl)
			.setClientSecret(CLIENT_SECRET);

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

	/**
	 * The login callback must fail and must not set authentication cookies
	 * when the access token audience does not match the client ID.
	 */
	@Test
	public void testLoginWithForeignAudienceIsRejected() {
		var requestOptions = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/auth/login");
		var request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		var response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Login request status code")
			.isEqualTo(HttpResponseStatus.FOUND.code());

		var callbackUrl = performKeycloakLogin(response.getResponse().getHeader(HttpHeaders.LOCATION));

		requestOptions.setURI(callbackUrl);
		request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Callback request status code")
			.isEqualTo(HttpResponseStatus.UNAUTHORIZED.code());
		assertThat(getCookies(response.getResponse()))
			.as("Callback response cookies")
			.doesNotContainIgnoringCase(AuthenticationHandler.COOKIE_AUTH);
	}

	/**
	 * A request with an authentication cookie containing an access token with
	 * a foreign audience must be handled as anonymous.
	 */
	@Test
	public void testAuthCookieWithForeignAudienceIsAnonymous() {
		var tokenRequestOptions = new RequestOptions()
			.setMethod(HttpMethod.POST)
			.setURI(String.format("/realms/%s/protocol/openid-connect/token", realmName));
		var tokenForm = ClientForm.form()
			.attribute("grant_type", "password")
			.attribute("client_id", "portal")
			.attribute("client_secret", CLIENT_SECRET)
			.attribute("scope", "openid")
			.attribute("username", "dummyuser")
			.attribute("password", "dummypassword");
		// The form must be sent on the request's context thread, so send it inside flatMap
		// (same pattern as AbstractEndToEndTest#performKeycloakLogin).
		var tokenResponse = portal.keycloakClient().rxRequest(tokenRequestOptions)
			.flatMap(rq -> rq.rxSend(tokenForm))
			.flatMap(rs -> rs.rxBody().map(body -> new ResponseWithBody(rs, body.toString())))
			.blockingGet();

		assertThat(tokenResponse.getResponse().statusCode())
			.as("Token request status code")
			.isEqualTo(HttpResponseStatus.OK.code());

		var accessToken = new JsonObject(tokenResponse.getBody()).getString("access_token");

		assertThat(accessToken).as("Access token").isNotBlank();

		var requestOptions = new RequestOptions()
			.setURI("/api/authenticated")
			.addHeader(HttpHeaders.COOKIE, AuthenticationHandler.COOKIE_AUTH + "=" + accessToken);
		var request = portal.portalClient().rxRequest(requestOptions).blockingGet();
		var response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Authenticated request status code")
			.isEqualTo(HttpResponseStatus.UNAUTHORIZED.code());
	}
}
