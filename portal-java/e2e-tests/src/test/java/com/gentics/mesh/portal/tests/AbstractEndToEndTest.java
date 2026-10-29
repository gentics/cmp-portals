package com.gentics.mesh.portal.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.ClientForm;
import io.vertx.core.http.HttpHeaders;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.ClassRule;

import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.server.PortalTestContext;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;

import static com.gentics.mesh.portal.util.RxUtils.string;
import static org.assertj.core.api.Assertions.assertThat;

public class AbstractEndToEndTest {

	// Pattern to extract the login action from the Keylcoak login page.
	private static final Pattern LOGIN_ACTION_PATTERN = Pattern.compile("<form.*action=\"http://[^/]+([^\"]+)\".*>");

	// Set to false when debugging to prevent test abortion after ten seconds.
	protected static boolean NODEBUG = false;

	@ClassRule
	public static PortalTestContext portal = new PortalTestContext();

	/**
	 * Send a request to the specifed URI using the monitoring client.
	 *
	 * <p>
	 * The response is retrieved by {@link Observable#blockingFirst()} with a timeout of ten seconds.
	 * </p>
	 *
	 * @param client
	 *            The HTTP client to use.
	 * @param uri
	 *            The URI to load
	 * @return The HTTP response for the loaded URI
	 */
	protected HttpClientResponse request(HttpClient client, String uri, String... headers) {
		RequestOptions options = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI(uri);

		if (NODEBUG) {
			options.setTimeout(10_000);
		}

		if (headers.length % 2 == 0) {
			for (int ii = 0; ii < headers.length - 1; ii += 2) {
				options.putHeader(headers[ii], headers[ii + 1]);
			}
		}

		return client.rxRequest(options).flatMap(HttpClientRequest::rxSend).blockingGet();
	}

	/**
	 * Get the response for the request with the given form.
	 * @param request The request to send.
	 * @param form The form data to send.
	 * @return The response.
	 */
	protected ResponseWithBody getResponseWithBody(HttpClientRequest request, ClientForm form) {
		return doGetResponse(request.rxSend(form));
	}

	/**
	 * Get the response for the given request without a body.
	 * @param request The request to send.
	 * @return The response.
	 */
	protected ResponseWithBody getResponseWithBody(HttpClientRequest request) {
		return doGetResponse(request.rxSend());
	}

	/**
	 * Get the response for the given request.
	 * @param response The request to send.
	 * @return The response.
	 */
	private ResponseWithBody doGetResponse(Single<HttpClientResponse> response) {
		var ret = response
			.flatMap(rs -> rs.rxBody().toFlowable().to(string())
				.map(body -> new ResponseWithBody(rs, body)));

		if (NODEBUG) {
			ret = ret.timeout(10, TimeUnit.SECONDS);
		}

		return ret.blockingGet();
	}

	/**
	 * Get the absolute path for the given resource path.
	 * @param path The path relative to the resources folder.
	 * @return The absolute path.
	 */
	public static String getResourcePath(String path) {
		return Paths.get(
			AbstractEndToEndTest.class.getResource("/").getPath(),
			path
		).toString();
	}

	/**
	 * Load a resource as a string.
	 * @param path The resource path.
	 * @return The resource content.
	 */
	public static String loadResource(String path) {
		try {
			return IOUtils.toString(AbstractEndToEndTest.class.getResourceAsStream(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Load the given resource as a REST model.
	 * @param path The resource path.
	 * @param clazz The REST model class.
	 * @return The loaded REST model.
	 */
	public static <T> T loadRestModel(String path, Class<T> clazz) {
		return JsonUtil.readValue(loadResource(path), clazz);
	}

	/**
	 * Post login data to the Keycloak login page and return the redirect URL.
	 * @param loginUrl The login URL to post to.
	 * @return The redirect URL from the login response.
	 */
	protected String performKeycloakLogin(String loginUrl) {
		var keycloakClient = portal.keycloakClient();
		var requestOptions = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI(loginUrl);
		var request = keycloakClient.rxRequest(requestOptions).blockingGet();
		var response = getResponseWithBody(request);
		var cookies = getCookies(response.getResponse());
		var loginBody = response.getBody();
		var matcher = LOGIN_ACTION_PATTERN.matcher(loginBody);

		assertThat(matcher.find())
			.as("Login action found")
			.isTrue();

		var loginAction = StringEscapeUtils.unescapeHtml(matcher.group(1));
		var loginData = ClientForm.form()
			.attribute("username", "dummyuser")
			.attribute("password", "dummypassword")
			.attribute("credentialId", "password");

		requestOptions = new RequestOptions()
			.setTimeout(-1L)
			.setIdleTimeout(-1L)
			.setConnectTimeout(-1L)
			.setMethod(HttpMethod.POST)
			.setURI(loginAction)
			.addHeader(HttpHeaders.COOKIE, cookies);

		var loginResponse = keycloakClient.rxRequest(requestOptions)
			.flatMap(rq -> rq.rxSend(loginData))
			.blockingGet();

		assertThat(loginResponse.statusCode())
			.as("Login request status code")
			.isEqualTo(HttpResponseStatus.FOUND.code());

		return StringUtils.removeStart(loginResponse.getHeader(HttpHeaders.LOCATION), "http://localhost");
	}

	/**
	 * Get the cookies from the given response.
	 * @param response The response to extract the cookies from.
	 * @return The cookies as string usable for the {@code Cookie} header.
	 */
	protected String getCookies(HttpClientResponse response) {
		return response.headers().entries().stream()
			.filter(e -> e.getKey().equalsIgnoreCase(HttpHeaders.SET_COOKIE.toString()))
			.map(Map.Entry::getValue)
			.map(val -> Pair.of(val, val.indexOf(';')))
			.map(valWithIdx -> valWithIdx.getKey().substring(0, valWithIdx.getValue()))
			.collect(Collectors.joining("; "));
	}
}
