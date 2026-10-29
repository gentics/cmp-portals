package com.gentics.mesh.portal.comments;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.core.HandlerProvider;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;

/**
 * Handler for the Mesh comment plugin.
 */
public class CommentsHandler {

	/** The proxy handler that will forward all requests to Mesh. */
	private final Handler<RoutingContext> proxy;

	/**
	 * Default constructor.
	 *
	 * @param config The portal configuration
	 * @param handlerProvider The provider for the proxy handler
	 */
	@Inject
	public CommentsHandler(PortalConfig config, HandlerProvider handlerProvider) {
		this.proxy = handlerProvider.pluginProxy(
			"^/api/comment/",
			"/api/v1/" + config.getMeshProject() + "/plugins/comment/");
	}

	/**
	 * Register the {@link #proxy} handler for all requests to <code>/api/comment/*</code>.
	 *
	 * @param router The router to add the endpoint
	 */
	public void addRoutes(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withPath("/api/comment/*")
			.withOrder(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
			.withHandler(proxy)
			.withDescription("A proxy to the Comment Mesh Plugin ")
			.withExampleResponse(HttpResponseStatus.OK, "Plugin response")
			.build();
	}
}
