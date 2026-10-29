package com.gentics.mesh.portal.content;

import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * Service class to load language variants of a page specified by path.
 */
@Singleton
public class LanguageVariantLoader {

	private final static String RC_CACHE_KEY = "mesh.portal.langVariant.path";
	private final static String DEFAULT_KEY = "<default>";

	private final static Logger log = LoggerFactory.getLogger(LanguageVariantLoader.class);

	private final PortalCache<Map<String, String>> languageVariants;
	private final QueryStore queryStore;
	private final String projectName;
	private final MeshRestClientProvider meshClientProvider;
	private final MeshRestClient adminClient;
	private final PortalInfo portalInfo;

	@Inject
	public LanguageVariantLoader(
			PortalConfig portalConfig,
			CacheProvider cacheProvider,
			QueryStore queryStore,
			MeshRestClientProvider meshClientProvider,
			MeshRestClient adminClient,
			PortalInfo portalInfo) {
		this.languageVariants = cacheProvider.<Map<String, String>>cacheBuilder()
			.namespace("mesh.portal.languageVariants")
			.expireMode(ExpireMode.Write)
			.keyMapper(this::keyMapper)
			.clearEvents(cacheProvider.defaultClearEvents())
			.eventFilter(cacheProvider::defaultEventFilter)
			.build();

		this.queryStore = queryStore;
		this.projectName = portalConfig.getMeshProject();
		this.meshClientProvider = meshClientProvider;
		this.adminClient = adminClient;
		this.portalInfo = portalInfo;
	}

	/**
	 * Get language variants for the given path without using the cache.
	 *
	 * <p>
	 *     The returned mapping is from language codes to the path of the
	 *     respective language variant.
	 * </p>
	 *
	 * <p>
	 *     <em>Note</em> that the returned map will always contain the given
	 *     {@code path} with the key {@link #DEFAULT_KEY <default>}.
	 * </p>
	 *
	 * <p>
	 *     <strong>IMPORTANT:</strong> This method will not use the cache to
	 *     store the retrieved language variants. If possible use
	 *     {@link #getLanguageVariant(RoutingContext, String)} instead.
	 * </p>
	 *
	 * @param path The path to load language variants for
	 * @return A {@code Single} with a map of language variants
	 */
	public Single<Map<String, String>> getLanguageVariants(String path) {
		return adminClient.graphql(projectName, createRequest(path)).toSingle()
			.doOnSubscribe(ignored -> log.debug("Loading language variants for path without cache: {}", path))
			.compose(resultProcessor(path));
	}

	/**
	 * Get language variants for the given path using the cache.
	 *
	 * <p>
	 *     The returned mapping is from language codes to the path of the
	 *     respective language variant.
	 * </p>
	 *
	 * <p>
	 *     <em>Note</em> that the returned map will always contain the given
	 *     {@code path} with the key "{@link #DEFAULT_KEY &lt;default&gt;}".
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param path The path to load language variants for
	 * @return A {@code Single} with a map of language variants
	 */
	public Single<Map<String, String>> getLanguageVariants(RoutingContext rc, String path) {
		if (canLoadLanguageVariant(path)) {
			rc.put(RC_CACHE_KEY, path);

			return languageVariants.get(rc, languageVariantLoader(path))
				.doOnSuccess(map -> map.put(DEFAULT_KEY, path));
		}

		log.warn("Language variants cannot be loaded for external URL: {}", path);

		return Single.just(Collections.singletonMap(DEFAULT_KEY, path));
	}

	/**
	 * Get language variants for the currently requested
	 * {@link RoutingContext#normalisedPath() path}.
	 *
	 * <p>
	 *     The returned mapping is from language codes to the path of the
	 *     respective language variant.
	 * </p>
	 *
	 * <p>
	 *     The currently requested path will be available with the key
	 *     "{@link #DEFAULT_KEY &lt;default&gt;}".
	 * </p>
	 *
	 * @see #getLanguageVariant(RoutingContext, String)
	 * @param rc The current routing context
	 * @return A {@code Single} with a map of language variants
	 */
	public Single<Map<String, String>> getLanguageVariants(RoutingContext rc) {
		return getLanguageVariants(rc, rc.normalizedPath());
	}

	/**
	 * Get the specified language variant for the given path.
	 *
	 * <p>
	 *     When there is no variant in the specified language, the path
	 *     itself will be returned.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param path The path to load language variants for
	 * @param lang The desired language
	 * @return A {@code Single} with a map of language variants
	 */
	public Single<String> getLanguageVariant(RoutingContext rc, String path, String lang) {
		return getLanguageVariants(rc, path)
			.map(variants -> {
				String variant = variants.get(lang);

				if (variant == null) {
					variant = variants.get(DEFAULT_KEY);

					log.warn("Path {} is not available in {}", path, lang);
					log.debug("Falling back to default variant: {}", variant);
				}

				return variant;
			});
	}

	/**
	 * Get the language variant for the given path in the portals current
	 * language.
	 *
	 * <p>
	 *     When there is no variant in the specified language, the path
	 *     itself will be returned.
	 * </p>
	 *
	 * @see PortalRoutingContext#getLanguage()
	 * @param rc The current routing context
	 * @param path The path to load language variants for
	 * @return A {@code Single} with a map of language variants
	 */
	public Single<String> getLanguageVariant(RoutingContext rc, String path) {
		return getLanguageVariant(rc, path, PortalRoutingContext.wrap(rc).getLanguage());
	}

	/**
	 * Get the language variant for the currently requested path in the
	 * portals current language.
	 *
	 * <p>
	 *     When there is no variant in the specified language, the path
	 *     itself will be returned.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @return A {@code Single} with a map of language variants
	 */
	public Single<String> getLanguageVariant(RoutingContext rc) {
		return getLanguageVariant(rc, rc.normalizedPath());
	}

	/**
	 * Tests whether a language variant can be loaded for the given path.
	 *
	 * <p>
	 *     Currently language variants can <em>not</em> be loaded for absolute
	 *     URLs.
	 * </p>
	 *
	 * @param path The path to check
	 * @return {@code true} when any of the {@code getLanguageVariant} methods
	 * 		would actually try an load the variants, and {@code false} when
	 * 		trying to load language variants for the path is not feasible.
	 */
	public boolean canLoadLanguageVariant(String path) {
		return path != null && !path.startsWith("https://") && !path.startsWith("http://");
	}

	/**
	 * Get the {@link #RC_CACHE_KEY cache key} from the routing context.
	 *
	 * @param rc The current routing context
	 * @return The cache key stored in the routing context
	 */
	private String keyMapper(RoutingContext rc) {
		String key = rc.get(RC_CACHE_KEY);

		if (StringUtils.isBlank(key)) {
			log.warn("No cache key set for loading language variants");

			return "<missing-cache-key>";
		} else if (log.isTraceEnabled()) {
			log.trace("Using cache key: {}", key);
		}

		return key;
	}

	/**
	 * Return a function that will load the language variants for the specified
	 * path.
	 *
	 * @param path The path to load language variants for
	 * @return A function that will load the language variants for the specified path
	 */
	private Function<RoutingContext, Single<Map<String, String>>> languageVariantLoader(String path) {
		return rc -> {
			PortalRoutingContext prc = PortalRoutingContext.wrap(rc);

			return meshClientProvider.get(prc).graphql(projectName, createRequest(path), prc.getVersion()).toSingle()
				.observeOn(RxUtils.scheduler(rc))
				.doOnError(portalInfo::checkState)
				.doOnError(RxUtils.onErrorRethrowCode(prc, log, "loadLanguages"))
				.compose(resultProcessor(path));
		};
	}

	/**
	 * Create the GraphQL request to load the specified path.
	 *
	 * @param path The path to load language variants for
	 * @return The GraphQL request to load the specified path
	 */
	private GraphQLRequest createRequest(String path) {
		return new GraphQLRequest()
			.setQuery(queryStore.getLanguagesQuery())
			.setVariables(new JsonObject().put("path", path));
	}

	/**
	 * Create a transformer that will extract the available language variants
	 * from the GraphQL result.
	 *
	 * @param path The path to load language variants for
	 * @return A mapping from language code to the respective path
	 */
	private SingleTransformer<GraphQLResponse, Map<String, String>> resultProcessor(String path) {
		return upstream -> upstream
			.doOnSubscribe(ignored -> log.debug("Loading available languages for {}", path))
			.map(GraphQLResponse::getData)
			.map(data -> JsonUtils.getValue(data, "node.languages", new JsonArray()))
			.doOnSuccess(languages -> {
				if (log.isTraceEnabled()) {
					log.trace("Available languages: {}", languages.encodePrettily());
				}
			})
			.map(HandlerUtils::mapLanguageToPath);
	}
}
