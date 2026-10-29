package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.config.MeshAuthenticationMode;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.EncryptionMode;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Test the {@link MeshOptions#getCookieForwardWhitelist() cookie forwarding whitelist} and the
 * {@link MeshOptions#getAuthenticationMode() authentication mode}.
 */
@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	startupChecks = TestContextStartupCheckMode.SKIP,
	configProvider = PluginCookieWhitelistTest.Config.class)
@RunWith(Parameterized.class)
public class PluginCookieWhitelistTest extends AbstractEndToEndTest {

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig config = new DefaultPortalConfig().get();
			SecurityOptions securityOptions = config.getSecurityOptions();

			securityOptions.setEncryptionMode(EncryptionMode.PASSWORD_ONLY_KEY)
				.setEncryptionPassword("12345");

			return config;
		}
	}

	/**
	 * Different authentication modes for tests.
	 */
	private enum AuthenticationMode {
		NONE,
		NORMAL,
		ENCRYPTED
	}

	public final String RS_FIELD_MESSAGE = "echo";
	public final String RS_FIELD_AUTHORIZATION = "authorization";
	public final String RS_FIELD_COOKIES = "cookies";

	private final String AUTH_TOKEN = "test_user_token";

	@Parameters(name = "{index}: auth mode: {0}, cookies: {1}")
	public static Collection<Object[]> data() {
		Collection<Object[]> data = new ArrayList<>();

		for (MeshAuthenticationMode mode : MeshAuthenticationMode.values()) {
			data.add(new Object[] { mode, Collections.emptyList() });
			data.add(new Object[] { mode, Collections.singletonList("cookieFoo") });
			data.add(new Object[] { mode, Arrays.asList("forms-data", "cookieFoo") });
		}

		return data;
	}

	@Rule
	public final MockServerRule mockServer = new MockServerRule(this);

	private static final Map<String, String> COOKIES = new HashMap<String, String>() {{
		put("forms-data", "data");
		put("forms-voted", "bar:doo");
		put("cookieFoo", "foo");
		put("evilCookie", "Muhaha");
	}};

	@Parameter
	public MeshAuthenticationMode authForwardMode;

	@Parameter(1)
	public Collection<String> cookieWhitelist;

	private MockServerClient mockServerClient;
	private EncryptionService encryptionService;

	@Before
	public void setup() {
		System.out.printf("Mockserver port: %d\n", mockServer.getPort());

		encryptionService = portal.portalServerComponent().runner().getEncryptionService();

		PortalConfig portalConfig = portal.portalConfig();

		portalConfig.getSecurityOptions().setEncryptCookies(false);

		MeshOptions meshOptions = portalConfig.getMeshOptions();

		meshOptions
			.setPort(mockServer.getPort())
			.setAuthenticationMode(authForwardMode)
			.setCookieForwardWhitelist(new HashSet<>(cookieWhitelist));

		mockServerClient
			.when(HttpRequest.request("/echo").withMethod("POST"))
			.respond(rq -> {
				String body = new JsonObject()
					.put(RS_FIELD_MESSAGE, rq.getBodyAsString())
					.put(RS_FIELD_AUTHORIZATION, rq.getFirstHeader(HttpHeaders.AUTHORIZATION.toString()))
					.put(RS_FIELD_COOKIES, rq.getCookieList().stream()
						.map(c -> String.format("%s=%s", c.getName().getValue(), c.getValue().getValue()))
						.collect(Collectors.toList()))
					.encodePrettily();

				return HttpResponse.response(body)
					.withCookies(rq.getCookies())
					.withHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
					.withStatusCode(HttpResponseStatus.OK.code());
			});

		mockServerClient
			.when(HttpRequest.request())
			.respond(HttpResponse.response().withStatusCode(HttpResponseStatus.NOT_IMPLEMENTED.code()));
	}

	/**
	 * Test the cookie whitelist without an authentication cookie.
	 */
	@Test
	public void testCookieWhitelistAnonymous() throws IOException {
		performTest(AuthenticationMode.NONE);
	}

	/**
	 * Test the cookie whitelist with an authentication cookie.
	 */
	@Test
	public void testCookieWhitelistAuthenticated() throws IOException{
		performTest(AuthenticationMode.NORMAL);
	}

	/**
	 * Test the cookie whitelist with an encrypted authentication cookie.
	 */
	@Test
	public void testCookieWhitelistAuthenticatedEncrypted() throws IOException{
		portal.portalConfig().getSecurityOptions().setEncryptCookies(true);
		performTest(AuthenticationMode.ENCRYPTED);
	}

	/**
	 * Perform a request to /api/proxy/echo and verify the correct headers
	 * and cookies are sent to Mesh.
	 *
	 * @param testAuthMode Whether the request should be authenticated and
	 * 	whether the cookie should be encrypted.
	 */
	private void performTest(AuthenticationMode testAuthMode) throws IOException {
		String message = "This is a test.";
		String contentType = "text/plain";
		Function<String, String> encrypter = testAuthMode == AuthenticationMode.ENCRYPTED
			? encryptionService::encrypt
			: Function.identity();
		OkHttpClient client = new OkHttpClient.Builder()
			.callTimeout(Duration.ZERO)
			.readTimeout(Duration.ZERO)
			.writeTimeout(Duration.ZERO)
			.build();
		Headers headers = new Headers.Builder()
			.add(HttpHeaders.CONTENT_TYPE.toString(), contentType)
			.add(HttpHeaders.CONTENT_LENGTH.toString(), String.valueOf(message.length()))
			.add(HttpHeaders.COOKIE.toString(), getTestCookies(testAuthMode != AuthenticationMode.NONE, encrypter))
			.build();
		RequestBody body = RequestBody.create(MediaType.parse(contentType), message);
		String echoUrl = String.format("http://localhost:%d%s", portal.portalPort(), "/api/proxy/echo");

		System.out.printf("Setting \"%s\" to %s\n", message, echoUrl);

		Request request = new Request.Builder()
			.method("POST", body)
			.headers(headers)
			.url(echoUrl)
			.build();
		Call call = client.newCall(request);
		JsonObject responseBody;

		try (Response response = call.execute()) {
			responseBody = new JsonObject(response.body().string());

			assertThat(response.code())
				.as("Response status")
				.isEqualTo(HttpResponseStatus.OK.code());
		}

		List<String> expectedCookies = cookieWhitelist.stream()
			.map(cookieName -> String.format("%s=%s", cookieName, encrypter.apply(COOKIES.get(cookieName))))
			.collect(Collectors.toList());

		if (testAuthMode != AuthenticationMode.NONE && authForwardMode != MeshAuthenticationMode.HEADER) {
			// Even if we send the cookies encrypted, the authentication token must be decrypted by the portal.
			expectedCookies.add("mesh.token=" + AUTH_TOKEN);
		}

		assertThat(responseBody.getJsonArray(RS_FIELD_COOKIES))
			.as("Sent cookies")
			.containsExactlyInAnyOrder(expectedCookies.toArray());

		assertThat(StringUtils.isNotBlank(responseBody.getString(RS_FIELD_AUTHORIZATION)))
			.as("Authorization header present")
			.isEqualTo(testAuthMode != AuthenticationMode.NONE && authForwardMode != MeshAuthenticationMode.COOKIE);
	}

	/**
	 * Returns a string representation of {@link #COOKIES} with an additional
	 * authentication cookie if {@code addAuthCookie} is {@code true}.
	 *
	 * <p>
	 *     The {@code encrypter} function is appliled to all cookie values.
	 * </p>
	 *
	 * @param addAuthCookie Whether to add the authentication cookie.
	 * @param encrypter The encrypting function for the cookie values.
	 * @return The cookies for this test, optionally with an added
	 * 		authentication cookie.
	 */
	private String getTestCookies(boolean addAuthCookie, Function<String, String> encrypter) {
		String otherCookies = COOKIES.entrySet().stream()
				.map(e -> String.format("%s=%s", e.getKey(), encrypter.apply(e.getValue())))
				.collect(Collectors.joining("; "));

		if (addAuthCookie) {
			return String.format("%s; %s=%s", otherCookies, AuthenticationHandler.COOKIE_AUTH, encrypter.apply(AUTH_TOKEN));
		}

		return otherCookies;
	}
}
