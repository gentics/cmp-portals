package com.gentics.mesh.portal.integration.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.TelemetryConverter;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.testing.internal.proto.collector.trace.v1.ExportTraceServiceRequest;

/**
 * Spans and log records exported by the OpenTelemetry Java agent, which runs the portal in the tracing tests.
 *
 * <p>
 *     The agent exports them with OTLP (http/protobuf) to the receiver started by this class on the port given by the
 *     system property {@code tracing.otlp.port} (see the execution "tracing-tests" in the pom). The instrumentation of the
 *     JDK HTTP server is switched off for the tests, so the receiver is not traced itself.
 * </p>
 */
public final class ExportedSpans {
	/**
	 * Maximum time in milliseconds to wait for the spans of a request
	 */
	public final static long TIMEOUT_MS = 10_000;

	private final static List<SpanData> SPANS = new CopyOnWriteArrayList<>();

	private final static List<LogRecordData> LOG_RECORDS = new CopyOnWriteArrayList<>();

	static {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", Integer.getInteger("tracing.otlp.port", 24318)), 0);
			server.createContext("/v1/traces", exchange -> receive(exchange,
					body -> SPANS.addAll(TelemetryConverter.getSpanData(ExportTraceServiceRequest.parseFrom(body).getResourceSpansList()))));
			server.createContext("/v1/logs", exchange -> receive(exchange,
					body -> LOG_RECORDS.addAll(TelemetryConverter.getLogRecordData(ExportLogsServiceRequest.parseFrom(body).getResourceLogsList()))));
			server.start();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private ExportedSpans() {
	}

	/**
	 * Remove all received spans and log records
	 */
	public static void reset() {
		SPANS.clear();
		LOG_RECORDS.clear();
	}

	/**
	 * Get all received spans
	 * @return spans
	 */
	public static List<SpanData> all() {
		return new ArrayList<>(SPANS);
	}

	/**
	 * Get all received log records
	 * @return log records
	 */
	public static List<LogRecordData> logRecords() {
		return new ArrayList<>(LOG_RECORDS);
	}

	/**
	 * Wait until log records with the given trace ID were exported and return them
	 * @param traceId trace ID
	 * @return log records of the trace
	 * @throws InterruptedException
	 */
	public static List<LogRecordData> logRecords(String traceId) throws InterruptedException {
		long end = System.currentTimeMillis() + TIMEOUT_MS;
		while (System.currentTimeMillis() < end) {
			List<LogRecordData> records = logRecords().stream().filter(r -> r.getSpanContext().getTraceId().equals(traceId))
					.collect(Collectors.toList());
			if (!records.isEmpty()) {
				return records;
			}
			Thread.sleep(100);
		}
		return fail("No log records of trace " + traceId + " were exported within " + TIMEOUT_MS + " ms, exported log records: " + logRecords());
	}

	/**
	 * Wait until the root span of the trace with the given ID was exported and return all spans of the trace
	 * @param traceId trace ID
	 * @return spans of the trace
	 * @throws InterruptedException
	 */
	public static List<SpanData> trace(String traceId) throws InterruptedException {
		assertThat(traceId).as("Trace ID").isNotBlank();
		long end = System.currentTimeMillis() + TIMEOUT_MS;
		while (System.currentTimeMillis() < end) {
			List<SpanData> trace = all().stream().filter(s -> s.getTraceId().equals(traceId)).collect(Collectors.toList());
			if (trace.stream().anyMatch(s -> !s.getParentSpanContext().isValid())) {
				return trace;
			}
			Thread.sleep(100);
		}
		return fail("Root span of trace " + traceId + " was not exported within " + TIMEOUT_MS + " ms, exported spans: " + all());
	}

	/**
	 * Get the root span of the trace and assert that it is a server span and all other spans of the trace are its descendants
	 * @param trace spans of the trace
	 * @return root span
	 */
	public static SpanData assertServerRooted(List<SpanData> trace) {
		List<SpanData> roots = trace.stream().filter(s -> !s.getParentSpanContext().isValid()).collect(Collectors.toList());
		assertThat(roots).as("Root spans of the trace").hasSize(1);
		SpanData root = roots.get(0);
		assertThat(root.getKind()).as("Kind of the root span " + root.getName()).isEqualTo(SpanKind.SERVER);

		Map<String, SpanData> bySpanId = trace.stream().collect(Collectors.toMap(SpanData::getSpanId, Function.identity()));
		for (SpanData span : trace) {
			if (span != root) {
				assertThat(bySpanId).as("Parent of span " + span.getName()).containsKey(span.getParentSpanId());
			}
		}
		return root;
	}

	/**
	 * Get the direct and indirect children of the given span
	 * @param trace spans of the trace
	 * @param parent parent span
	 * @return descendants
	 */
	public static List<SpanData> descendants(List<SpanData> trace, SpanData parent) {
		Set<String> ids = new HashSet<>(Set.of(parent.getSpanId()));
		boolean added = true;
		while (added) {
			added = trace.stream().filter(s -> ids.contains(s.getParentSpanId())).map(SpanData::getSpanId).anyMatch(ids::add);
		}
		return trace.stream().filter(s -> s != parent && ids.contains(s.getSpanId())).collect(Collectors.toList());
	}

	/**
	 * Find a span with the given name in the trace
	 * @param trace spans of the trace
	 * @param name span name
	 * @return optional span
	 */
	public static Optional<SpanData> byName(List<SpanData> trace, String name) {
		return trace.stream().filter(s -> s.getName().equals(name)).findFirst();
	}

	/**
	 * Assert that no span is exported during the given time
	 * @param millis time to wait in milliseconds
	 * @param description description of what was done
	 * @throws InterruptedException
	 */
	public static void assertNothingExported(long millis, String description) throws InterruptedException {
		Thread.sleep(millis);
		assertThat(all()).as("Spans exported for " + description).isEmpty();
	}

	/**
	 * Read the body of the OTLP request, pass it to the consumer and answer with an empty response
	 * @param exchange exchange
	 * @param consumer consumer of the body
	 * @throws IOException
	 */
	private static void receive(HttpExchange exchange, BodyConsumer consumer) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			consumer.accept(in.readAllBytes());
			exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
			exchange.sendResponseHeaders(200, -1);
		} catch (Exception e) {
			e.printStackTrace();
			exchange.sendResponseHeaders(500, -1);
		} finally {
			exchange.close();
		}
	}

	/**
	 * Consumer of an OTLP request body
	 */
	@FunctionalInterface
	private interface BodyConsumer {
		void accept(byte[] body) throws Exception;
	}
}
