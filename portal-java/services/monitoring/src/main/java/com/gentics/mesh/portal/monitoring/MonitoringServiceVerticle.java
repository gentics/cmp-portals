package com.gentics.mesh.portal.monitoring;

import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.api.server.PortalServerVerticle;
import com.gentics.mesh.portal.api.server.PortalState;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MonitoringOptions;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.monitoring.MonitoringRestClient;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.PrometheusScrapingHandler;

import javax.inject.Inject;

/**
 * Verticle exposing monitoring endpoints.
 */
public class MonitoringServiceVerticle extends PortalServerVerticle {

	private static final Logger log = LoggerFactory.getLogger(MonitoringServiceVerticle.class);

	private final MonitoringRestClient monitoringClient;
	private final MonitoringOptions options;
	private final PortalInfo info;

	@Inject
	public MonitoringServiceVerticle(PortalConfig config, PortalInfo info, MonitoringRestClient monitoringClient) {
		this.options = config.getMonitoringOptions();
		this.info = info;
		this.monitoringClient = monitoringClient;
	}

	/**
	 * Configures and starts the HTTP server and routes for monitoring.
	 *
	 * <p>
	 * The future will be completed once the HTTP server is {@link HttpServer#listen(Handler) listening}.
	 * </p>
	 *
	 * @param startPromise
	 *            A Promise which will be completed once the HTTP server is running
	 */
	@Override
	public void start(Promise<Void> startPromise) {
		if (!options.isEnabled()) {
			log.info("Monitoring is disabled in configuration");

			startPromise.complete();

			return;
		}

		int port = options.getPort();
		String host = options.getHost();
		HttpServerOptions httpServerOptions = new HttpServerOptions()
			.setPort(port)
			.setHost(host)
			.setCompressionSupported(false)
			.setHandle100ContinueAutomatically(true);
		Router router = Router.router(vertx);

		addRoutes(router);

		vertx.createHttpServer(httpServerOptions)
			.requestHandler(router)
			.listen()
			.andThen(startupHandler(startPromise));
	}

	/**
	 * Create a {@link HttpServer#listen(Handler) listening handler} which completes the given <code>Future</code>.
	 *
	 * <p>
	 * When the <code>AsyncResult</code> is successful, the handler will publish the event that the verticle has been deployed and complete the given
	 * <code>Future</code>.
	 * </p>
	 *
	 * @param promise
	 *            The Promise to complete when the handler is called
	 * @return A listening handler
	 */
	private Handler<AsyncResult<HttpServer>> startupHandler(Promise<Void> promise) {
		return event -> {
			if (event.failed()) {
				promise.fail(event.cause());
			} else {
				int port = event.result().actualPort();

				if (log.isInfoEnabled()) {
					log.info("Monitoring server running on {}:{}", options.getHost(), port);
				}

				deploymentComplete(port);
				promise.complete();
			}
		};
	}

	/**
	 * Add monitoring and failure routes.
	 *
	 * @param router
	 *            The router to use
	 */
	private void addRoutes(Router router) {
		addHealth(router);
		addMetrics(router);

		router.route().last().handler(this::notFoundHandler);
		router.route().failureHandler(this::failureHandler);
	}

	/**
	 * Add handlers that reacts to health status requests.
	 *
	 * @param router
	 *            The router to use
	 */
	private void addHealth(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withRoute("/health/live")
			.withMethod(HttpMethod.GET)
			.withHandler(this::checkLiveStatus)
			.withFailureHandler(this::failureHandler)
			.withDescription("Liveness check")
			.withExampleResponse(HttpResponseStatus.OK, "Portal is alive")
			.withExampleResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, "Portal is not healthy")
			.build();

		InternalEndpointBuilder.wrap(router)
			.withRoute("/health/ready")
			.withMethod(HttpMethod.GET)
			.withHandler(this::checkReadyStatus)
			.withFailureHandler(this::failureHandler)
			.withDescription("Readiness check")
			.withExampleResponse(HttpResponseStatus.OK, "Portal is ready to serve")
			.withExampleResponse(HttpResponseStatus.SERVICE_UNAVAILABLE, "Portal is not ready")
			.build();
	}

	/**
	 * Add handlers for the metrics endpoint.
	 *
	 * @param router
	 *            The router to use
	 */
	private void addMetrics(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withRoute("/metrics")
			.withMethod(HttpMethod.GET)
			.withHandler(PrometheusScrapingHandler.create(MetricsService.REGISTRY_NAME))
			.withDescription("Metrics")
			.hidden(true)
			.build();
	}

	/**
	 * Check if the portal is still alive.
	 *
	 * <p>
	 *     When {@link PortalInfo#getState()} is
	 *     {@link PortalState#NETWORK_ERROR}, the portal is no longer alive,
	 *     and this method will end the request with
	 *     <em>503 Service Unavailable</em>. Otherwise it will end the request
	 *     with <em>200 OK</em>.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	private void checkLiveStatus(RoutingContext rc) {
		PortalState state = info.getState();

		if (state == PortalState.STARTING || state == PortalState.READY) {
			rc.response()
				.setStatusCode(HttpResponseStatus.OK.code())
				.end();
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Portal is no longer alive: {}", state);
			}

			rc.response()
				.setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
				.end();
		}
	}

	/**
	 * Check if the portal and Mesh are ready.
	 *
	 * <p>
	 *     When {@link PortalInfo#ready()} does <em>not</em> return
	 *     {@code READY}, the <em>503 Service Unavailable</em> response is sent
	 *     immediately.
	 * </p>
	 *
	 * <p>
	 *     Otherwise the Mesh ready status is queried and returned.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	private void checkReadyStatus(RoutingContext rc) {
		if (!info.ready()) {
			if (log.isDebugEnabled()) {
				log.debug("Portal is not ready: {}", info.getState());
			}

			rc.response()
				.setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
				.end();
		} else if (options.isIgnoreMeshReady()) {
			rc.response()
				.setStatusCode(HttpResponseStatus.OK.code())
				.end();
		} else {
			monitoringClient.ready().getResponse()
				.map(MeshResponse::getStatusCode)
				.doOnSuccess(status -> log.debug("Mesh ready status: {}", status))
				.subscribe(
					status -> rc.response().setStatusCode(status).end(),
					error -> {
                        log.trace("Could not get Mesh ready status: {}", error.getMessage(), error);

						rc.response()
							.setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
							.end();
					});
		}
	}

	/**
	 * Send a 404 Not found response.
	 *
	 * @param rc
	 *            The current routing context
	 */
	private void notFoundHandler(RoutingContext rc) {
		rc.response()
			.setStatusCode(HttpResponseStatus.NOT_FOUND.code())
			.end();
	}

	/**
	 * Log an error and send a 500 Internal server error response.
	 *
	 * @param rc
	 *            The current routing context
	 */
	private void failureHandler(RoutingContext rc) {
		if (!rc.failed()) {
			return;
		}

		HttpServerRequest request = rc.request();

		log.error(
			"Could not handle request {} {}: {}",
			request.method().name(),
			request.path(),
			rc.failure().getMessage(),
			rc.failure());

		HttpServerResponse response = rc.response();

		if (!response.closed()) {
			response
				.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
				.end();
		}
	}
}
