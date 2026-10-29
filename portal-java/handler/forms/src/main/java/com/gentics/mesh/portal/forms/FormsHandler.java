package com.gentics.mesh.portal.forms;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.handler.PortalHandler;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.server.core.HandlerProvider;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.reactivex.core.http.HttpClient;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.stream.Stream;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

/**
 * Handler for the form generator Mesh plugin.
 */
@Singleton
public class FormsHandler implements PortalHandler {

	/**
	 * Name of the cookie the user will get when a form is partially filled.
	 *
	 * <p>
	 *     The cookie value is the UUID of the form data node.
	 * </p>
	 *
	 * <p>
	 *     <strong>NOTE</strong> that the cookie is set by the Mesh plugin and
	 *     <em>not</em> the portal.
	 * </p>
	 */
	public final static String COOKIE_NAME = "forms-data";

	/**
	 * Name of the cookie for already voted on polls.
	 *
	 * <p>
	 *     The cookie value is a colon separated list of UUIDs of form nodes.
	 * </p>
	 *
	 * <p>
	 *     <strong>NOTE</strong> that the cookie is set by the Mesh plugin and
	 *     <em>not</em> the portal.
	 * </p>
	 */
	public final static String COOKIE_NAME_VOTED = "forms-voted";

	/**
	 * Character used to separate the UUIDs in the {@link #COOKIE_NAME_VOTED} cookie.
	 */
	public final static String COOKIE_VOTED_SEPARATOR = ":";

	private final String projectName;
	private final CookieProvider cookieProvider;
	private final Handler<RoutingContext> proxy;

	/**
	 * Generate an instance with the client.
	 *
	 * @param portalConfig Portal configuration
	 * @param engine template engine
	 * @param handlerProvider handler provider
	 * @param httpClient Rest Client
	 */
	@Inject
	protected FormsHandler(
			PortalConfig portalConfig,
			HandlebarsTemplateEngine engine,
			HandlerProvider handlerProvider,
			HttpClient httpClient,
			CookieProvider cookieProvider) {
		this.projectName = portalConfig.getMeshProject();
		this.cookieProvider = cookieProvider;
		this.proxy = handlerProvider.pluginProxy("^/api/forms/", "/api/v1/" + projectName + "/plugins/forms/");

		engine.unwrap().registerHelpers(new FormsHelper(portalConfig, httpClient, engine));
	}

	/**
	 * Add the routes for form generator
	 *
	 * @param router router
	 */
	public void addRoutes(Router router) {
		router.route()
			.order(RouteOrder.API_ROUTES.getOrder())
			.handler(this);

		int order = RouteOrder.BEFORE_BODY_HANDLER.getOrder();

		// methods for the 3rd gen form management
		router.routeWithRegex("/api/forms/form/([^/]*)/(load|validate|save|submit|cancel)")
			.order(order)
			.method(POST)
			.handler(proxy);

		router.routeWithRegex("/api/forms/form/([^/]*)/binary/([^/]*)")
			.order(order)
			.method(GET)
			.handler(proxy);

		router.routeWithRegex("/api/forms/translations/([^/]*)/([A-Za-z]*)")
			.order(order)
			.method(GET)
			.handler(proxy);
	}

	/**
	 * Put the form data UUID in the routing context if available.
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);

		// Get forms-data and forms-voted cookies and add to context. Also disable caching if any cookie is present.
		Stream.of(COOKIE_NAME, COOKIE_NAME_VOTED)
			.map(cookieName -> cookieProvider.get(rc, cookieName))
			.filter(cookie -> cookie != null && StringUtils.isNotBlank(cookie.getValue()))
			.forEach(cookie -> prc.disableCache().put(cookie.getName(), cookie.getValue()));

		rc.next();
	}
}
