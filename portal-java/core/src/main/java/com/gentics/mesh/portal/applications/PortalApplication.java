package com.gentics.mesh.portal.applications;


import com.gentics.mesh.portal.api.config.InvalidConfigurationException;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * An interface for handlers providing specialised portal functionality.
 *
 * The handler will be called after a pages data has been loaded from the content repository
 * and authentication has been handled if applicable. The handler should either
 * <ul>
 *   <li>Modify data in the routing context to prepare it for rendering by the default content
 *       path and template handlers.</li>
 *   <li>Provide the complete response itself by calling any form of {@link HttpServerResponse#end()}
 *       (e.g. <code>rc.response().end("<em>Some HTML markup</em>")</code>).</li>
 *   <li>Putting the relative path to a Handlebars template in the routing context with the name
 *       {@link #RC_PARTIAL_NAME}. This template will be rendered below the normal content of a page
 *       (the path is relative to the path of the other portal templates).</li>
 *   <li>Putting an error message in the routing context with the name
 *       {@link #RC_ERROR_NAME} which will be displayed under the normal content of a page.</li>
 * </ul>
 */
public interface PortalApplication extends Handler<RoutingContext> {
	/** Routing context key for the partial template to render the applications content. */
	static final String RC_PARTIAL_NAME = "portalappPartial";
	/** Routing context key for an error message from the portal application. */
	static final String RC_ERROR_NAME = "portalappError";

	/**
	 * Initialises the portal application.
	 *
	 * <p>
	 * The application is responsible for retrieving its configuration (most likely by injection),
	 * and if any mandatory options are missing, it can throw an {@link InvalidConfigurationException}
	 * which will abort the startup of the portal.
	 * </p>
	 *
	 * @return A reference to this portal application
	 * @throws InvalidConfigurationException When a mandatory option is missing or has an
	 *   invalid value
	 */
	PortalApplication init() throws InvalidConfigurationException;

	/**
	 * Get the key for this portal application.
	 *
	 * This key
	 * <ul>
	 * 	<li>is used to identify whether a loaded page needs this application</li>
	 * 	<li>should be used to store data provided by this application in the routing context</li>
	 * </ul>
	 *
	 * @return The key for this portal application
	 */
	String getApplicationKey();
}
