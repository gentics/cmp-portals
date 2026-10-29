package com.gentics.mesh.portal.content;

import com.gentics.mesh.core.rest.error.PermissionException;
import com.gentics.mesh.core.rest.graphql.GraphQLError;
import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.parameter.VersioningParameters;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.monitoring.PortalMetric;
import com.gentics.mesh.portal.api.monitoring.PortalTimer;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.ReflectionUtils;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.google.common.net.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.MimeMapping;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Load pages and binary files via path.
 */
@Singleton
public class ContentPathHandler extends AbstractBaseHandler implements Handler<RoutingContext> {

	public enum RenderMode {
		Page, Folder, Raw
	}

	private static final String FIELD_BINARYCONTENT = "binarycontent";
	private static final String RC_CONTENT_TYPE = "content_type";

	protected static final Logger log = LoggerFactory.getLogger(ContentPathHandler.class);

	private final String projectName;
	private final PortalCache<Map<String, String>> startPageLanguages;
	private final MetricsService metricsService;
	private final CachingHandler cachingHandler;
	private final PortalInfo portalInfo;

	protected final MeshRestClientProvider meshClientProvider;
	protected final QueryStore queryStore;
	protected final PortalConfig config;
	protected final int breadcrumbsLevel;

	@Inject
	public ContentPathHandler(
			PortalConfig config,
			MeshRestClientProvider meshClientProvider,
			QueryStore queryStore,
			CacheProvider cacheProvider,
			CachingHandler cachingHandler,
			PortalInfo portalInfo,
			MetricsService metricsService) {
		this.config = config;
		this.projectName = config.getMeshProject();
		this.meshClientProvider = meshClientProvider;
		this.queryStore = queryStore;
		this.breadcrumbsLevel = config.getNavigationOptions().getBreadcrumbsLevel();
		this.cachingHandler = cachingHandler;
		this.portalInfo = portalInfo;

		// Show the information only once and not for every subclass of the ContentPathHandler.
		if (log.isDebugEnabled() && !ReflectionUtils.isSubclassInstance(this, ContentPathHandler.class)) {
			if (breadcrumbsLevel <= 0) {
				log.info("Breadcrumb level will not be restricted");
			} else {
				log.info("Maximum breadcrumb level is {}", breadcrumbsLevel);
			}
		}

		this.startPageLanguages = cacheProvider.<Map<String, String>>cacheBuilder()
			.expireMode(ExpireMode.Write)
			.keyMapper(ignored -> "startPageLanguages")
			.clearEvents(cacheProvider.defaultClearEvents())
			.eventFilter(cacheProvider::defaultEventFilter)
			.build();

		this.metricsService = metricsService;
	}

	@Override
	public void addRoutes(Router router) {
		// TODO move routes here.
		// Currently this is done in the PortalHttpVerticle and there is nothing to do here.
	}

	/**
	 * Process the GraphQL response for a loaded page.
	 *
	 * @param rc The current routing context
	 * @param path The path of the loaded page
	 * @param data The data from the GraphQL response
	 */
	protected void processPageData(RoutingContext rc, String path, JsonObject data) {
		if (data == null) {
			log.debug("No data received for path {}", path);
			rc.fail(HttpResponseStatus.NOT_FOUND.code());

			return;
		}

		if (log.isTraceEnabled()) {
			log.trace("Processing loaded data:\n{}", data.encodePrettily());
		}

		JsonObject node = data.getJsonObject("node");

		if (node == null) {
			PortalRoutingContext prc = wrap(rc);
			if (prc.hasMissingPerm()) {
				// data is missing due to missing permissions
				// proceed with handling (AuthenticationHandler will take care of this)
				rc.next();
			} else {
				int rerouteStatusCode = prc.getRerouteStatusCode();
				if (rerouteStatusCode > 0) {
					log.debug(
						"Rerouting status {} to {} caused {}, sending plain response with {}",
						rerouteStatusCode,
						path,
						HttpResponseStatus.NOT_FOUND.code(),
						rerouteStatusCode);
					rc.response().setStatusCode(rerouteStatusCode).end();
				} else {
					log.debug("Could not find node for path {}", path);
					rc.fail(HttpResponseStatus.NOT_FOUND.code());
				}
			}

			return;
		}

		rc.put("data", data);

		String schemaName = node.getJsonObject("schema").getString("name");

		if (config.getFolderSchema().equals(schemaName)) {
			String startPage = JsonUtils.getValue(node, "fields.startpage.path", null);

			if (startPage == null) {
				startPage = JsonUtils.getValue(node, "fields.startpageurl", null);
			}

			if (startPage != null) {
				log.debug("Rerouting to start page {} of requested folder {}", startPage, path);
				rc.reroute(startPage);

				return;
			}

			rc.put(Constants.RC_NODE_TO_RENDER, node);
			rc.put(Constants.RC_RENDER_MODE, RenderMode.Folder);
		} else if (config.getContentSchema().equals(schemaName)) {
			JsonObject fields = node.getJsonObject("fields");
			String contentType = fields.getString("contenttype");

			if ("text/html".equals(contentType)) {
				rc.put(Constants.RC_NODE_TO_RENDER, node);
				rc.put(Constants.RC_RENDER_MODE, RenderMode.Page);
			} else {
				rc.put(Constants.RC_NODE_TO_RENDER, node);
				rc.put(Constants.RC_RENDER_MODE, RenderMode.Raw);
				rc.put(RC_CONTENT_TYPE, contentType);
			}
		} else if (config.getBinaryContentSchema().equals(schemaName)) {
			handleFiles(path, rc);

			return;
		} else {
			log.error("No index for type \"{}\" has been defined", schemaName);
			rc.fail(HttpResponseStatus.NOT_FOUND.code());

			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Loaded node with schema: {}", schemaName);
			log.debug("Processing request with render mode {}", (RenderMode) rc.get(Constants.RC_RENDER_MODE));
		}

		rc.next();
	}

	/**
	 * Load a page via GraphQL.
	 *
	 * @param path The path of the page to load
	 * @param rc The current routing context
	 */
	protected void handlePage(String path, PortalRoutingContext rc) {
		String folderPath = path;
		int lastSegmentPos = path.lastIndexOf('/');

		if (lastSegmentPos > 0) {
			folderPath = path.substring(0, lastSegmentPos);
		}

		log.debug("Loading node data for path {} (folder: {})", path, folderPath);

		rc.enableNavigation();

		loadByPath(path, rc, false)
			.map(GraphQLResponse::getData)
			.to(RxUtils.observable())
			.subscribe(data -> processPageData(rc, path, data), rc::fail);
	}

	/**
	 * Invoke the graphql query using the given path information.
	 *
	 * @param path The path of the node to load (page or folder)
	 * @param rc routing context
	 * @param forPreview true to force usage of admin client
	 * @return A {@code Single} containing the loaded data
	 */
	protected Single<GraphQLResponse> loadByPath(String path, RoutingContext rc, boolean forPreview) {
		String fixedPath =  (path != null && !path.startsWith("/")) ? "/" + path : path;

		PortalRoutingContext prc = wrap(rc);
		MeshRestClient client = forPreview ? meshClientProvider.get() : meshClientProvider.get(prc);
		GraphQLRequest request = new GraphQLRequest()
			.setQuery(queryStore.getByPathQuery())
			.setVariables(new JsonObject()
				.put("path", fixedPath)
				.put("language", prc.getLanguage())
				.put("withChildren", !StringUtils.isEmpty(config.getFolderTemplateName())));

		PortalTimer timer = metricsService.wrapTimer(PortalMetric.MESH_LOAD_CONTENT);

		return client
			.graphql(projectName, request, prc.getVersion())
			.toSingle()
			.observeOn(RxUtils.scheduler(rc))
			.doOnSubscribe(ignored -> timer.start())
			.doFinally(() -> timer.stop(() -> String.format("GraphQL request for %s took {}ns", fixedPath)))
			.doOnError(portalInfo::checkState)
			.doOnError(RxUtils.onErrorRethrowCode(prc, log, "loadByPath"))
			.map(errorLogger)
			.doOnSuccess(r -> checkPermissions(r, prc));
	}

	/**
	 * Check the GraphQL response for permission errors and store the information in the routing context.
	 *
	 * @param response The GraphQL response to check
	 * @param prc The current routing context
	 */
	private void checkPermissions(GraphQLResponse response, PortalRoutingContext prc) {
		List<String> missingPerm;

		// check whether at least one permission error occurred, and set this info into the rc
		if (response.getErrors() == null) {
			missingPerm = Collections.emptyList();
		} else {
			missingPerm = response.getErrors().stream()
				.filter(error -> PermissionException.TYPE.equals(error.getType()))
				.map(GraphQLError::getElementId)
				.collect(Collectors.toList());
		}

		prc.setMissingPerm(missingPerm);
	}

	/**
	 * Deliver binary content with the specified <code>path</code> via webroot,
	 * or webroot field requests from Mesh.
	 *
	 * @param path The path of the file
	 * @param rc The current routing context
	 */
	private void handleFiles(String path, RoutingContext rc) {
		// Resolve image path using the webroot API.
		VersioningParameters version = wrap(rc).getVersion();
		Map<String, String> forwardedHeaders = HandlerUtils.toMapExcluding(rc.request().headers(), true, HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.HOST);
		PortalTimer timer = metricsService.wrapTimer(PortalMetric.MESH_LOAD_BINARY);
		java.util.function.Consumer<String> eTagHandler = eTag -> {
			if (cachingHandler != null) {
				cachingHandler.cacheETag(rc, eTag);
			}
		};
		MeshRequest<?> request;

		if (config.getMeshOptions().isWebrootFieldAvailable()) {
			log.debug("Loading file {} via webroot field request", path);
			request = meshClientProvider.get(rc).webrootField(projectName, FIELD_BINARYCONTENT, "/" + path, version);
		} else {
			log.debug("Loading file {} via webroot request", path);
			request = meshClientProvider.get(rc).webroot(projectName, "/" + path, version);
		}

		// Pass request headers to the webroot request (e.g. for HTTP range requests). We explicitly do not forward the Authorization header,
		// cookies, and host because this has to be set by the Mesh REST client itself.
		request.setHeaders(forwardedHeaders);

		if (log.isTraceEnabled()) {
			log.trace("Sending webroot request for {} in branch {}, headers:\n{}",
				path,
				version.getBranch(),
				forwardedHeaders.entrySet().stream().map(e -> "  " + e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")));
		}


		request.getResponse()
			.doOnSubscribe(ignored -> timer.start())
			.observeOn(RxUtils.scheduler(rc))
			.doOnError(ignored -> timer.stop())
			.subscribe(
				HandlerUtils.fileDownloadPipe(path, rc, timer, config.getCachingOptions().getBinaryMaxAge(), eTagHandler),
				HandlerUtils.simpleFailureHandler(rc, "Error while handling file path " + path));
	}

	/**
	 * Determine the appropriate Handlebars template for the loaded node and prepare the breadcrumbs.
	 *
	 * @param rc The current routing context
	 */
	public void prepareRender(RoutingContext rc) {
		JsonObject node = rc.get(Constants.RC_NODE_TO_RENDER);
		RenderMode mode = rc.get(Constants.RC_RENDER_MODE);
		PortalRoutingContext prc = wrap(rc);

		rc.put(Constants.RC_CONFIG, config);

		if ((node == null || mode == null) && prc.hasMissingPerm()) {
			if (prc.isAuthenticationEnabled()) {
				// This will be handled by the authentication handler later.
				rc.next();
			} else {
				log.warn("Authentication is disabled, but requested page has missing permissions");
				rc.fail(HttpResponseStatus.UNAUTHORIZED.code());
			}

			return;
		}

		if (mode == null) {
			log.error("Could not determine render mode");
			rc.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

			return;
		}

		if (node == null) {
			log.error("No node data loaded for rendering");
			rc.fail(HttpResponseStatus.NOT_FOUND.code());

			return;
		}

		String templateName = null;

		switch (mode) {
		case Page:
			templateName = node.getJsonObject("fields").getString("templateName");
			break;

		case Folder:
			templateName = config.getFolderTemplateName();
			if (templateName == null) {
				rc.fail(HttpResponseStatus.NOT_FOUND.code());
				return;
			}
			break;

		case Raw:
			JsonObject fields = node.getJsonObject("fields");

			if (fields == null) {
				log.warn("Rendered node does not contain any fields");
			}

			String contentType = rc.get(RC_CONTENT_TYPE);

			if (contentType == null || contentType.isEmpty()) {
				log.warn("No content type specified for raw rendering, using \"text/html\"");
				contentType = "text/html";
			}

			rc.response()
				.putHeader(HttpHeaders.CONTENT_TYPE, contentType)
				.end(fields == null ? "" : fields.getString("content", ""));

			return;

		default:
			log.error("Unexpected render mode {}", mode.name());
		}

		if (templateName == null || templateName.isEmpty()) {
			log.error("Could not determine template to render");
			rc.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

			return;
		}


		transformBreadcrumbs(node);
		// When the request goes to a folder, the node to render can be different from the node in the
		// data object (the start page of the folder).
		((JsonObject) rc.get("data")).put("node", node);

		rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
		rc.put(Constants.RC_TEMPLATE_NAME, templateName + ".hbs");
		rc.put("node", node);
		rc.next();
	}

	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);

		log.trace("Loading requested content");
		// The template handler is blocking so it is possible we get here, although
		// the preview handler already starting working on this request.
		if (prc.isPreview()) {
			return;
		}

		int rerouteStatus = prc.getRerouteStatusCode();

		if (rerouteStatus > 0) {
			rc.response().setStatusCode(rerouteStatus);
		}

		rc.put("loggedin", rc.user() != null);

		String path = rc.pathParam("param0");

		if (path == null) {
			path = "";
		}

		if (path.equals("favicon.ico")) {
			rc.response().setStatusCode(404).end("Not found");
			return;
		}

		// Render the index page for root page requests
		if (path.isEmpty()) {
			if (StringUtils.isBlank(config.getStartPage())) {
				log.warn("No startpage configured");
			} else {
				loadStartPage(prc);

				return;
			}
		}

		if (log.isDebugEnabled()) {
			log.debug("Handling request for path {}", path);
		}

		String pathForMime = path.toLowerCase();
		String mimeType = MimeMapping.mimeTypeForFilename(pathForMime);

		if (mimeType != null
				&& (
					mimeType.startsWith("image/")
					|| mimeType.startsWith("application/")
					|| mimeType.startsWith("video/")
					|| mimeType.startsWith("audio/")
				)
		) {
			handleFiles(path, prc);
		} else {
			handlePage(path, prc);
		}
	}

	/**
	 * Clear the internal cache.
	 */
	public void clearCache() {
		queryStore.clearCache();
	}

	/**
	 * Load the startpage in the appropriate language.
	 *
	 * @param prc The current routing context
	 */
	private void loadStartPage(PortalRoutingContext prc) {
		startPageLanguages.get(prc, cacheKey -> loadPageLanguages(prc, config.getStartPage()))
			.subscribe(languages -> {
				String lang = prc.getLanguage();
				String langPath = languages.get(lang);

				if (langPath == null) {
					log.warn("Startpage is not available in language \"{}\"", lang);

					langPath = config.getStartPage();
				}

				log.debug("Loading startpage {}", langPath);
				handlePage(langPath, prc);
			});
	}

	/**
	 * Load the available languages for the page with the specified path.
	 *
	 * @param prc The current routing context
	 * @param path The path of the page to load
	 * @return A <code>Single</code> with a mapping from languages to the respective path
	 */
	private Single<Map<String, String>> loadPageLanguages(PortalRoutingContext prc, String path) {
		GraphQLRequest request = new GraphQLRequest()
			.setQuery(queryStore.getLanguagesQuery())
			.setVariables(new JsonObject().put("path", path));

		log.debug("Loading available languages for {}", path);

		return meshClientProvider.get(prc).graphql(projectName, request, prc.getVersion()).toSingle()
			.observeOn(RxUtils.scheduler(prc.wrapped()))
			.doOnError(portalInfo::checkState)
			.doOnError(RxUtils.onErrorRethrowCode(prc, log, "loadLanguages"))
			.map(GraphQLResponse::getData)
			.map(data -> JsonUtils.getValue(data, "node.languages", new JsonArray()))
			.map(HandlerUtils::mapLanguageToPath);
	}

	/**
	 * Keeps only first {@link PortalConfig#getBreadcrumbsLevel() breadcrumbsLevel} breadcrumbs (in direction from the root node), all others are removed from the list.
	 *
	 * @param node The page for which breadcrumbs should be adjusted
	 */
	private void transformBreadcrumbs(JsonObject node) {
		if (node == null || breadcrumbsLevel <= 0) {
			return;
		}

		JsonArray breadcrumbs = node.getJsonArray("breadcrumb");
		int numBreadcrumbs = breadcrumbs == null ? 0 : breadcrumbs.size();

		if (numBreadcrumbs <= breadcrumbsLevel) {
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug(
				"Removing {} breadcrumb entries (max level: {})",
				numBreadcrumbs - breadcrumbsLevel,
				breadcrumbsLevel);
		}

		while (breadcrumbs.size() > breadcrumbsLevel) {
			breadcrumbs.remove(0);
		}
	}
}
