package com.gentics.mesh.portal.plugins;

import com.gentics.mesh.portal.api.RouteOrder;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Interface for handlers which forwards requests to Mesh plugins.
 */
public interface PluginHandler {

	/**
	 * Get the proxy that will forward requests to Mesh.
	 * @return The proxy that will forward requests to Mesh
	 */
	Handler<RoutingContext> proxy();

	/**
	 * Get the name of the plugin.
	 *
	 * @return The name of the plugin
	 */
	String name();

	/**
	 * Add routes which should be forwarded.
	 *
	 * <p>
	 *     The default implementation will only add the route <code>/api/PLUGIN_NAME/*</code>.
	 * </p>
	 *
	 * @param router The router to register the plugin routes
	 */
	default void addRoutes(Router router) {
		router.route(String.format("/api/%s/*", name()))
			.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
			.handler(proxy());
	}
}
