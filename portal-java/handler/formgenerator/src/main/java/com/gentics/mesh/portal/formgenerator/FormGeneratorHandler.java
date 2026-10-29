package com.gentics.mesh.portal.formgenerator;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;
import static com.gentics.mesh.util.URIUtils.encodeSegment;
import static io.vertx.core.http.HttpMethod.POST;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.formgenerator.helper.FormHelper;
import com.gentics.mesh.portal.server.core.HandlerProvider;

import io.reactivex.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;

/**
 * Handler for the form generator
 */
@Singleton
public class FormGeneratorHandler {

	/**
	 * Name of the cookie, the user will get when a form is partially filled (the cookie value is the uuid of the form data node)
	 */
	public final static String COOKIE_NAME = "fg-data";

	private final HandlebarsTemplateEngine engine;

	private final String meshApiKey;
	private final String projectName;

	private final HandlerProvider handlerProvider;

	private final HttpClient httpClient;

	/**
	 * Generate an instance with the client.
	 *
	 * @param portalConfig
	 *            Portal configuration
	 * @param engine
	 *            template engine
	 * @param handlerProvider
	 *            handler provider
	 * @param httpClient
	 *            Rest Client
	 * @throws URISyntaxException
	 * @throws IOException
	 */
	@Inject
	protected FormGeneratorHandler(PortalConfig portalConfig, HandlebarsTemplateEngine engine, HandlerProvider handlerProvider, HttpClient httpClient) {
		this.engine = engine;
		this.meshApiKey = portalConfig.getMeshOptions().getApiKey();
		this.projectName = portalConfig.getMeshProject();
		this.httpClient = httpClient;
		this.engine.unwrap().registerHelpers(new FormHelper(this));
		this.handlerProvider = handlerProvider;
	}

	/**
	 * Add the routes for form generator
	 *
	 * @param router
	 *            router
	 * @param apiKey
	 *            portal API key
	 */
	public void addRoutes(Router router, String apiKey) {
		router.route().order(RouteOrder.API_ROUTES.getOrder()).handler(rc -> {
			Cookie cookie = rc.request().getCookie(COOKIE_NAME);
			// get fg-data cookie and add to context, if any
			if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
				rc.put(COOKIE_NAME, cookie.getValue());
				// omit caching of response
				wrap(rc).disableCache();
			}
			rc.next();
		});

		// add handler to post data for an existing form
		router.routeWithRegex("/api/fg/forms/([^/]*)/data").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).method(POST).handler(
			handlerProvider.pluginProxy("^/api/fg/", "/api/v1/" + projectName + "/plugins/fg/")
		);

		router.routeWithRegex("/api/fg/forms/([^/]*)/jsondata").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).method(POST).handler(
			handlerProvider.pluginProxy("^/api/fg/", "/api/v1/" + projectName + "/plugins/fg/")
		);

		// add handler to update posted data for an existing form
		router.routeWithRegex("/api/fg/forms/([^/]*)/data/([^/]*)").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).method(POST).handler(
			handlerProvider.pluginProxy("^/api/fg/", "/api/v1/" + projectName + "/plugins/fg/")
		);

		router.routeWithRegex("/api/fg/forms/([^/]*)/jsondata/([^/]*)").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).method(POST).handler(
			handlerProvider.pluginProxy("^/api/fg/", "/api/v1/" + projectName + "/plugins/fg/")
		);

		// add handler to get form elements
		router.routeWithRegex("/api/fg/forms/([^/]*)/elements").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).method(HttpMethod.GET).handler(
			handlerProvider.pluginProxy("^/api/fg/", "/api/v1/" + projectName + "/plugins/fg/")
		);

		router.routeWithRegex("/api/fg/forms/([^/]*)/data/([^/]*)/binary/[^/]*").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).method(HttpMethod.GET).handler(
			handlerProvider.pluginProxy("^/api/fg/", "/api/v1/" + projectName + "/plugins/fg/")
		);
	}

	private Single<String> getString(String url) {
		RequestOptions rqOpt = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI(url)
			.putHeader(HttpHeaders.AUTHORIZATION.toString(), "Bearer " + meshApiKey)
			.putHeader(HttpHeaders.ACCEPT.toString(), "text/html");

		return httpClient.rxRequest(rqOpt)
			.flatMap(HttpClientRequest::rxSend)
			.toObservable()
			.flatMap(HttpClientResponse::toObservable)
			.reduce(Buffer::appendBuffer)
			.toSingle(Buffer.buffer())
			.map(Buffer::toString);

	}

	/**
	 * Let the formgenerator plugin render a form
	 * @param uuid form uuid
	 * @return rendered form
	 */
	public Single<String> render(String uuid) {
		return getString("/api/v1/" + encodeSegment(projectName) + "/plugins/fg/forms/" + uuid);
	}

	/**
	 * Let the formgenerator plugin render a form with data
	 * @param formUuid form uuid
	 * @param formDataUuid form data uuid
	 * @return rendered form
	 */
	public Single<String> render(String formUuid, String formDataUuid) {
		return getString("/api/v1/" + encodeSegment(projectName) + "/plugins/fg/forms/" + formUuid + "/data/" + formDataUuid);
	}
}
