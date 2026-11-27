package com.gentics.mesh.portal.integration.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.DataProviderOptions;
import com.gentics.mesh.portal.content.TemplateHandler;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.portal.tracing.RequestTracing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;

/**
 * Test cases for the tracing of requests with the OpenTelemetry Java agent and the sampler of the portal extension.
 *
 * <p>
 *     Runs with the OpenTelemetry testing agent and the extension (execution "tracing-tests" of the surefire plugin),
 *     which export the spans into memory. Requests are sent with {@link RawHttp}, so that they do not carry a trace context.
 * </p>
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF, configProvider = RequestTracingTest.Config.class)
public class RequestTracingTest extends AbstractEndToEndTest {
	public final static AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");

	public final static AttributeKey<String> URL_FULL = AttributeKey.stringKey("url.full");

	public final static String CONTENT_PAGE = "/start.de.html";

	public final static String IMAGE = "/products/Svg_example4.svg";

	/**
	 * Configuration with a data provider, which refreshes every second (background work)
	 */
	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();

			DataProviderOptions dataProviderOptions = portalConfig.getDataProviderOptions();
			dataProviderOptions.getMeshData().put("image", Arrays.asList(IMAGE));
			dataProviderOptions.setRefreshTime(1);

			return portalConfig;
		}
	}

	@Before
	public void setup() {
		ExportedSpans.reset();
	}

	/**
	 * Test that a content request produces one trace with the server span as root, which contains the requests to Mesh,
	 * the cache lookup and the rendering, and that the trace ID is returned in the response header
	 * @throws InterruptedException
	 */
	@Test
	public void testContentRequest() throws InterruptedException {
		RawHttp.Response response = RawHttp.get(portal.portalPort(), CONTENT_PAGE);
		assertThat(response.status()).as("Response status").isEqualTo(200);
		String traceId = response.header(RequestTracing.TRACE_ID_HEADER);

		List<SpanData> trace = ExportedSpans.trace(traceId);
		SpanData root = ExportedSpans.assertServerRooted(trace);
		assertThat(root.getName()).as("Name of the request span").isEqualTo("GET /*");
		assertThat(root.getAttributes().get(HTTP_ROUTE)).as("Route of the request span").isEqualTo("/*");

		assertThat(trace).as("Mesh GraphQL requests")
			.anySatisfy(span -> {
				assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
				assertThat(span.getAttributes().get(URL_FULL)).contains("/graphql");
			});

		Optional<SpanData> cacheLookup = ExportedSpans.byName(trace, "Page cache lookup");
		assertThat(cacheLookup).as("Cache lookup span").isPresent();
		assertThat(cacheLookup.get().getAttributes().get(CachingHandler.CACHE_HIT)).as("Cache hit").isFalse();

		Optional<SpanData> render = ExportedSpans.byName(trace, "Render template");
		assertThat(render).as("Rendering span").isPresent();
		assertThat(render.get().getAttributes().get(TemplateHandler.TEMPLATE_NAME)).as("Template name").isNotBlank();

		assertThat(ExportedSpans.all()).as("All exported spans belong to the request").allMatch(s -> s.getTraceId().equals(traceId));

		// the same page again (with the language cookie like a browser, the language is part of the cache key)
		// is a cache hit, in its own trace
		String languageCookie = response.header("Set-Cookie").split(";", 2)[0];
		ExportedSpans.reset();
		response = RawHttp.get(portal.portalPort(), CONTENT_PAGE, "Cookie", languageCookie);
		String secondTraceId = response.header(RequestTracing.TRACE_ID_HEADER);
		assertThat(secondTraceId).as("Trace ID of the second request").isNotEqualTo(traceId);

		trace = ExportedSpans.trace(secondTraceId);
		ExportedSpans.assertServerRooted(trace);
		cacheLookup = ExportedSpans.byName(trace, "Page cache lookup");
		assertThat(cacheLookup).as("Cache lookup span").isPresent();
		assertThat(cacheLookup.get().getAttributes().get(CachingHandler.CACHE_HIT)).as("Cache hit").isTrue();
	}

	/**
	 * Test that the request span of a GenticsImageStore request is named after the GenticsImageStore route
	 * (also after the request was rerouted to the image)
	 * @throws InterruptedException
	 */
	@Test
	public void testImageStoreRequest() throws InterruptedException {
		RawHttp.Response response = RawHttp.get(portal.portalPort(), "/GenticsImageStore/100/auto/prop" + IMAGE);
		String traceId = response.header(RequestTracing.TRACE_ID_HEADER);

		List<SpanData> trace = ExportedSpans.trace(traceId);
		SpanData root = ExportedSpans.assertServerRooted(trace);
		assertThat(root.getName()).as("Name of the request span").isEqualTo("GET /GenticsImageStore/:width/:height/:mode/:path");
		assertThat(root.getAttributes().get(HTTP_ROUTE)).as("Route of the request span").isEqualTo("/GenticsImageStore/:width/:height/:mode/:path");
		assertThat(trace).as("Requests to Mesh").anyMatch(s -> s.getKind() == SpanKind.CLIENT);
	}

	/**
	 * Test that log events written while a request is handled carry the trace ID (in the MDC for the log pattern,
	 * and in the exported log records)
	 * @throws InterruptedException
	 */
	@Test
	public void testTraceIdInLogs() throws InterruptedException {
		Logger logger = (Logger) LoggerFactory.getLogger("com.gentics.mesh.portal");
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		try {
			// the cache clear endpoint logs, whether the request is allowed or not
			RawHttp.Response response = RawHttp.request(portal.portalPort(), "POST", "/api/clearCache", "{\"key\":\"wrong\"}",
					"Content-Type", "application/json");
			String traceId = response.header(RequestTracing.TRACE_ID_HEADER);
			ExportedSpans.trace(traceId);

			assertThat(appender.list).as("Log events of the request")
				.filteredOn(e -> e.getFormattedMessage().contains("/api/clearCache") || e.getFormattedMessage().contains("Cache clear"))
				.isNotEmpty()
				.allSatisfy(e -> assertThat(e.getMDCPropertyMap()).as("MDC of " + e.getFormattedMessage()).containsEntry("trace_id", traceId));

			assertThat(ExportedSpans.logRecords(traceId)).as("Exported log records of the request")
				.anySatisfy(r -> assertThat(r.getBodyValue().asString()).containsAnyOf("/api/clearCache", "Cache clear"));
		} finally {
			logger.detachAppender(appender);
		}
	}

	/**
	 * Test that the background work of the portal (here: the refresh of the data provider every second) is not exported
	 * @throws InterruptedException
	 */
	@Test
	public void testBackgroundWorkNotExported() throws InterruptedException {
		ExportedSpans.assertNothingExported(3_500, "3.5 seconds without requests (data provider refreshes every second)");
	}

	/**
	 * Test that no work of the data provider refresh, which runs in the background, is part of the trace of a request
	 * @throws InterruptedException
	 */
	@Test
	public void testBackgroundWorkNotInRequestTrace() throws InterruptedException {
		// let the data provider refresh while requests are handled
		for (int i = 0; i < 4; i++) {
			RawHttp.Response response = RawHttp.get(portal.portalPort(), CONTENT_PAGE + "?run=" + i);
			ExportedSpans.trace(response.header(RequestTracing.TRACE_ID_HEADER));
			Thread.sleep(500);
		}

		List<SpanData> spans = ExportedSpans.all();
		assertThat(spans).as("Spans requesting the data provider image " + IMAGE)
			.noneMatch(s -> String.valueOf(s.getAttributes().get(URL_FULL)).contains("Svg_example4.svg"));
		spans.stream().map(SpanData::getTraceId).distinct()
			.forEach(traceId -> ExportedSpans.assertServerRooted(spans.stream().filter(s -> s.getTraceId().equals(traceId)).toList()));
	}

	/**
	 * Test that requests to static files and the FormGen app are not traced
	 * @throws InterruptedException
	 */
	@Test
	public void testStaticFilesNotTraced() throws InterruptedException {
		for (String path : List.of("/static/css/style.css", "/formgen/index.js")) {
			RawHttp.Response response = RawHttp.get(portal.portalPort(), path);
			assertThat(response.header(RequestTracing.TRACE_ID_HEADER)).as("Trace ID header for " + path).isNull();
		}
		ExportedSpans.assertNothingExported(1_000, "static files");
	}

	/**
	 * Test that the excluded path prefixes can be set with the environment variable (the test execution adds /excluded-by-env/)
	 * @throws InterruptedException
	 */
	@Test
	public void testPrefixFromEnvironmentNotTraced() throws InterruptedException {
		RawHttp.Response response = RawHttp.get(portal.portalPort(), "/excluded-by-env/page.html");
		assertThat(response.header(RequestTracing.TRACE_ID_HEADER)).as("Trace ID header").isNull();
		ExportedSpans.assertNothingExported(1_000, "a path with a prefix from the environment variable");
	}

	/**
	 * Test that requests from the Blackbox exporter are not traced
	 * @throws InterruptedException
	 */
	@Test
	public void testBlackboxNotTraced() throws InterruptedException {
		RawHttp.Response response = RawHttp.get(portal.portalPort(), CONTENT_PAGE, "User-Agent", "Blackbox Exporter/0.27.0");
		assertThat(response.status()).as("Response status").isEqualTo(200);
		assertThat(response.header(RequestTracing.TRACE_ID_HEADER)).as("Trace ID header").isNull();
		ExportedSpans.assertNothingExported(1_000, "a request from the Blackbox exporter");
	}

	/**
	 * Test that the health and metrics endpoints of the monitoring server are not traced
	 * @throws InterruptedException
	 */
	@Test
	public void testMonitoringNotTraced() throws InterruptedException {
		for (String path : List.of("/health/live", "/health/ready", "/metrics")) {
			RawHttp.Response response = RawHttp.get(portal.monitoringPort(), path);
			assertThat(response.status()).as("Response status for " + path).isEqualTo(200);
		}
		ExportedSpans.assertNothingExported(1_000, "the monitoring endpoints");
	}

	@After
	public void tearDown() {
		ExportedSpans.reset();
	}
}
