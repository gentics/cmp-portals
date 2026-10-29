package com.gentics.mesh.portal.handler;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler which adds the <code>X-Powered-By: gentics.com</code> header.
 */
@Singleton
public class PoweredByHandler extends AbstractBaseHandler {

	@Inject
	public PoweredByHandler() {
	}

	@Override
	public void addRoutes(Router router) {
		router.route().handler(this);
	}

	@Override
	public void handle(RoutingContext rc) {
		rc.response().putHeader("X-Powered-By", "gentics.com");
		rc.next();
	}
}
