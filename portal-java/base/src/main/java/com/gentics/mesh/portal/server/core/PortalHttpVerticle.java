package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.portal.DataProviderVerticle;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.PortalVersion;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.api.server.PortalServerVerticle;
import com.gentics.mesh.portal.applications.PortalApplicationHandler;
import com.gentics.mesh.portal.branch.QueryParameterBranchSelector;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.content.ContentLanguageHandler;
import com.gentics.mesh.portal.content.ContentPathHandler;
import com.gentics.mesh.portal.content.NavigationHandler;
import com.gentics.mesh.portal.content.RedirectHandler;
import com.gentics.mesh.portal.content.TemplateHandler;
import com.gentics.mesh.portal.content.preview.ContentPreviewHandler;
import com.gentics.mesh.portal.error.ErrorHandler;
import com.gentics.mesh.portal.gis.CroppingGisHandler;
import com.gentics.mesh.portal.gis.GisHandler;
import com.gentics.mesh.portal.handler.AdditionalHeaderHandler;
import com.gentics.mesh.portal.handler.OpenApiHandler;
import com.gentics.mesh.portal.handler.PoweredByHandler;
import com.gentics.mesh.portal.handler.SecurityHandler;
import com.gentics.mesh.portal.search.SearchHandler;
import com.gentics.mesh.portal.sitemap.SitemapHandler;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Promise;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ResourceBundle;

import static com.gentics.mesh.http.HttpConstants.APPLICATION_JSON;
import static com.gentics.mesh.portal.api.Constants.RC_TEMPLATE_NAME;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.ext.web.handler.TemplateHandler.DEFAULT_CONTENT_TYPE;

/**
 * Central verticle which will setup all needed routes to handle requests.
 */
public class PortalHttpVerticle extends PortalServerVerticle {

	public static final Logger log = LoggerFactory.getLogger(PortalHttpVerticle.class);

	/**
	 * Local router to which all routes are bound.
	 */
	private Router router;

	private final PortalApplicationHandler appHandler;
	private final QueryParameterBranchSelector branchSelectorHandler;
	private final CacheProvider cacheProvider;
	private final CachingHandler cachingHandler;
	private final PortalConfig config;
	private final ContentPathHandler contentHandler;
	private final ContentLanguageHandler contentLanguageHandler;
	private final CroppingGisHandler croppingGisHandler;
	private final DataProviderVerticle dataProvider;
	private final HandlebarsTemplateEngine engine;
	private final ErrorHandler errorHandler;
	private final GisHandler gisHandler;
	private final LoggerHandler loggerHandler;
	private final NavigationHandler navigationHandler;
	private final PoweredByHandler poweredByHandler;
	private final ContentPreviewHandler previewHandler;
	private final RedirectHandler redirectHandler;
	private final SitemapHandler sitemapHandler;
	private final Provider<StaticHandler> staticHandler;
	private final FormGenStaticHandler formgenStaticHandler;
	private final TemplateHandler templateHandler;
	private final AdditionalHeaderHandler additionalHeaderHandler;
	private final CookieProvider cookieProvider;
	private final SecurityHandler securityHandler;
	private final SearchHandler searchHandler;
	private final OpenApiHandler openApiHandler;

	@Inject
	public PortalHttpVerticle(PortalApplicationHandler appHandler, QueryParameterBranchSelector branchSelectorHandler, CacheProvider cacheProvider, CachingHandler cachingHandler, PortalConfig config, ContentPathHandler contentHandler, ContentLanguageHandler contentLanguageHandler, CroppingGisHandler croppingGisHandler, DataProviderVerticle dataProvider, HandlebarsTemplateEngine engine, ErrorHandler errorHandler, GisHandler gisHandler, LoggerHandler loggerHandler, NavigationHandler navigationHandler, PoweredByHandler poweredByHandler, ContentPreviewHandler previewHandler, RedirectHandler redirectHandler, SitemapHandler sitemapHandler, Provider<StaticHandler> staticHandler, FormGenStaticHandler formGenStaticHandler, TemplateHandler templateHandler, AdditionalHeaderHandler additionalHeaderHandler, CookieProvider cookieProvider, SecurityHandler securityHandler, SearchHandler searchHandler, OpenApiHandler openApiHandler) {
		this.appHandler = appHandler;
		this.branchSelectorHandler = branchSelectorHandler;
		this.cacheProvider = cacheProvider;
		this.cachingHandler = cachingHandler;
		this.config = config;
		this.contentHandler = contentHandler;
		this.contentLanguageHandler = contentLanguageHandler;
		this.croppingGisHandler = croppingGisHandler;
		this.dataProvider = dataProvider;
		this.engine = engine;
		this.errorHandler = errorHandler;
		this.gisHandler = gisHandler;
		this.loggerHandler = loggerHandler;
		this.navigationHandler = navigationHandler;
		this.poweredByHandler = poweredByHandler;
		this.previewHandler = previewHandler;
		this.redirectHandler = redirectHandler;
		this.sitemapHandler = sitemapHandler;
		this.staticHandler = staticHandler;
		this.formgenStaticHandler = formGenStaticHandler;
		this.templateHandler = templateHandler;
		this.additionalHeaderHandler = additionalHeaderHandler;
		this.cookieProvider = cookieProvider;
		this.securityHandler = securityHandler;
		this.searchHandler = searchHandler;
		this.openApiHandler = openApiHandler;
	}

	@Override
	public void start(Promise<Void> startPromise) {

		this.router = Router.router(vertx);
		// The error handler will just log the error
		// and the redirect handler will return the error page
		router.route().failureHandler(errorHandler);
		router.route().failureHandler(redirectHandler);
		addRoutes(router);

		int serverPort = config.getServerPort();
		String serverHost = config.getServerHost();
		HttpServerOptions httpServerOptions = new HttpServerOptions()
			.setPort(serverPort)
			.setHost(serverHost)
			// TODO: Re-enable compression once the issue is fixed in Vert.x 5.
			.setCompressionSupported(false)
			.setHandle100ContinueAutomatically(true)
			.setTcpFastOpen(true)
			.setTcpNoDelay(true)
			.setTcpQuickAck(true)
			.setMaxHeaderSize(config.getMaxHttpHeaderSize());

		vertx.createHttpServer(httpServerOptions)
			.requestHandler(router)
			.listen().andThen(event -> {
				if (event.failed()) {
					log.error("FATAL: Could not start HTTP server: {}", event.cause().getMessage(), event.cause());
					startPromise.fail(event.cause());
				} else {
					int port = event.result().actualPort();

					log.info("HTTP server running on {}:{}", serverHost, port);

					deploymentComplete(port);
					startPromise.complete();
				}
			});
	}

	public void addRoutes(Router router) {
		// default routes/handlers - always first
		router.route()
			.handler(TimeoutHandler.create(config.getNetworkTimeout() * 1000L))
			.handler(loggerHandler);
		poweredByHandler.addRoutes(router);
		additionalHeaderHandler.addRoutes(router);
		openApiHandler.addRoutes(router);
		addBodyHandler(router);

		// auth and caching routes/handlers
		// The preview handler works after the authentication routes, but the
		// authentication handlers needs to know, if this is a preview request,
		// so setting the preview mode is done here.
		router.route("/api/preview/*")
			.method(POST)
			.order(RouteOrder.AUTHENTICATION_ROUTES.getOrder())
			.consumes(APPLICATION_JSON)
			.produces(DEFAULT_CONTENT_TYPE)
			.handler(previewHandler::setPreviewMode);

		cacheProvider.addRoutes(router);
		cachingHandler.addRoutes(router);

		// all api routes/handlers - or routes which serve a specific path
		addBuildInfoHandler(router);
		addGisHandler(router);
		addCacheClearHandler(router);
		addPreviewHandler(router);
		sitemapHandler.addRoutes(router);
		searchHandler.addRoutes(router);

		// before content routes/handlers
		addStaticHandlers(router);
		contentLanguageHandler.addRoutes(router);

		// content routes/handlers
		addCookieDisclaimerHandler(router);
		InternalEndpointBuilder.wrap(router)
			.withDisplayName("Main content route")
			.withDescription("Delivers the page or binary with the given path")
			.withPathRegex(Constants.ROUTE_CATCH_ALL)
			.withRAMLPath("/*")
			.withOrder(RouteOrder.CONTENT_ROUTES.getOrder())
			.withHandler(contentHandler)
			.withExampleResponse(HttpResponseStatus.OK, "Requested content, either page, or binary data, or any other resource")
			.build();

		router.routeWithRegex(Constants.ROUTE_CATCH_ALL)
			.order(RouteOrder.AFTER_CONTENT_ROUTES.getOrder())
			.handler(navigationHandler);

		// TODO Maybe this should be done in the BootstrapInitializer, so that the dependency to DataProviderVerticle can be removed.
		dataProvider.addRoutes(router);

		router.routeWithRegex(Constants.ROUTE_CATCH_ALL)
			.order(RouteOrder.AFTER_CONTENT_ROUTES.getOrder())
			.handler(appHandler);
		router.routeWithRegex(Constants.ROUTE_CATCH_ALL)
			.order(RouteOrder.AFTER_CONTENT_ROUTES.getOrder())
			.handler(contentHandler::prepareRender);

		// render routes/handlers
		router.route().order(RouteOrder.RENDERING_ROUTES.getOrder())
			.blockingHandler(templateHandler, false)
			.failureHandler(rc -> log.error(
				"Error while rendering template \"{}\"",
				rc.get(RC_TEMPLATE_NAME),
				rc.failure()))
			.failureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()));
	}

	/**
	 * Add the body handler.
	 *
	 * <p>
	 *     Nothing will be done for websocket requests, and for other requests {@link HttpServerRequest#setExpectMultipart(boolean)}
	 *     is set accordingly.
	 * </p>
	 * @param router The router to add the body handler to
	 */
	private void addBodyHandler(Router router) {
		BodyHandler bodyHandler = BodyHandler.create()
			.setDeleteUploadedFilesOnEnd(config.isAutoDeleteUploads())
			.setUploadsDirectory(config.getUploadDir())
			.setMergeFormAttributes(true);

		router.route().method(POST).order(RouteOrder.BODY_HANDLER.getOrder()).handler(rc -> {
			// Connection upgrade requests never end and therefore the body
			// handler will never pass through to subsequent route handlers.
			if ("websocket".equalsIgnoreCase(rc.request().getHeader(HttpHeaders.UPGRADE))) {
				rc.next();
			} else {
				HttpServerRequest req = rc.request();

				if (req.getHeader(HttpHeaders.CONTENT_TYPE) != null
						&& req.getHeader(HttpHeaders.CONTENT_TYPE).startsWith("multipart/")) {
					req.setExpectMultipart(true);
				}

				bodyHandler.handle(rc);
			}
		});
	}

	/**
	 * Add the cache clear handler.
	 *
	 * <p>
	 *     The handler checks if the request comes from an allowed IP or if the
	 *     correct {@link SecurityOptions#getApiKey() API key} was provided,
	 *     and if so will clear cached values for
	 *     <ul>
	 *         <li>the caching handler</li>
	 *         <li>all caches created by the {@link CacheProvider}</li>
	 *         <li>the content handler</li>
	 *         <li>the templates</li>
	 *         <li>the data provider</li>
	 *         <li>the redirect handler</li>
	 *     </ul>
	 * </p>
	 *
	 * @param router The router to add a cache clear route to
	 */
	private void addCacheClearHandler(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withDisplayName("Clear cache")
			.withDescription("Clears the cache")
			.withPath("/api/clearCache")
			.withOrder(RouteOrder.API_ROUTES.getOrder())
			.withMethod(POST)
			.consumes(APPLICATION_JSON)
			.produces("text/plain")
			.withExampleRequest(new JsonObject().put("key", "CONFIGURED_PORTAL_API_KEY"))
			.withExampleResponse(HttpResponseStatus.OK, "Cache was successfully cleared")
			.withExampleResponse(HttpResponseStatus.FORBIDDEN, "Request denied due to missing/invalid API key")
			.withHandler(securityHandler)
			.withHandler(rc -> {
				try {
					cachingHandler.clear();
					cacheProvider.clearAll();
					contentHandler.clearCache();
					engine.clearCache();
					// Not exactly caches, but this seems a good place to force a refresh.
					dataProvider.refreshNow();
					redirectHandler.refreshNow(rc);
					ResourceBundle.clearCache();
					rc.response().end("Cache clear invoked");
					log.info("Cache clear invoked");
				} catch (Exception e) {
					log.warn("Clearing cache failed: {}", e.getMessage(), e);
					rc.fail(e);
				}
			})
			.build();
	}

	/**
	 * Add necessary handlers for <code>/api/preview</code>.
	 *
	 * @param router The router to add routes to
	 */
	private void addPreviewHandler(Router router) {
		// preview requests may specify the branch with a query parameter
		router.routeWithRegex("/api/preview/(.*)")
			.order(RouteOrder.BEFORE_API_ROUTES.getOrder())
			.method(POST)
			.handler(branchSelectorHandler);

		var exampleRequest = new JsonObject()
			.put("uuid", "b0ae041c824111ed9a480242ac180003")
			.put("language", "en")
			.put("parentNode", new JsonObject()
				.put("uuid",  "f3bfdaab610911edb8080242ac140003")
				.put("schema", new JsonObject()
					.put("name", "project_content")))
			.put("schema", new JsonObject()
				.put("name", "project_content"))
			.put("fields", new JsonObject()
				.put("filename", "welcome")
				.put("cms_id", 1)
				.put("createdate", "2025-12-03T21:26:00Z")
				.put("publishtimestamp", "2025-12-03T21:26:00Z")
				.put("description", "")
				.put("editdate", "2025-12-03T21:26:00Z")
				.put("nice_url", null)
				.put("pagecontent", "<div class=\"welcome-text\">This is a test page</div>")
				.put("editoremail", "nowhere@gentics.com")
				.put("gtx_url", "/en/welcome")
				.put("editor", "Node Admin")
				.put("templateName", "startpage")
				.put("contenttype", "text/html")
				.put("creator", "Node Admin")
				.put("publishermail", "nowhere@gentics.com")
				.put("name", "Welcome")
				.put("alternate_urls", new JsonArray())
				.put("creatoremail", "nowhere@gentics.com")
				.put("publisher", "Node Admin"));

		InternalEndpointBuilder.wrap(router)
			.withDisplayName("Preview")
			.withDescription("Render a preview of the posted page")
			.withPathRegex("/api/preview/(.*)")
			.withRAMLPath("/api/preview/:contentPath")
			.withOrder(RouteOrder.API_ROUTES.getOrder())
			.withUriParameter("contentPath", "Path of the content to preview", "/en/welcome")
			.withMethod(POST)
			.consumes(APPLICATION_JSON)
			.produces(DEFAULT_CONTENT_TYPE)
			.withExampleRequest(exampleRequest)
			.withHandler(previewHandler)
			.withExampleResponse(HttpResponseStatus.OK, "Requested resource for the preview")
			.build();

		// TODO Why is this not also "/api/preview/(.*)"?
		router.routeWithRegex(Constants.ROUTE_CATCH_ALL).order(RouteOrder.API_ROUTES.getOrder()).handler(navigationHandler);

		dataProvider.addPreviewRoutes(router);

		// Finally use the previously set context data to render the preview
		router.routeWithRegex("/api/preview/(.*)")
			.order(RouteOrder.RENDERING_ROUTES.getOrder())
			.method(POST)
			.consumes(APPLICATION_JSON)
			.produces(DEFAULT_CONTENT_TYPE)
			.blockingHandler(templateHandler, false)
			.failureHandler(rc -> log.error(
				"Error while rendering preview template \"{}\"",
				rc.get(RC_TEMPLATE_NAME),
				rc.failure()))
			.failureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()));
	}

	/**
	 * Add version handler which provides information about the portal build.
	 *
	 * @param router The router to add the route to
	 */
	private void addBuildInfoHandler(Router router) {
		JsonObject versionInfo = new JsonObject()
			.put("productName", "Gentics Portal | java")
			.put("productVersion", PortalVersion.plainVersion());

		InternalEndpointBuilder.wrap(router)
			.withDisplayName("Version information")
			.withDescription("Returns the Portal | java version")
			.withPath("/api/version")
			.withOrder(RouteOrder.API_ROUTES.getOrder())
			.produces("application/json")
			.withExampleResponse(HttpResponseStatus.OK, versionInfo, "The version of this portal")
			.withHandler(securityHandler)
			.withHandler(rc -> rc.response().end(versionInfo.encode()))
			.build();
	}

	/**
	 * Add GenticsImageStore Handlers
	 *
	 * @param router
	 *            router
	 */
	private void addGisHandler(Router router) {
		// routes to the gis will be rerouted to the image path, with gis specific data set in the routing context
		InternalEndpointBuilder.wrap(router)
			.withDisplayName("GenticsImageStore Crop & Resize")
			.withDescription("Prepares reroute for serving an image with given resizing and cropping specification")
			.withPathRegex(CroppingGisHandler.ROUTE_REGEX)
			.withRAMLPath("/GenticsImageStore/:width/:height/:mode/:startx/:starty/:cropwidth/:cropheight/:path")
			.withOrder(RouteOrder.BEFORE_API_ROUTES.getOrder())
			.withUriParameter("width", "Width for cropping", "800")
			.withUriParameter("height", "Height for cropping", "600")
			.withUriParameter("mode", "Resize mode (force, smart, prop)", "smart")
			.withUriParameter("startx", "X coordinate for cropping", "0")
			.withUriParameter("starty", "Y coordinate for cropping", "0")
			.withUriParameter("cropwidth", "Width for cropping", "200")
			.withUriParameter("cropheight", "Height for cropping", "150")
			.withUriParameter("path", "Actual path to the image", "/images/example.png")
			.withHandler(croppingGisHandler::reroute)
			.withExampleResponse(HttpResponseStatus.OK, "Modified image")
			.build();

		InternalEndpointBuilder.wrap(router)
			.withDisplayName("GenticsImageStore Resize")
			.withDescription("Prepares reroute for serving an image with given resizing specification")
			.withPathRegex(GisHandler.ROUTE_REGEX)
			.withRAMLPath("/GenticsImageStore/:width/:height/:mode/:path")
			.withOrder(RouteOrder.BEFORE_API_ROUTES.getOrder())
			.withUriParameter("width", "Width for cropping", "800")
			.withUriParameter("height", "Height for cropping", "600")
			.withUriParameter("mode", "Resize mode", "smart")
			.withUriParameter("path", "Actual path to the image", "/images/example.png")
			.withHandler(gisHandler::reroute)
			.withExampleResponse(HttpResponseStatus.OK, "Modified image")
			.build();

		// all routes will be handled by the gis handlers, because they may be rerouted from gis requests
		router.route()
			.order(RouteOrder.API_ROUTES.getOrder())
			.handler(gisHandler)
			.handler(croppingGisHandler)
			.failureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()));
	}

	/**
	 * Add the static files handlers for normal static files and for the FormGen app.
	 *
	 * @param router The router to add the static routes to
	 */
	private void addStaticHandlers(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withDisplayName("Static files")
			.withDescription("Serves static files from the portals \"static\" directory")
			.withPath("/static/*")
			.withRAMLPath("/static/:path")
			.withUriParameter("path", "Path to the static file", "/css/style.css")
			.withOrder(RouteOrder.BEFORE_CONTENT_ROUTES.getOrder())
			.withMethod(GET)
			.withHandler(staticHandler.get())
			.withExampleResponse(HttpResponseStatus.OK, "Requested static resource")
			.build();

		var formgenStaticServePath = config.getFormsOptions().getFormgenStaticServePath();

		formgenStaticServePath = Strings.CI.prependIfMissing(formgenStaticServePath, "/");
		formgenStaticServePath = Strings.CI.appendIfMissing(formgenStaticServePath, "*");

		InternalEndpointBuilder.wrap(router)
			.withDisplayName("FormGen files")
			.withDescription("Serves static files for the FormGen app")
			.withPath(formgenStaticServePath)
			.withRAMLPath("%s:path".formatted(formgenStaticServePath))
			.withUriParameter("path", "Path to the FormGen app", "/index.js")
			.withOrder(RouteOrder.BEFORE_CONTENT_ROUTES.getOrder())
			.withMethod(GET)
			.withHandler(formgenStaticHandler)
			.withExampleResponse(HttpResponseStatus.OK, "Requested FormGen app resource")
			.build();
	}

	/**
	 * Check if the cookie disclaimer was accepted, and store the result in the routing context.
	 *
	 * @param router The router to add the route to
	 */
	private void addCookieDisclaimerHandler(Router router) {
		router.route()
			.order(RouteOrder.CONTENT_ROUTES.getOrder())
			.handler(rc -> {
				Cookie cookie = cookieProvider.get(rc, Constants.COOKIE_DISCLAIMER);
				String accepted = cookie == null ? "false" : cookie.getValue();

				rc.put(Constants.COOKIE_DISCLAIMER, accepted);

				log.trace("Cookie disclaimer accepted: {}", accepted);
				rc.next();
			});
	}

	/**
	 * Return the router to which all routes will be bound.
	 *
	 * @return The router used by the HTTP verticle
	 */
	public Router getRouter() {
		return router;
	}

}
