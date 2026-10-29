package com.gentics.mesh.portal.likes;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.core.HandlerProvider;

import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.Router;

/**
 * Handler for the likes plugin
 */
@Singleton
public class LikesHandler {

	private static final Logger log = LoggerFactory.getLogger(LikesHandler.class);

	private String projectName;

	private HandlerProvider handlerProvider;

	/**
	 * Generate an instance with the client.
	 *
	 * @param portalConfig
	 *            Portal configuration
	 * @param meshClient
	 *            Rest Client
	 * @param engine
	 *            template engine
	 * @param handlerProvider
	 *            handler provider
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	@Inject
	protected LikesHandler(PortalConfig portalConfig, HandlerProvider handlerProvider) {
		this.projectName = portalConfig.getMeshProject();
		this.handlerProvider = handlerProvider;
	}

	/**
	 * Add the routes for form generator
	 *
	 * @param router
	 *            router
	 */
	public void addRoutes(Router router) {
		// forward calls to the like plugin
		InternalEndpointBuilder.wrap(router)
			.withPathRegex("/api/like/(.*)")
			.withOrder(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
			.withHandler(handlerProvider.pluginProxy("^/api/like/", "/api/v1/" + projectName + "/plugins/like/"))
			.withExampleResponse(HttpResponseStatus.OK, "Plugin response")
			.withDescription("A proxy to Gentics Mesh Like plugin")
			.build();
	}
}
