package com.gentics.mesh.portal.content;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.core.rest.graphql.GraphQLError;
import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.api.content.NavigationTransformer;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.monitoring.PortalMetric;
import com.gentics.mesh.portal.api.monitoring.PortalTimer;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.NavigationOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Put navigation information in the {@link RoutingContext}.
 *
 * <p>
 * The navigation is loaded from cache if available and from Mesh otherwise. The cache uses the default key
 * mapper which will create a cache key from the current language and the user roles hash of the currently
 * used {@link MeshRestClient client}.
 * </p>
 *
 * <p>
 * An instance of {@link NavigationTransformer} is used to postprocess the navigation data. This transformer
 * is for example responsible for sorting and removing hidden elements.
 * </p>
 */
@Singleton
public class NavigationHandler implements Handler<RoutingContext> {

	private static final String FIELD_ANCESTORS = "ancestors";

	/** The namespace of the used cache. */
	private static final String NAVIGATION_CACHE = "mesh.portal.cache.navigation";

	private static final Logger log = LoggerFactory.getLogger(NavigationHandler.class);

	private final MeshRestClientProvider clientProvider;
	private final QueryStore queryStore;

	/** The navigation transformer instance for postprocessing loaded navigation data. */
	private final NavigationTransformer transformer;

	/** The metrics service instance. */
	private final MetricsService metricsService;

	/** The Mesh project name. */
	private final String projectName;

	/** The cache for processed navigation data. */
	private final PortalCache<JsonArray> cache;

	@Inject
	public NavigationHandler(
			PortalConfig config,
			MeshRestClientProvider clientProvider,
			QueryStore queryStore,
			CacheProvider cacheProvider,
			NavigationTransformer transformer, MetricsService metricsService) {
		this.clientProvider = clientProvider;
		this.queryStore = queryStore;
		this.transformer = transformer;
		this.metricsService = metricsService;
		this.projectName = config.getMeshProject();
		this.cache = initCache(config, cacheProvider);

		log.info("NavigationHandler initialized using navigation transformer \"{}\"", transformer.getClass().getSimpleName());
	}

	/**
	 * Put navigation data in the routing context.
	 *
	 * <p>
	 * When navigation is not enabled for this request (e.g. file downloads), this handler will do nothing
	 * and immediatelly call {@link RoutingContext#next() rc.next()}.
	 * </p>
	 *
	 * <p>
	 * First the cache is checked if it already contains navigation information. If not it is
	 * {@link #loadNavigation(RoutingContext) loaded from Mesh}.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = PortalRoutingContext.wrap(rc);

		if (!prc.navigationEnabled()) {
			rc.next();

			return;
		}

		log.debug("Loading navigation");

		cache.get(rc, this::loadNavigation)
			.subscribe((nav, error) -> {
				if (error != null) {
					log.error("Could not load navigation: {}", error.getMessage(), error);
				}

				if (nav == null) {
					nav = new JsonArray();
				}

				prc.setNavigation(nav);
				rc.next();
			});
	}

	/**
	 * Setup the cache.
	 *
	 * <p>
	 * This method will also register eventbus events that the cache is cleared when nodes or schemas of this
	 * project are changed.
	 * </p>
	 *
	 * @param config The portal configuration
	 * @param cacheProvider The cache provider to use
	 * @return An initialized cache for navigation data
	 */
	private PortalCache<JsonArray> initCache(PortalConfig config, CacheProvider cacheProvider) {
		NavigationOptions options = config.getNavigationOptions();

		if (options == null) {
			options = new NavigationOptions();
		}

		return cacheProvider.<JsonArray>cacheBuilder()
			.namespace(NAVIGATION_CACHE)
			.expireTime(options.getExpireTime())
			.keyMapper(this::keyMapper)
			.clearEvents(cacheProvider.defaultClearEvents())
			.eventFilter(cacheProvider::defaultEventFilter)
			.build();
	}

	/**
	 * Load navigation data from Mesh via GraphQL.
	 *
	 * @see #handleResponse(GraphQLResponse, RoutingContext)
	 * @param rc The current routing context
	 * @return A <code>Single</code> containing the loaded navigation data
	 */
	private Single<JsonArray> loadNavigation(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		MeshRestClient client = prc.isPreview() ? clientProvider.get() : clientProvider.get(rc);
		GraphQLRequest request = new GraphQLRequest()
			.setQuery(queryStore.getNavigationQuery())
			// TODO the language variable is not used yet in the default query.
			.setVariables(new JsonObject().put("lang", prc.getContentLanguage()));

		log.debug("Loading navigation from Mesh");

		PortalTimer timer = metricsService.wrapTimer(PortalMetric.MESH_LOAD_NAVIGATION);

		return client
			.graphql(projectName, request, prc.getVersion())
			.toSingle()
			.observeOn(RxUtils.scheduler(rc))
			.doOnSubscribe(ignored -> timer.start())
			.doFinally(timer::stop)
			.flatMap(response -> this.handleResponse(response, rc));
	}

	/**
	 * Checks the response for errors, and applies the {@link #transformer NavigationTransformer} if everything
	 * went well.
	 *
	 * @param response The GraphQL response containing navigation data
	 * @return A <code>Single</code> containing the processed navigation data
	 */
	private Single<JsonArray> handleResponse(GraphQLResponse response, RoutingContext rc) {
		JsonObject data = response.getData();
		List<GraphQLError> errors = response.getErrors();

		if (data == null) {
			log.error("Navigation query returned no data");

			if (errors != null && log.isDebugEnabled()) {
				Set<String> messages = new HashSet<>();

				errors.forEach(e -> messages.add(e.getMessage()));
				messages.forEach(log::debug);
			}

			return Single.just(new JsonArray());
		}

		JsonArray firstLevel = Optional.ofNullable(data.getJsonObject("rootNode"))
			.map(r -> r.getJsonObject("children"))
			.map(c -> c.getJsonArray("elements"))
			.orElse(new JsonArray());

		prepareAncestors(null, firstLevel);

		if (log.isTraceEnabled()) {
			log.trace("Navigation response:\n{}", data.encodePrettily());
		}

		return Single.fromCallable(() -> transformer.transform(data, rc));
	}

	/**
	 * Recursively creates the ancestors for the given {@code elements} and
	 * their children from the ancestors of the {@code parent} and the parents
	 * UUID.
	 *
	 * <p>
	 *     The parent may be {@code null} for toplevel elements. In this case
	 *     the ancestors for those elements will be an empty {@link JsonArray}.
	 * </p>
	 *
	 * <p>
	 *     <strong>Important:</strong> when an element in the array does not
	 *     have a UUID set, no ancestor information will be computed for its
	 *     child elements.
	 * </p>
	 *
	 * @param parent The parent item in the navigation (may be {@code null}
	 * 		if {@code elements} are the top level items
	 * @param elements The array with navigation entries to prepare
	 */
	private void prepareAncestors(JsonObject parent, JsonArray elements) {
		if (elements == null || elements.isEmpty()) {
			return;
		}

		JsonArray ancestors = parent == null
			? new JsonArray()
			: parent.getJsonArray(FIELD_ANCESTORS)
				.copy()
				.add(parent.getString("uuid"));

		for (Object elt : elements) {
			if (elt instanceof JsonObject) {
				JsonObject json = (JsonObject) elt;

				json.put(FIELD_ANCESTORS, ancestors);

				String uuid = json.getString("uuid");

				if (StringUtils.isBlank(uuid)) {
					if (log.isWarnEnabled()) {
						log.warn("Navigation element without UUID found, ancestor information may be incomplete:\n{}", json.encodePrettily());
					}

					continue;
				}

				// At this point we know that json has a non-empty "uuid" field
				// as well as a possibly empty "ancestor" field (none of them
				// are null).
				prepareAncestors(json, JsonUtils.getValue(json, "children.elements", null));
			}
		}
	}

	/**
	 * Create a cache key from the routing contexts language, user roles, branch
	 * and the result of {@link NavigationTransformer#additionalCacheKey(RoutingContext)}.
	 *
	 * @param rc The current routing context
	 * @return A cache key for the current navigation request
	 */
	private String keyMapper(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		BranchResponse branch = prc.getBranch();

		return prc.getContentLanguage()
			+ '/'
			+ prc.getUserRolesHash()
			+ '/'
			+ (branch == null ? "default-branch" : branch.getUuid())
			+ '/'
			+ transformer.additionalCacheKey(rc);
	}
}
