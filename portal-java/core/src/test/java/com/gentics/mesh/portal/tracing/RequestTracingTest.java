package com.gentics.mesh.portal.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;

import com.gentics.mesh.portal.api.authentication.PortalUser;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Test cases for {@link RequestTracing}
 */
public class RequestTracingTest {
	@Rule
	public OpenTelemetryRule otel = OpenTelemetryRule.create();

	@Test
	public void testRedactQuery() {
		assertThat(RequestTracing.redactQuery("state=abc&session_state=xyz&iss=http%3A%2F%2Fkeycloak&code=123.456",
				RequestTracing.OAUTH_CALLBACK_PARAMETERS))
			.as("Redacted query")
			.isEqualTo("state=abc&session_state=REDACTED&iss=http%3A%2F%2Fkeycloak&code=REDACTED");
	}

	@Test
	public void testRedactQueryWithoutValue() {
		assertThat(RequestTracing.redactQuery("code&other=1&code=", RequestTracing.OAUTH_CALLBACK_PARAMETERS))
			.as("Redacted query")
			.isEqualTo("code&other=1&code=REDACTED");
	}

	@Test
	public void testRedactEmptyQuery() {
		assertThat(RequestTracing.redactQuery(null, RequestTracing.OAUTH_CALLBACK_PARAMETERS)).as("Redacted null").isNull();
		assertThat(RequestTracing.redactQuery("", RequestTracing.OAUTH_CALLBACK_PARAMETERS)).as("Redacted empty query").isEmpty();
	}

	/**
	 * Test that the handler replaces the query parameters in the span of the request
	 */
	@Test
	public void testRedactQueryParametersHandler() {
		RoutingContext rc = routingContext("session_state=xyz&code=123");

		inRequestSpan(() -> RequestTracing.redactQueryParameters(RequestTracing.OAUTH_CALLBACK_PARAMETERS).handle(rc));

		assertThat(requestSpan().getAttributes().get(RequestTracing.URL_QUERY))
			.as("Query attribute")
			.isEqualTo("session_state=REDACTED&code=REDACTED");
		verify(rc).next();
	}

	@Test
	public void testTraceIdHeader() {
		RoutingContext rc = routingContext(null);

		inRequestSpan(() -> RequestTracing.addTraceIdHeader(rc));

		verify(rc.response()).putHeader(RequestTracing.TRACE_ID_HEADER, requestSpan().getTraceId());
		verify(rc).next();
	}

	@Test
	public void testNoTraceIdHeaderWhenNotSampled() {
		RoutingContext rc = routingContext(null);
		Span notSampled = Span.wrap(SpanContext.create("0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getDefault(),
				TraceState.getDefault()));

		try (Scope scope = notSampled.makeCurrent()) {
			RequestTracing.addTraceIdHeader(rc);
		}

		verify(rc.response(), never()).putHeader(anyString(), anyString());
		verify(rc).next();
	}

	@Test
	public void testNoTraceIdHeaderWithoutSpan() {
		RoutingContext rc = routingContext(null);

		RequestTracing.addTraceIdHeader(rc);

		verify(rc.response(), never()).putHeader(anyString(), anyString());
		verify(rc).next();
	}

	@Test
	public void testUserId() {
		inRequestSpan(() -> RequestTracing.setUserId(user("1b6d4f0e-4c3a-4c55-9c39-6f1f6a1e2b7d")));

		assertThat(requestSpan().getAttributes().get(RequestTracing.USER_ID))
			.as("User ID")
			.isEqualTo("1b6d4f0e-4c3a-4c55-9c39-6f1f6a1e2b7d");
	}

	@Test
	public void testNoUserId() {
		inRequestSpan(() -> {
			RequestTracing.setUserId(null);
			RequestTracing.setUserId(user(" "));
		});

		assertThat(requestSpan().getAttributes().get(RequestTracing.USER_ID)).as("User ID").isNull();
	}

	@Test
	public void testUserIdWithoutSpan() {
		PortalUser user = user("1b6d4f0e-4c3a-4c55-9c39-6f1f6a1e2b7d");

		RequestTracing.setUserId(user);

		verify(user, never()).userId();
	}

	@Test
	public void testRouteHandlerCallsNext() {
		RoutingContext rc = routingContext(null);

		inRequestSpan(() -> RequestTracing.route("/api/test/:id").handle(rc));

		verify(rc).next();
	}

	/**
	 * Run the given action while a request span is current
	 * @param action action
	 */
	private void inRequestSpan(Runnable action) {
		Span span = otel.getOpenTelemetry().getTracer("test").spanBuilder("GET").startSpan();

		try (Scope scope = span.makeCurrent()) {
			action.run();
		} finally {
			span.end();
		}
	}

	/**
	 * Get the single finished request span
	 * @return span data
	 */
	private SpanData requestSpan() {
		assertThat(otel.getSpans()).as("Finished spans").hasSize(1);
		return otel.getSpans().get(0);
	}

	/**
	 * Create a mocked routing context
	 * @param query query string of the request
	 * @return routing context
	 */
	private RoutingContext routingContext(String query) {
		RoutingContext rc = mock(RoutingContext.class);
		HttpServerRequest request = mock(HttpServerRequest.class);
		HttpServerResponse response = mock(HttpServerResponse.class);

		when(rc.request()).thenReturn(request);
		when(rc.response()).thenReturn(response);
		when(request.query()).thenReturn(query);

		return rc;
	}

	/**
	 * Create a mocked user
	 * @param userId user ID
	 * @return user
	 */
	private PortalUser user(String userId) {
		PortalUser user = mock(PortalUser.class);

		when(user.userId()).thenReturn(userId);

		return user;
	}
}
