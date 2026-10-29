package com.gentics.mesh.portal.content;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.NodeParameters;
import com.gentics.mesh.parameter.VersioningParameters;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.parameter.client.VersioningParametersImpl;
import com.gentics.mesh.portal.DataProviderVerticle;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CustomRedirectOptions;
import com.gentics.mesh.portal.config.option.RedirectOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.context.RedirectMode;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshWebrootResponse;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Tries to redirect erroneous requests to predefined redirection URLS or respective status pages.
 */
@Singleton
public class RedirectHandler extends AbstractBaseHandler {

	/** The entry in the language to path map, that contains the configured path as is. */
	public static final String DEFAULT_REDIRECT_KEY = "default";

	/**
	 * Options for the source of custom redirect mappings.
	 */
	private enum RedirectsSourceMode {
		/** The page containing the redirect map is fetched by the data provider. */
		DataProvider,
		/** The page containing the redirect map is fetched only at the first request. */
		LoadOnce,
		/** The page containing the redirect map is fetched for every request. */
		Reload
	}

	private static final Logger log = LoggerFactory.getLogger(RedirectHandler.class);
	/** Prefix marking a source as a keyword for the {@link DataProviderVerticle data provider} */
	private static final String DATA_PROVIDER_PREFIX = "dataProvider:";

	private final String projectName;

	/** The confiugred mapping from HTTP status codes to paths of the respective status pages. */
	private final Map<String, String> configuredStatusPages;

	/** Cache containing mappings from language to the respective status page for that language. */
	private final PortalCache<Map<String, String>> statusPageCache;

	private final MeshRestClient meshClient;

	private final QueryStore queryStore;

	private final PortalInfo portalInfo;

	/** A map of custom redirects. */
	private Map<String, String> customRedirects;

	/** The redirect type to be used (301 or 303). */
	private HttpResponseStatus redirectType = HttpResponseStatus.SEE_OTHER;

	/**
	 * The key for the data provider if the customSourceMode is DataProvider, or the path of the status page otherwise.
	 */
	private String customSource;

	/** Where to get the custom redirect map from, and when to refresh it. */
	private RedirectsSourceMode customSourceMode;

	/** A helper class extracting the actual redirect map from a page. */
	private RedirectHelper helper;

	/**
	 * Default constructor.
	 *
	 * The available configuration options for the handler can be seen in {@link RedirectOptions}.
	 *
	 * @param portalConfig The portal configuration
	 * @param meshClient The injected Mesh REST client
	 * @param cacheProvider The injected <code>CacheProvider</code>
	 * @param queryStore The injected <code>QueryStore</code>
	 */
	@Inject
	public RedirectHandler(
				PortalConfig portalConfig,
				MeshRestClient meshClient,
				CacheProvider cacheProvider,
				QueryStore queryStore,
				PortalInfo portalInfo) {
		this.meshClient = meshClient;
		this.queryStore = queryStore;
		this.portalInfo = portalInfo;

		RedirectOptions options = portalConfig.getRedirectOptions();

		if (options == null) {
			options = new RedirectOptions();
		}

		this.projectName = portalConfig.getMeshProject();
		this.configuredStatusPages = options.getStatusPages() == null
			? Collections.emptyMap()
			: options.getStatusPages();


		initCustomRedirects(options.getCustom());

		statusPageCache = cacheProvider.<Map<String, String>>cacheBuilder()
			.namespace("mesh.portal.cache.statusPages")
			.expireMode(ExpireMode.Write)
			.keyMapper(rc -> String.valueOf(wrap(rc).sanitizedStatusCode()))
			.clearEvents(cacheProvider.defaultClearEvents())
			.eventFilter(cacheProvider::defaultEventFilter)
			.build();
	}

	@Override
	public void addRoutes(Router router) {
		// Nothing to do, this handler is added as an error handler in PortalHttpVerticle.
	}

	/**
	 * Get a normalised version of the given URL without trailing slashes.
	 *
	 * @param url The URL to normalise
	 * @return A normalised version of the given URL without trailing slashes
	 */
	static String normaliseUrl(String url) {
		try {
			URI uri = new URI(url);
			String path = uri.normalize().getPath();

			if (path == null) {
				return null;
			}

			if (path.endsWith("/")) {
				return path.substring(0, path.length() - 1);
			}

			return path;
		} catch (URISyntaxException e) {
			log.error("Invalid url found during redirect; {}", url);
		}

		return url;
	}

	/**
	 * Tries to redirect an erroneous request.
	 *
	 * When the cause of the error is the Mesh REST client, its status code and error message are sent in the response.
	 *
	 * Otherwise if the status of the error is 404 the custom redirect map is checked if there is a redirect for the current URI.
	 *
	 * When there is no redirect specified, the handler tries to redirect to a status page for the respective error.
	 *
	 * If all else fails, an empty response with the respective code is sent.
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		int status = wrap(rc).sanitizedStatusCode();

		if (log.isDebugEnabled()) {
			HttpServerRequest req = rc.request();

			log.debug("Handling erroneous request: {} {} (status {})", req.method(), req.path(), status);

			if (log.isTraceEnabled()) {
				String entries = req.headers().entries().stream()
					.map(e -> e.getKey() + ": " + e.getValue())
					.collect(Collectors.joining("\n  - ", "\n  - ", ""));

				log.trace("Request headers:{}", entries);

				entries = req.params().entries().stream()
					.map(e -> e.getKey() + ": " + e.getValue())
					.collect(Collectors.joining("\n  - ", "\n  - ", ""));


				log.trace("Query parameters:{}", entries);
			}
		}

		if (rc.response().ended()) {
			log.warn("Cannot redirect or reroute because the response has already been sent");
		} else if (status == HttpResponseStatus.NOT_FOUND.code() && helper != null) {
			tryMappedRedirect(rc, status);
		} else {
			tryStatusRedirect(rc, status);
		}
	}

	/**
	 * Force reloading the custom redirect map.
	 *
	 * This method will do nohting if the {@link #customSourceMode} is anything else but <code>LoadOnce</code>
	 * 
	 * @param rc current routing context
	 */
	public void refreshNow(RoutingContext rc) {
		log.debug("Forcing reload of redirects map");

		if (customSourceMode != RedirectsSourceMode.LoadOnce) {
			// Nothing to do, this is either handled by the data provider, or the map is
			// loaded for every request anyways.
			return;
		}

		loadFromMesh(rc).subscribe(this::processMeshResponse, this::logError);
	}

	/**
	 * Tries to redirect the request to a page describing the occured error.
	 *
	 * @param rc The current routing context
	 * @param status The HTTP status of the current request
	 */
	private void tryStatusRedirect(RoutingContext rc, int status) {
		PortalRoutingContext prc = wrap(rc);
		String uri = rc.request().uri();

		if (rc.response().closed()) {
			log.warn("Request was already closed");
			// No option to redirect. Request already handled
			return;
		}

		statusPageCache.get(rc, this::loadStatusPageLanguages).subscribe(redirects -> {
			String redirect = redirects.get(prc.getLanguage());

			if (redirect == null) {
				log.warn("No redirect available for status {} and language {}", status, prc.getLanguage());

				redirect = redirects.get(DEFAULT_REDIRECT_KEY);
			}

			if (redirect == null) {
				// This should not happen because the configured path is always in the map as the default entry.
				log.warn("No redirect available for status {}", status);
			} else if (redirect.equals(uri)) {
				log.warn("The configured status page for {} ({}) causes a {} status itself", status, redirect, status);
				log.debug("Sending empty response with status {}", status);
			} else {
				log.debug("Rerouting to status {} page {}", status, redirect);
				prc.disableCache().reroute(redirect, status);

				return;
			}

			rc.response().setStatusCode(status).end();
		});
	}

	/**
	 * Load the available languages for the configured status redirect.
	 *
	 * <p>
	 *     When no redirect is configured for the current status code, the map in the returned <code>Single</code>
	 *     will be empty.
	 * </p>
	 *
	 * @see PortalRoutingContext#sanitizedStatusCode()
	 * @param rc The current routing context
	 * @return A single containing a map from language codes to the paths of the status redirects
	 */
	private Single<Map<String, String>> loadStatusPageLanguages(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		int status = prc.sanitizedStatusCode();
		String path = configuredStatusPages.get(String.valueOf(status));

		if (path == null) {
			log.warn("No status page redirect configured for {}", status);

			return Single.just(Collections.emptyMap());
		}

		log.debug("Loading languages for status page {}", path);

		GraphQLRequest request = new GraphQLRequest()
			.setQuery(queryStore.getLanguagesQuery())
			.setVariables(new JsonObject().put("path", path));

		return meshClient.graphql(projectName, request, prc.getVersion()).toSingle()
			.observeOn(RxUtils.scheduler(rc))
			.map(GraphQLResponse::getData)
			.map(data -> JsonUtils.getValue(data, "node.languages", new JsonArray()))
			.map(HandlerUtils::mapLanguageToPath)
			.doOnError(portalInfo::checkState)
			.doOnError(RxUtils.onErrorRethrowCode(prc, log, "loadLanguages"))
			.doOnSuccess(languages -> languages.put(DEFAULT_REDIRECT_KEY, path));
	}


	/**
	 * Redirects the current request according to the {@link #customRedirects custom redirect map} or to the respective status page if no redirect is specified.
	 *
	 * @param rc The current routing context
	 * @param status The HTTP status of the current request
	 */
	private void redirect(RoutingContext rc, int status) {
		String curPath = normaliseUrl(rc.normalizedPath());

		log.debug("Checking for redirect entry for {}", curPath);

		if (customRedirects == null) {
			log.warn("Redirect mappings not available");
		} else if (customRedirects.containsKey(curPath)) {
			String redirect = customRedirects.get(curPath);
			String uri = rc.request().uri();

			redirect += getQueryPart(uri, redirect.indexOf('?') >= 0);

			log.debug("Found redirect for {}", uri);

			wrap(rc).redirect(redirect, RedirectMode.TRUSTED, redirectType);

			return;
		}

		log.debug("No redirect entry found");

		tryStatusRedirect(rc, status);
	}

	/**
	 * Uses the {@link RedirectHelper} to convert the response from Mesh to a map of redirects.
	 *
	 * The result of the conversion is stored in {@link #customRedirects}.
	 *
	 * @param redirectsPage The content of the loaded page containing the redirects.
	 */
	private void convertData(JsonObject redirectsPage) {
		Map<String, String> redirects = helper.convertData(redirectsPage);

		if (redirects == null || redirects.isEmpty()) {
			log.warn("No redirects found in \"{}\"", customSource);

			customRedirects = null;
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Loaded {} redirect{}", redirects.size(), redirects.size() == 1 ? "" : "s");
			}

			customRedirects = redirects;
		}
	}

	/**
	 * Refreshes the redirect map if necessary and tries to redirect the current request according to its entries.
	 *
	 * @see #redirect(RoutingContext, int)
	 *
	 * @param rc The current routing context
	 * @param status The HTTP status of the current request
	 */
	private void tryMappedRedirect(RoutingContext rc, int status) {
		switch (customSourceMode) {
		case DataProvider:
			JsonObject redirectsPage = rc.get(customSource);

			if (redirectsPage == null) {
				log.warn("Redirect data \"{}\" not found in routing context", customSource);

				break;
			} else {
				log.debug("Using redirect data \"{}\" from data provider", customSource);
			}

			convertData(redirectsPage);

			break;

		case LoadOnce:
			if (customRedirects != null) {
				log.debug("Custom redirects already loaded");

				break;
			}
			// Fall through

		case Reload:
			loadFromMesh(rc)
				.doOnSuccess(this::processMeshResponse)
				.doOnError(this::logError)
				.doFinally(() -> redirect(rc, status))
				.subscribe();

			// The actual redirect happens in the callback for loadFromMesh().
			return;

		default:
			log.warn("Unexpected redirects source: {}", customSourceMode.toString());
		}

		redirect(rc, status);
	}

	/**
	 * Makes sure the response is not a binary download and converts the data to a map.
	 *
	 * @param response The response from the Mesh client
	 */
	private void processMeshResponse(MeshWebrootResponse response) {
		if (response.isBinary()) {
			response.close();
			log.error("Response for the redirect data page \"{}\" was not a NodeResponse", customSource);
			return;
		}

		// JsonObject.mapFrom() gets confused by the structure of the node response
		// and puts a superfluous "node" field in the "fields".
		convertData(new JsonObject(response.getNodeResponse().toJson()));
	}

	/**
	 * Log the given error.
	 *
	 * @param cause The error to log
	 */
	private void logError(Throwable cause) {
		log.error("Could not load redirect data page \"{}\": {}", customSource, cause.getMessage(), cause);
	}

	/**
	 * Loads the page containing redirect data from Mesh and executes the specified action on success.
	 * @param rc current routing context
	 *
	 * @return A <code>Single</code> with the webroot response for the redirects source
	 */
	private Single<MeshWebrootResponse> loadFromMesh(RoutingContext rc) {
		log.debug("Loading redirect data from \"{}\"", customSource);

		NodeParameters nodeParameters = new NodeParametersImpl().setResolveLinks(LinkType.SHORT);
		VersioningParameters versioningParameters = rc != null ? wrap(rc).getVersion() : new VersioningParametersImpl();

		var ret = meshClient.webroot(projectName, customSource, nodeParameters, versioningParameters).toSingle();

		if (rc != null) {
			ret = ret.observeOn(RxUtils.scheduler(rc));
		}

		return ret;
	}

	/**
	 * Load a constructor for the given class name and makes sure the class implements {@link RedirectHelper}.
	 *
	 * @param className The class name to load a constructor for.
	 * @return The default constructor for the given class name
	 */
	@SuppressWarnings("unchecked")
	private Constructor<RedirectHelper> loadHelperConstructor(String className) {
		Class<?> helperClass;

		try {
			helperClass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			log.error("Invalid redirect helper specified", e);

			return null;
		}

		if (!RedirectHelper.class.isAssignableFrom(helperClass)) {
			log.error("Specified redirect helper is not an implementation of {}", RedirectHelper.class.getSimpleName());

			return null;
		}

		Constructor<RedirectHelper> ctor = null;

		try {
			ctor = ((Class<RedirectHelper>) helperClass).getConstructor();
		} catch (NoSuchMethodException e) {
			log.error("Redirect helper class {} does not have a default constructor", className, e);
		} catch (SecurityException e) {
			log.error("Could not instantiate redirect helper class", e);
		}

		return ctor;
	}

	/**
	 * Initialises custom redirects with the specified options.
	 *
	 * When no options are specified or no valid {@link RedirectHelper} class can be instantiated, custom redirects will be disabled.
	 *
	 * @param options The configuration for custom redirects
	 */
	private void initCustomRedirects(CustomRedirectOptions options) {
		if (options == null) {
			log.info("No custom redirects configured");

			return;
		}

		Constructor<RedirectHelper> ctor = loadHelperConstructor(options.getHelperClass());

		if (ctor == null) {
			log.error("No valid constructor found for redirect helper");
		} else {
			try {
				helper = ctor.newInstance();
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				log.error("Could not instantiate redirect helper class", e);
			}
		}

		if (helper == null) {
			log.error("Custom redirects will be disabled");

			return;
		} else {
			helper.init(options.getHelperConfig());
		}

		customSource = options.getSource();

		if (customSource.startsWith(DATA_PROVIDER_PREFIX)) {
			customSourceMode = RedirectsSourceMode.DataProvider;
			customSource = customSource.substring(DATA_PROVIDER_PREFIX.length());
		} else {
			customSourceMode = Boolean.TRUE.equals(options.isReload())
				? RedirectsSourceMode.Reload
				: RedirectsSourceMode.LoadOnce;
		}

		redirectType = options.isPermanent()
			? HttpResponseStatus.MOVED_PERMANENTLY
			: HttpResponseStatus.SEE_OTHER;

		if (log.isInfoEnabled()) {
			log.info(
				"Initialised custom {} redirects from \"{}\" ({})",
				options.isPermanent() ? "permanent" : "temporary",
				customSource,
				customSourceMode.toString());
		}
	}

	/**
	 * Get the query part from the given URI (including the <code>?</code>).
	 *
	 * <p>
	 *     When <code>existingParams</code> is <code>true</code> the resulting string will start with an
	 *     <code>&amp;</code> character, otherwise it will start with <code>?</code>.
	 * </p>
	 *
	 * @param uri The URI to get the query part from
	 * @param existingParams Whether the extracted query params are to be added to existing parameters
	 * @return The query part of the given URI
	 */
	private String getQueryPart(String uri, boolean existingParams) {
		int idx = uri.lastIndexOf('?');

		if (idx < 0) {
			return "";
		}

		return (existingParams ? '&' : '?') + uri.substring(idx + 1);
	}
}
