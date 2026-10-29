package com.gentics.mesh.portal.api;

import com.gentics.mesh.portal.api.server.CookieProvider;

/**
 * List of flags and fields which are usually used to exchange data via the routing context.
 */
public interface Constants {

	/** Routing context key for the render mode. */
	String RC_RENDER_MODE = "gpj.render.mode";

	/** Routing context key for the rendered node. */
	String RC_NODE_TO_RENDER = "gpj.render.node";

	/** Routing context key for the flag if a rerouting occured. */
	String RC_REROUTE_ACTIVE = "gpj.reroute.active";

	/** Routing context key for the reroute status. */
	String RC_REROUTE_STATUS = "gpj.reroute.status";

	/** Key for the template name stored in the routing context. */
	String RC_TEMPLATE_NAME = "gpj.render.templateName";

	/** Routing context key for the actual language of the loaded page. */
	String RC_LANGUAGE = "gpj.language";

	/** Routing context key for the portal configuration. */
	String RC_CONFIG = "gpj.config";

	/** The name of the language cookie. */
	String COOKIE_LANG = "gpj.language";

	/** The name of the cookie disclaimer cookie. */
	String COOKIE_DISCLAIMER = "gpj.cookieDisclaimer";

	/**
	 * The value for deleted cookies.
	 * @deprecated Use {@link com.gentics.mesh.portal.api.server.CookieProvider#delete(String)} instead
	 */
	@Deprecated
	String DELETED_COOKIE_VALUE = CookieProvider.DELETED_COOKIE_VALUE;

	/** The Mesh API version. */
	String MESH_API_VERSION = "v2";

	// TODO the regex route should not be necessary, test with just Router#route() instead.
	/** The default content route. */
	String ROUTE_CATCH_ALL = "/(.*)";
}
