package com.gentics.mesh.portal.content;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.ext.web.handler.TemplateHandler.DEFAULT_CONTENT_TYPE;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.monitoring.PortalMetric;
import com.gentics.mesh.portal.api.monitoring.PortalTimer;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.tracing.RequestTracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.net.RFC3986;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.reactivex.core.Vertx;

/**
 * Handler for rendering a template with the template engine. The template name must be set with key {@link Constants#RC_TEMPLATE_NAME} in the routing context.
 */
@Singleton
public class TemplateHandler extends AbstractBaseHandler {

	/**
	 * Span attribute containing the name of the rendered template
	 */
	public static final AttributeKey<String> TEMPLATE_NAME = AttributeKey.stringKey("portal.template.name");

	private final HandlebarsTemplateEngine engine;
	private final CachingHandler cachingHandler;
	private final MetricsService metricsService;

	@Inject
	public PortalConfig options;

	@Inject
	public TemplateHandler(HandlebarsTemplateEngine engine, CachingHandler cachingHandler, MetricsService metricsService) {
		this.engine = engine;
		this.cachingHandler = cachingHandler;
		this.metricsService = metricsService;
	}

	@Override
	public void addRoutes(Router router) {
		// TODO move routes here.
		// Currently this is done in the PortalHttpVerticle and there is nothing to do here.
	}

	@Override
	public void handle(RoutingContext rc) {
		String file = rc.get(Constants.RC_TEMPLATE_NAME);
		if (file == null) {
			rc.next();
		} else {
			PortalTimer timer = metricsService.wrapTimer(PortalMetric.RENDER_TEMPLATE).start();
			Span span = RequestTracing.tracer().spanBuilder("Render template")
				.setAttribute(TEMPLATE_NAME, file)
				.startSpan();

			// requests done while rendering (e.g. by helpers) are children of the rendering span
			try (Scope scope = span.makeCurrent()) {
				renderHandler(rc, file).andThen(completed(rc, timer, span, file));
			}
		}
	}

	/**
	 * Create a handler for {@link Vertx#rxExecuteBlocking(Handler, boolean) rxExecuteBlocking} which renders the
	 * data from the routing context.
	 *
	 * @param rc The current routing context
	 * @param filename The filename of the template to render
	 * @return A handler which renders the data of <code>rc</code>
	 */
	private Future<Buffer> renderHandler(RoutingContext rc, String filename) {
		return engine.render(rc.data(),	new File(options.getTemplatesPath(), RFC3986.normalizePath(filename)).getAbsolutePath());
	}

	/**
	 * Create a completion handler for rendering the template.
	 *
	 * <p>
	 *     The created handler will stop the timer, cache the result, and end the response by sending it.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param timer A rendering timer
	 * @param span The span of the rendering, which will be ended
	 * @param file The template file to be rendered
	 * @return A handler for the rendered content, or an exception.
	 */
	private Completable<Buffer> completed(RoutingContext rc, PortalTimer timer, Span span, String file) {
		return new Completable<Buffer>() {

			@Override
			public void complete(Buffer buf, Throwable failure) {
				if (failure != null) {
					timer.stop(String.format("Rendering \"%s\" took {}ns and failed", file));
					span.recordException(failure);
					span.setStatus(StatusCode.ERROR);
					span.end();

					rc.fail(failure);
				} else if (buf != null) {
					timer.stop(String.format("Rendering \"%s\" took {}ns and succeeded", file));
					span.end();

					String content = buf.toString();
					cachingHandler.cache(rc, content);
					rc.response()
						.putHeader(CONTENT_TYPE, String.format("%s; charset=UTF-8", DEFAULT_CONTENT_TYPE))
						.end(content);
				} else {
					// Should not happen, but we have to drop the request anyway
					span.setStatus(StatusCode.ERROR);
					span.end();
					rc.fail(new IllegalStateException("Template rendered to null"));
				}
			}
		};
	}
}
