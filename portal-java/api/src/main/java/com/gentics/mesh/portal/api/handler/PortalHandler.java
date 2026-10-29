package com.gentics.mesh.portal.api.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Basic interface for all portal handlers.
 */
public interface PortalHandler extends Handler<RoutingContext> {

	/**
	 * Add the handlers routes to the given router.
	 *
	 * @param router The portals router
	 */
	void addRoutes(Router router);
}
