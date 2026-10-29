package com.gentics.mesh.portal.integration;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

import static com.gentics.mesh.portal.integration.PluginProxyTest.ConfigProvider;
import static org.assertj.core.api.Assertions.assertThat;

@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	startupChecks = TestContextStartupCheckMode.SKIP,
	configProvider = ConfigProvider.class)
public class PluginProxyTest extends AbstractEndToEndTest {

	private static final int NETWORK_TIMEOUT = 5;
	private static final String SEARCH_TEST_RESPONSE = "FAKE_SEARCH_RESULT";

	public static class ConfigProvider implements Supplier<PortalConfig> {

		@Override
		public PortalConfig get() {
			PortalConfig config = new DefaultPortalConfig().get();

			config.setNetworkTimeout(NETWORK_TIMEOUT);

			return config;
		}
	}

	@Rule
	public final MockServerRule mockServer = new MockServerRule(this);

	private MockServerClient mockServerClient;

	@Before
	public void setup() {
		System.out.printf("Mockserver port: %d\n", mockServer.getPort());

		portal.portalConfig().getMeshOptions().setPort(mockServer.getPort());

		mockServerClient
			.when(HttpRequest.request("/echo").withMethod("POST"))
			.respond(rq -> HttpResponse.response("ECHO: " + rq.getBodyAsString()).withStatusCode(HttpResponseStatus.OK.code()));

		mockServerClient
			.when(HttpRequest.request("/api/v2/demo/plugins/search/search"))
			.respond(rq -> HttpResponse.response(SEARCH_TEST_RESPONSE).withStatusCode(HttpResponseStatus.OK.code()));

		mockServerClient
			.when(HttpRequest.request("/redirect").withMethod("GET"))
			.respond(rq -> HttpResponse.response("").withHeader("Location", "/echo").withStatusCode(HttpResponseStatus.SEE_OTHER.code()));

		mockServerClient
			.when(HttpRequest.request())
			.respond(HttpResponse.response().withStatusCode(HttpResponseStatus.NOT_IMPLEMENTED.code()));
	}

	@Test
	public void testDirectEcho() throws IOException {
		doTest("/api/echo");
	}

	@Test
	public void testProxyEcho() throws IOException {
		doTest("/api/proxy/echo");
	}

	/**
	 * Test that the proxy does not follow redirects itself, but forwards the redirect to the client
	 * @throws IOException
	 */
	@Test
	public void testProxyRedirect() throws IOException {
		OkHttpClient client = new OkHttpClient.Builder()
				.callTimeout(Duration.ZERO)
				.readTimeout(Duration.ZERO)
				.writeTimeout(Duration.ZERO)
				.followRedirects(false)
				.followSslRedirects(false)
				.build();
		String redirectUrl = String.format("http://localhost:%d%s", portal.portalPort(), "/api/proxy/redirect");
		Request request = new Request.Builder()
				.url(redirectUrl)
				.build();
		Call call = client.newCall(request);
		Response response = call.execute();
		assertThat(response.code())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.SEE_OTHER.code());
		assertThat(response.header("Location"))
			.as("Location Header")
			.isEqualTo("/echo");
	}

	/**
	 * Test forwarding to a plugin via the {@link com.gentics.mesh.portal.plugins.CompatibilityHandlerProvider} using
	 * a POST request.
	 */
	@Test
	public void testSearchPluginProxy() throws IOException {
		OkHttpClient client = new OkHttpClient.Builder()
			.callTimeout(Duration.ZERO)
			.readTimeout(Duration.ZERO)
			.writeTimeout(Duration.ZERO)
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
		String searchUrl = String.format("http://localhost:%d%s", portal.portalPort(), "/search?q=test");
		Request request = new Request.Builder()
			// Use an empty POST request to make sure, there is no conflict with the body handler of the portal.
			.post(RequestBody.create(null, new byte[0]))
			.url(searchUrl)
			.build();
		Call call = client.newCall(request);

		try (Response response = call.execute()) {
			assertThat(response.code())
				.as("Response status")
				.isEqualTo(HttpResponseStatus.OK.code());
			assertThat(response.body().string())
				.as("Response body")
				.isEqualTo(SEARCH_TEST_RESPONSE);
		}
	}

	private void doTest(String path) throws IOException {
		String message = "This is a test.";
		String contentType = "text/plain";
		OkHttpClient client = new OkHttpClient.Builder()
			.callTimeout(Duration.ZERO)
			.readTimeout(Duration.ZERO)
			.writeTimeout(Duration.ZERO)
			.build();
		Headers headers = new Headers.Builder()
			.add(HttpHeaders.CONTENT_TYPE.toString(), contentType)
			.add(HttpHeaders.CONTENT_LENGTH.toString(), String.valueOf(message.length()))
			.build();
		RequestBody body = new RequestBody() {
			@Nullable
			@Override
			public MediaType contentType() {
				return MediaType.parse(contentType);
			}

			@Override
			public long contentLength() {
				return message.length();
			}

			@Override
			public void writeTo(BufferedSink sink) throws IOException {
				long sleep = NETWORK_TIMEOUT * 1000 * 2 / message.length();

				for (byte b : message.getBytes(StandardCharsets.UTF_8)) {
					sink.writeByte(b);
					sink.flush();
					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
						// Nothing to do here really. We sleep after each byte anyways, and the total time would be
						// double the timeout.
						System.err.printf("Sleep was interrupted: %s\n", e.getMessage());
					}
				}
			}
		};
		String echoUrl = String.format("http://localhost:%d%s", portal.portalPort(), path);

		Request request = new Request.Builder()
			.method("POST", body)
			.headers(headers)
			.url(echoUrl)
			.build();
		Call call = client.newCall(request);
		Response response = call.execute();

		assertThat(response.code())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
	}
}
