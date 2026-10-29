package com.gentics.mesh.portal.api.authentication;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler for the redirect after a successful login.
 *
 * <p>
 *     This handler is responsible for using the value of the
 *     {@code AUTH_REDIRECT} cookie.
 * </p>
 *
 * <p>
 *     Implementations of this interface should extend
 *     {@code AbstractLoginRedirectHandler}.
 * </p>
 */
public interface LoginRedirectHandler extends Handler<RoutingContext> {

	/**
	 * Add the route for the configured login redirect path.
	 *
	 * @param router The router to add the route to
	 */
	void addRoutes(Router router);
}
