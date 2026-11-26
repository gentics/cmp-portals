package com.gentics.mesh.portal.integration.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.portal.tracing.RequestTracing;
import com.gentics.testcontainers.KeycloakContainer;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;

/**
 * Test cases for the tracing of the login with Keycloak: the OAuth2 code must not be recorded, the token request is part
 * of the trace of the callback request, and the ID of the logged in user is recorded.
 *
 * <p>
 *     Runs with the OpenTelemetry testing agent (see {@link RequestTracingTest}).
 * </p>
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", testData = TestData.REF, keycloakConfig = "/keycloak/realm-with-portal-client.json", startKeycloak = false, keycloakInitRetryPeriodSeconds = 3)
public class KeycloakRequestTracingTest extends AbstractEndToEndTest {
	private final static Pattern CODE = Pattern.compile("[?&]code=([^&]+)");

	@Before
	public void setup() throws InterruptedException {
		var testSetting = getClass().getAnnotation(PortalTestSetting.class);

		KeycloakContainer keycloak = portal.keycloak();
		keycloak.start();
		keycloak.waitStartup();

		var realmUrl = String.format("http://%s:%d/realms/%s", keycloak.getHost(), keycloak.getMappedPort(8080), testSetting.keycloakRealmName());

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

		assertTrue("KeycloakAuthenticationHandler should be ready", waitForReady.await(30, TimeUnit.SECONDS));
		ExportedSpans.reset();
	}

	@Test
	public void testLogin() throws InterruptedException {
		// login request to the portal
		RawHttp.Response response = RawHttp.get(portal.portalPort(), "/auth/login");
		assertThat(response.status()).as("Login request status").isEqualTo(302);
		List<SpanData> trace = ExportedSpans.trace(response.header(RequestTracing.TRACE_ID_HEADER));
		SpanData root = ExportedSpans.assertServerRooted(trace);
		assertThat(root.getName()).as("Name of the login request span").isEqualTo("GET /auth/login/:redirectPath");

		// login at Keycloak
		String callbackUrl = performKeycloakLogin(response.header("Location"));
		Matcher matcher = CODE.matcher(callbackUrl);
		assertThat(matcher.find()).as("Callback URL " + callbackUrl + " contains the code").isTrue();
		String code = matcher.group(1);

		// callback request to the portal
		response = RawHttp.get(portal.portalPort(), callbackUrl);
		assertThat(response.status()).as("Callback request status").isEqualTo(303);
		trace = ExportedSpans.trace(response.header(RequestTracing.TRACE_ID_HEADER));
		root = ExportedSpans.assertServerRooted(trace);

		assertThat(root.getAttributes().get(RequestTracing.URL_QUERY)).as("Query of the callback request")
			.contains("code=REDACTED")
			.doesNotContain(code);
		assertThat(trace).as("Spans of the callback request")
			.allSatisfy(span -> assertThat(span.getAttributes().asMap().values()).as("Attributes of " + span.getName())
				.noneMatch(value -> String.valueOf(value).contains(code)));
		assertThat(ExportedSpans.descendants(trace, root)).as("Token request to Keycloak")
			.anySatisfy(span -> {
				assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
				assertThat(span.getAttributes().get(RequestTracingTest.URL_FULL)).contains("/protocol/openid-connect/token");
			});
		String userId = root.getAttributes().get(RequestTracing.USER_ID);
		assertThat(userId).as("User ID on the callback request").isNotBlank();

		// follow the redirect, which sets the authentication cookies
		String cookies = cookies(response);
		response = RawHttp.get(portal.portalPort(), response.header("Location"), "Cookie", cookies);
		String updatedCookies = cookies(response);
		if (!updatedCookies.isEmpty()) {
			cookies = cookies + "; " + updatedCookies;
		}

		// a request of the logged in user
		ExportedSpans.reset();
		response = RawHttp.get(portal.portalPort(), "/api/authenticated", "Cookie", cookies);
		assertThat(response.status()).as("Authenticated request status").isEqualTo(200);
		root = ExportedSpans.assertServerRooted(ExportedSpans.trace(response.header(RequestTracing.TRACE_ID_HEADER)));
		assertThat(root.getAttributes().get(RequestTracing.USER_ID)).as("User ID on the authenticated request").isEqualTo(userId);
	}

	/**
	 * Get the cookies set by the response, as value for the Cookie header
	 * @param response response
	 * @return cookies
	 */
	private String cookies(RawHttp.Response response) {
		return response.headers("Set-Cookie").stream().map(value -> value.split(";", 2)[0]).collect(Collectors.joining("; "));
	}
}
