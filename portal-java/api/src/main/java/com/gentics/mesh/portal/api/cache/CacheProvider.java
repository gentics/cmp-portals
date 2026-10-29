package com.gentics.mesh.portal.api.cache;

import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.portal.api.RouteOrder;

import com.gentics.mesh.rest.client.EventbusEvent;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Provides a unified way to create caches used in the portal.
 *
 * <p>
 * Caches should be aware of different user permissions, so an
 * <code>CacheProvider</code> implementation should take appropriate actions in
 * its {@link Handler#handle(Object) handle} method, which is called with the
 * {@link RouteOrder#CACHING_ROUTES} order by default.
 * </p>
 */
public interface CacheProvider extends Handler<RoutingContext> {

	/**
	 * By default the handler is called for all routes with the
	 * {@link RouteOrder#CACHING_ROUTES} order, which is after authentication,
	 * but before API routes.
	 *
	 * @param router The router to setup the routes
	 */
	default void addRoutes(Router router) {
		router.route()
			.order(RouteOrder.CACHING_ROUTES.getOrder())
			.handler(this);
	}

	/**
	 * Clear all created caches.
	 */
	void clearAll();

	/**
	 * Clear the cache with the given namespace.
	 *
	 * @param namespace The cache to clear
	 */
	void clear(String namespace);

	/**
	 * Indicates whether caching is enabled.
	 *
	 * @return <code>true</code> when caching is enabled, and <code>false</code> otherwise
	 */
	boolean enabled();

	/**
	 * Start building a new cache.
	 *
	 * @return A <code>CacheBuilder</code> to build a new cache
	 */
	<T> CacheBuilder<T> cacheBuilder();

	/**
	 * The default clear events for caches:
	 * <ul>
	 *     <li>{@link MeshEvent#NODE_UPDATED}</li>
	 * 	   <li>{@link MeshEvent#NODE_CREATED}</li>
	 *     <li>{@link MeshEvent#NODE_DELETED}</li>
	 *     <li>{@link MeshEvent#SCHEMA_UPDATED}</li>
	 * </ul>
	 *
	 * @return The default clear events for caches
	 */
	MeshEvent[] defaultClearEvents();

	/**
	 * The default event filter for caches.
	 *
	 * <p>
	 *     Only events where the schema name is one of <code>*_content</code>, <code>*_binaryContent</code>, or
	 *     <code>*_folder</code> will match this filter.
	 * </p>
	 *
	 * @param event The event to apply the filter to
	 * @return The default event filter for caches
	 */
	boolean defaultEventFilter(EventbusEvent event);
}
