package com.gentics.mesh.portal.tracing;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.portal.api.authentication.PortalUser;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Helpers for the tracing of requests with the OpenTelemetry Java agent.
 *
 * <p>
 *     The agent creates the server span of every request. These helpers add information, which the agent does not know
 *     (the route of routes registered with a regular expression, the user, the trace ID response header).
 *     Without the agent, all helpers do nothing.
 * </p>
 */
public final class RequestTracing {
	/**
	 * Name of the response header containing the trace ID
	 */
	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	/**
	 * Span attribute for the ID of the authenticated user (OpenTelemetry semantic conventions)
	 */
	public static final AttributeKey<String> USER_ID = AttributeKey.stringKey("user.id");

	/**
	 * Span attribute containing the query string of the request (OpenTelemetry semantic conventions)
	 */
	public static final AttributeKey<String> URL_QUERY = AttributeKey.stringKey("url.query");

	/**
	 * Replacement for the values of redacted query parameters
	 */
	public static final String REDACTED = "REDACTED";

	/**
	 * Query parameters of the OAuth2 callback, which must not be recorded
	 */
	public static final Set<String> OAUTH_CALLBACK_PARAMETERS = Set.of("code", "session_state");

	/**
	 * Name of the tracer for spans created by the portal
	 */
	public static final String INSTRUMENTATION_NAME = "com.gentics.mesh.portal";

	private RequestTracing() {
	}

	/**
	 * Get the tracer for spans created by the portal.
	 *
	 * @return The tracer
	 */
	public static Tracer tracer() {
		return GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
	}

	/**
	 * Create a handler, which sets the given route as {@code http.route} (and in the name) of the server span and
	 * continues with the next handler.
	 *
	 * <p>
	 *     The agent sets the route only for routes registered with a path, so this handler should be added as first
	 *     handler of routes registered with a regular expression. As with the routes set by the agent, the longest
	 *     route of a request wins.
	 * </p>
	 *
	 * @param route The route template, e.g. {@code /api/forms/form/:formUuid/:action}
	 * @return The handler
	 */
	public static Handler<RoutingContext> route(String route) {
		return rc -> {
			HttpServerRoute.update(Context.current(), HttpServerRouteSource.NESTED_CONTROLLER, route);
			rc.next();
		};
	}

	/**
	 * Add the ID of the trace as response header {@link #TRACE_ID_HEADER}, if the request is sampled. Then continue
	 * with the next handler.
	 *
	 * @param rc The routing context
	 */
	public static void addTraceIdHeader(RoutingContext rc) {
		SpanContext spanContext = Span.current().getSpanContext();

		if (spanContext.isValid() && spanContext.isSampled()) {
			rc.response().putHeader(TRACE_ID_HEADER, spanContext.getTraceId());
		}

		rc.next();
	}

	/**
	 * Add the ID of the given user to the current span (the span of the request), if the span is recorded.
	 *
	 * @param user The user, may be null
	 */
	public static void setUserId(PortalUser user) {
		Span span = Span.current();

		if (user == null || !span.isRecording()) {
			return;
		}

		String userId = user.userId();

		if (StringUtils.isNotBlank(userId)) {
			span.setAttribute(USER_ID, userId);
		}
	}

	/**
	 * Create a handler, which replaces the values of the given query parameters in the {@code url.query} attribute of
	 * the current span (the span of the request) with {@link #REDACTED} and continues with the next handler.
	 *
	 * @param names The names of the query parameters to redact
	 * @return The handler
	 */
	public static Handler<RoutingContext> redactQueryParameters(Collection<String> names) {
		return rc -> {
			Span span = Span.current();
			String query = rc.request().query();

			if (span.isRecording() && query != null) {
				span.setAttribute(URL_QUERY, redactQuery(query, names));
			}

			rc.next();
		};
	}

	/**
	 * Replace the values of the given parameters in the query string with {@link #REDACTED}.
	 *
	 * @param query The query string (without the leading {@code ?}), may be null
	 * @param names The names of the query parameters to redact
	 * @return The query string with redacted values
	 */
	public static String redactQuery(String query, Collection<String> names) {
		if (StringUtils.isEmpty(query)) {
			return query;
		}

		return Arrays.stream(query.split("&", -1))
			.map(parameter -> {
				int index = parameter.indexOf('=');

				return index >= 0 && names.contains(parameter.substring(0, index))
					? parameter.substring(0, index + 1) + REDACTED
					: parameter;
			})
			.collect(Collectors.joining("&"));
	}
}
