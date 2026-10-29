package com.gentics.mesh.portal.api;

/**
 * Predefined {@link io.vertx.ext.web.Route#order(int) route orders} for
 * {@link com.gentics.mesh.portal.api.handler.PortalHandler PortalHandlers}.
 *
 * <p>
 *     <strong>Note</strong> that any router order below {@link #API_ROUTES}
 *     are for internal use only, and should not be used by custom handlers
 *     (with the only exception being handlers which forward requests to
 *     Mesh plugins using the proxy from the {@code HandlerProvider}.
 * </p>
 */
public enum RouteOrder {
	/**
	 * Route before the body handler was called, only intended for handlers
	 * which proxy requests.
	 */
	BEFORE_BODY_HANDLER(10),
	/** Route for the body handler only. */
	BODY_HANDLER(20),
	/** Route for authentication related actions. */
	AUTHENTICATION_ROUTES(30),
	/** Route before any cache lookups. */
	BEFORE_CACHING_ROUTES(35),
	/** Route for caching related actions. */
	CACHING_ROUTES(40),
	/** Last internal setup actions. */
	BEFORE_API_ROUTES(50),
	/** Main custom handler route order for actions before loading content. */
	API_ROUTES(60),
	/** Last internal actions before loading content. */
	BEFORE_CONTENT_ROUTES(70),
	/** Routes for loading content from Mesh. */
	CONTENT_ROUTES(80),
	/** Routes for postprocessing content loaded from Mesh. */
	AFTER_CONTENT_ROUTES(90),
	/** Route for the render handler only. */
	RENDERING_ROUTES(100);

	private final int order;

	RouteOrder (int order) {
		this.order = order;
	}

	public int getOrder() {
		return order;
	}
}
