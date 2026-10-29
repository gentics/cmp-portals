package com.gentics.mesh.portal.search;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.util.RxUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.SearchOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.rest.client.MeshRestClient;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.handlebars.HandlebarsTemplateEngine;

/**
 * Handler that will perform search request to the Mesh search plugin, and
 * render the results using a configured handlebars template.
 */
@Singleton
public class SearchHandler extends AbstractBaseHandler {

	// TODO: Those could be interesting for other helpers too. Maybe put this in the PortalHttpVerticle.
	/** The routing context key for query parameters. */
	static final String RC_QUERY_PARAMS = "gpj_request_query";

	static final String RC_ACTION = "gpj_search_actionBase";
	static final String RC_CUR_ACTION = "gpj_search_curAction";

	/** The routing context key for the search results. */
	static final String RC_SEARCH_RESULTS = "gpj_search_results";

	private static final String PARAM_QUERY = "query";

	private static final Logger log = LoggerFactory.getLogger(SearchHandler.class);

	/** Fake breadcrumbs for search results. */
	private final static JsonObject SEARCH_RESULT_BREADCRUMB = new JsonObject()
		.put("node", new JsonObject()
			.put("breadcrumb", new JsonArray()
				.add(new JsonObject())
				.add(new JsonObject()
					.put("path", "/search")
					.put("fields", new JsonObject()
						.put("name", "Search")))));

	private final HandlebarsTemplateEngine engine;
	private final MeshRestClient adminClient;
	private final String endpoint;
	private final String searchUrlBase;
	private final String resultTemplate;

	@Inject
	public SearchHandler(PortalConfig config, io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine engine, MeshRestClient adminClient) {
		SearchOptions searchOptions = config.getSearchOptions();

		if (searchOptions.isEnabled()) {
			this.engine = new HandlebarsTemplateEngine(engine);
			this.adminClient = adminClient;
			this.endpoint = searchOptions.getEndpoint();
			this.searchUrlBase = String.format("/%s/plugins/search/", config.getMeshProject());

			String templateName = StringUtils.appendIfMissing(searchOptions.getTemplate(), ".hbs");
			File templateFile = new File(config.getTemplatesPath(), templateName);
			String templatePath = templateFile.getPath();
			String handlerName = getClass().getSimpleName();

			if (templateFile.exists()) {
				log.info("{} initialized with endpoint {}", handlerName, endpoint);
				this.resultTemplate = templatePath;
			} else {
				log.warn("{} could not be initialized because {} does not exist", handlerName, templatePath);
				this.resultTemplate = null;
			}
		} else {
			this.engine = null;
			this.adminClient = null;
			this.searchUrlBase = this.endpoint = this.resultTemplate = null;
		}
	}

	/**
	 * Add routes for searching and rendering the results.
	 *
	 * <p>
	 *     The endpoint configured in {@link SearchOptions#getEndpoint()} will
	 *     be added as a route that uses the default {@code /search} endpoint
	 *     of the plugin.
	 * </p>
	 *
	 * <p>
	 *     The route {@code endpoint/:queryName} will use the plugin endpoint
	 *     {@code /custom/queryName}.
	 * </p>
	 *
	 * <p>
	 *     Routes will only be added when a result template is available.
	 * </p>
	 *
	 * @param router The portals router
	 */
	@Override
	public void addRoutes(Router router) {
		if (resultTemplate != null) {
			router.get()
				.order(RouteOrder.API_ROUTES.getOrder())
				.handler(this::exposeSearchConfig);

			router.route(endpoint)
				.order(RouteOrder.API_ROUTES.getOrder())
				.handler(this)
				.blockingHandler(this::renderResults);

			router.route(endpoint + "/:" + PARAM_QUERY)
				.order(RouteOrder.API_ROUTES.getOrder())
				.handler(this)
				.blockingHandler(this::renderResults);
		}
	}

	/**
	 * Perform a search query to the search plugin, and put the result in the
	 * routing context.
	 *
	 * <p>
	 *     When a {@link PortalRoutingContext#getUserClient() user client} is
	 *     available it will be used for the search request. Otherwise the
	 *     admin client will be used.
	 * </p>
	 *
	 * <p>
	 *     When no query parameter {@code q} is present, this handler will do
	 *     nothing.
	 * </p>
	 *
	 * <p>
	 *     Additionally to the search results, this handler will also put the
	 *     requests query parameters, and a fake breadcrumb in the routing
	 *     context.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		MultiMap params = rc.queryParams();

		if (!params.contains("q")) {
			log.debug("No search query present, skipping actual search request");
			rc.next();

			return;
		}

		List<String> queryParams = new ArrayList<>();

		params.forEach(p -> queryParams.add(p.getKey() + '=' + p.getValue()));

		String action;
		String query = rc.pathParam(PARAM_QUERY);

		if (StringUtils.isEmpty(query)) {
			query = "search";
			action = endpoint;
		} else {
			query = StringUtils.stripStart(query, "/");
			action = endpoint + "/" + query;
			query = "custom/" + query;
		}

		String search = searchUrlBase + query + "?" + String.join("&", queryParams);
		MeshRestClient client = wrap(rc).getUserClient();

		if (client == null) {
			log.debug("No user Mesh client is provided, switching to the admin Mesh client.");
			client = adminClient;
		}

		client.get(search)
			.toSingle()
			.observeOn(RxUtils.scheduler(rc))
			.subscribe(results ->
					rc.put("data", SEARCH_RESULT_BREADCRUMB) // Fake breadcrumbs
						// Needed to create the pagination URLs.
						.put(RC_CUR_ACTION, action)
						.put(RC_QUERY_PARAMS, rc.queryParams() != null 
							? (rc.queryParams().entries().stream()
								.flatMap(entry -> Arrays.stream(entry.getValue().split(",")).map(item -> new ImmutablePair<>(entry.getKey(), item)))
								.reduce(
										new JsonObject(), 
										(jo, entry) -> {
											jo.put(entry.getKey(), jo.getJsonArray(entry.getKey(), new JsonArray()).add(entry.getValue()));
											return jo;
										}, 
										(a, b) -> a)
								)
							: new JsonObject())
						.put(RC_SEARCH_RESULTS, results)
						.next(),
				rc::fail);
	}

	/**
	 * Render the search results using the configured template.
	 *
	 * @param rc The current routing context
	 */
	void renderResults(RoutingContext rc) {
		engine.rxRender(rc.data(), resultTemplate)
			.subscribe(
				rs -> rc.response()
					.setStatusCode(HttpResponseStatus.OK.code())
					.putHeader(HttpHeaders.CONTENT_TYPE, "text/html")
					.end(rs),
				rc::fail);
	}

	/**
	 * Expose the configured search endpoint in the routing context.
	 *
	 * @param rc The current routing context
	 */
	void exposeSearchConfig(RoutingContext rc) {
		rc.put(RC_ACTION, endpoint)
			.next();
	}
}
