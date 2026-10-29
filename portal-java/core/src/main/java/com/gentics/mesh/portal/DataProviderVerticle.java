package com.gentics.mesh.portal;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.http.HttpConstants;
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.ParameterProvider;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.parameter.client.VersioningParametersImpl;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.DataProviderOptions;
import com.gentics.mesh.portal.config.option.WebDataOptions;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.ReflectionUtils;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientMessageException;
import com.gentics.mesh.rest.client.MeshWebrootResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Completable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static com.gentics.mesh.portal.api.Constants.RC_NODE_TO_RENDER;
import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Periodically loads specified pages from Mesh and puts them in routing contexts.
 *
 * The configuration options can be found in {@link DataProviderOptions}.
 */
@Singleton
public class DataProviderVerticle extends AbstractVerticle {

	/**
	 * Functional interface analogous to {@link BiConsumer}.
	 *
	 * @param <T>
	 *            the type of the first argument to the operation
	 * @param <U>
	 *            the type of the second argument to the operation
	 * @param <V>
	 *            the type of the third argument to the operation
	 */
	@FunctionalInterface
	private interface TriConsumer<T, U, V> {
		void accept(T t, U u, V v);
	}

	/**
	 * Functional interface analogous to {@link BiConsumer}.
	 *
	 * @param <T>
	 *            the type of the first argument to the operation
	 * @param <U>
	 *            the type of the second argument to the operation
	 * @param <V>
	 *            the type of the third argument to the operation
	 * @param <W>
	 *            the type of the fourth argument to the operation
	 */
	@FunctionalInterface
	private interface QuadConsumer<T, U, V, W> {
		void accept(T t, U u, V v, W w);
	}

	public static final String META_DATA_KEY = "dpvmeta";
	public static final String TIMESTAMP_KEY = "timestamp";
	public static final String IS_FRESH_KEY = "fresh";

	/** Threshold for the timer below which a warning is issued. */
	private static final int WARN_TIME_THRESHOLD = 10;

	/** Routing context key for the flag indicating whether the data has already been set. */
	private static final String RC_DATA_SET = "data_set";

	private final Logger log = LoggerFactory.getLogger(DataProviderVerticle.class);
	/** A mapping from pages that should be loaded routing context keys to. */
	private final Map<String, String> meshDataKeys = Collections.synchronizedMap(new LinkedHashMap<>());

	/**
	 * Map of branch uuids to Sets of URLs, which should not be loaded (due to failure)
	 */
	private final Map<String, Set<String>> meshUrlBlacklist = Collections.synchronizedMap(new LinkedHashMap<>());

	/** A mapping from routing context keys to the options of a web datasource. */
	private final Map<String, WebDataOptions> webDataOptions = Collections.synchronizedMap(new LinkedHashMap<>());
	/** A mapping from routing context keys to the loaded web data. */
	private final Map<String, JsonObject> webData = new ConcurrentHashMap<>();
	/** A mapping from branch uuid to map from routing context keys to the loaded mesh data. */
	private final Map<String, Map<String, JsonObject>> meshData = new ConcurrentHashMap<>();

	/** The HTTP client to use for loading web datasources. */
	private HttpClient httpClient;
	/** The current project name in Mesh. */
	private String projectName;

	/** Whether to keep trying to load pages even though the last try failed. */
	private boolean retryOnError;
	/** The ID used to cancel the timer if necessary. */
	private long timerId;

	/** Post processor instances for the respective web datasources. */
	private Map<String, DataProviderPostProcessor> postProcessorInstances = new HashMap<>();

	private final BranchProvider branchProvider;
	private final MeshRestClient meshClient;
	private final PortalConfig portalConfig;

	/*
	 * Whether to log loading failures as errors.
	 *
	 * When a data source cannot be loaded, it is only logged as an error during
	 * the initial load. Later they are logged as warnings.
	 */
	private boolean logFailuresAsErrors = false;

	@Inject
	public DataProviderVerticle(BranchProvider branchProvider, MeshRestClient meshClient, PortalConfig portalConfig) {
		this.branchProvider = branchProvider;
		this.meshClient = meshClient;
		this.portalConfig = portalConfig;
	}

	/**
	 * Loads the configuration for the verticle and starts the periodic timer.
	 *
	 * <p>
	 * The verticle will try to load all specified datasources during start up, if any of those cannot be loaded the provided promise will be failed.
	 * </p>
	 *
	 * <p>
	 * Note that some errors may not be recognised until after the promise has been completed successfully (like invalid HTTP response bodies), but in most cases
	 * the portal should refuse to start, if specified datasource is not available.
	 * </p>
	 *
	 * @param promise The promise indicating successful deployment
	 */
	@Override
	public void start(Promise<Void> promise) {
		DataProviderOptions options = portalConfig.getDataProviderOptions();

		if (options == null) {
			log.info("Data provider is not enabled in configuration");
			promise.complete();

			return;
		}

		if (options.noDataSources()) {
			log.warn("Disabling data provider because configuration contains no data sources");
			promise.complete();

			return;
		}

		int refreshTime;

		try {
			refreshTime = init(options);
		} catch (InvalidConfigurationException e) {
			promise.fail(e);

			return;
		}

		if (log.isInfoEnabled()) {
			log.info("Starting data provider, refreshing every {} second{}", refreshTime, (refreshTime == 1 ? "" : "s"));
		}

		logFailuresAsErrors = true;

		if (options.isPreventStartupOnError()) {
			initialLoad(promise);
		} else {
			refreshNow();
			promise.complete();
		}

		timerId = vertx.setPeriodic(refreshTime * 1000L, id -> {
			if (meshDataKeys.isEmpty() && webDataOptions.isEmpty()) {
				log.warn("No data sources to refresh, stopping refresh timer");
				vertx.cancelTimer(id);

				return;
			}

			logFailuresAsErrors = false;
			refreshNow();
		});
	}

	/**
	 * Cancels the periodic timer.
	 */
	@Override
	public void stop() {
		vertx.cancelTimer(timerId);
	}

	/**
	 * Adds a handler for all routes that will put the loaded pages in the routing context.
	 *
	 * For requests to one of the configured data source pages, the pre-loaded data will be overridden with the data loaded by the content path handler.
	 *
	 * @param router
	 *            The router used by the application
	 */
	public void addRoutes(Router router) {
		router.route().order(RouteOrder.API_ROUTES.getOrder()).handler(this::addData);

		for (String path : meshDataKeys.keySet()) {
			router.route(path).order(RouteOrder.CONTENT_ROUTES.getOrder()).handler(this::overrideData);
		}
	}

	/**
	 * Adds a handler for all preview routes that will put the loaded pages in the routing context.
	 *
	 * For requests to one of the configured data source pages, the pre-loaded data will be overridden with the data loaded by the content path handler.
	 *
	 * @param router
	 *            The router used by the application
	 */
	public void addPreviewRoutes(Router router) {
		router.route().order(RouteOrder.API_ROUTES.getOrder()).handler(this::addData);

		router.routeWithRegex("/api/preview/(.*)").order(RouteOrder.API_ROUTES.getOrder())
			.method(HttpMethod.POST)
			.consumes(HttpConstants.APPLICATION_JSON)
			.produces(TemplateHandler.DEFAULT_CONTENT_TYPE)
			.handler(this::overrideData);
	}

	/**
	 * Performs the initialisation of this verticle.
	 *
	 * @see #start()
	 *
	 * @param options
	 *            This verticles configuration
	 *
	 * @return The time span after which the data pages should be refreshed
	 * @throws InvalidConfigurationException
	 */
	public int init(DataProviderOptions options) throws InvalidConfigurationException {
		projectName = portalConfig.getMeshProject();
		retryOnError = options.isRetryOnError();

		initMeshData(options.getMeshData());
		initWebData(options.getWebData(), options.isPreventStartupOnError());

		if (!webDataOptions.isEmpty()) {
			// TODO Make SSL and proxy settings configurable.
			setHttpClient(HttpClient.newInstance(vertx.createHttpClient(new HttpClientOptions().setTrustAll(true).setVerifyHost(false))));
		}

		int refreshTime = options.getRefreshTime();

		if (refreshTime < 1) {
			log.warn("Bumping invalid refresh time {} to {}", refreshTime, WARN_TIME_THRESHOLD);
			refreshTime = WARN_TIME_THRESHOLD;
		} else if (refreshTime < WARN_TIME_THRESHOLD) {
			log.warn("Refresh time of data provider is very low: {}", refreshTime);
		}

		return refreshTime;
	}

	/**
	 * Set the HTTP client to use for retrieving web data.
	 *
	 * <p>
	 * When there already is a client set, it will be closed.
	 * </p>
	 *
	 * <p>
	 * <strong>NOTE</strong> that this method should only be used in testing.
	 * </p>
	 *
	 * @param httpClient The HTTP client to use
	 */
	public void setHttpClient(HttpClient httpClient) {
		if (this.httpClient != null) {
			if (this.httpClient.equals(httpClient)) {
				return;
			}

			this.httpClient.close();
		}

		this.httpClient = httpClient;
	}

	/**
	 * Convenience method for {@link #refreshNow(TriConsumer, BiConsumer, QuadConsumer)} using the default handlers.
	 */
	public void refreshNow() {
		refreshNow(this::processMeshResponse, this::processWebResponse, this::processError).subscribe(() -> {}, t -> {});
	}

	/**
	 * Puts the loaded pages in the current routing context.
	 *
	 * @param rc
	 *            The current routing context
	 */
	public void addData(RoutingContext rc) {
		if (!Boolean.TRUE.equals(rc.get(RC_DATA_SET))) {
			log.debug("Putting data objects in routing context");

			addMeshData(rc).subscribe(() -> {
				addData(rc, webData);
				if (!rc.response().headWritten()) {
					rc.put(RC_DATA_SET, Boolean.TRUE);
					rc.next();
				} else {
					log.warn("The header for " + rc.request().absoluteURI() + " is already written!");
				}
			}, rc::fail);
		} else {
			rc.next();
		}
	}

	/**
	 * Calls {@link #refreshNow(TriConsumer, BiConsumer, QuadConsumer) refreshNow} with special error handlers that will fail the provided promise.
	 *
	 * Once all datasources have been loaded the promise is completed successfully.
	 *
	 * @param promise
	 *            The promise for verticle deployment
	 */
	private void initialLoad(Promise<Void> promise) {
		refreshNow(
			(branch, path, response) -> {
				if (!processMeshResponse(branch, path, response)) {
					log.error("Could not load \"{}\" from Mesh", path);
					promise.tryFail("Could not load \"" + path + "\" from Mesh");
				}
			},
			(key, response) -> {
				String url = webDataOptions.get(key).getUrl();

				if (!processWebResponse(key, response)) {
					log.error(
						"Could not load \"{}\" {}: {} {}",
						key,
						url,
						response.statusCode(),
						response.statusMessage());
					promise.tryFail("Could not load \"" + key + "\"");
				}
			},
			(branch, key, path, cause) -> processError(branch, key, path, cause))
			.subscribe(promise::complete, promise::fail);
	}

	/**
	 * Forces a reload of the configures data sources.
	 *
	 * <p>
	 * The datasources which should be refreshed are taken from {@link #meshDataKeys} and {@link #webDataOptions}.
	 * When {@link DataProviderOptions#isRetryOnError() retryOnError} is <code>false</code> the {@link #meshData} and
	 * {@link #webData} collections may contain stale entries which will no longer be refreshed.
	 * </p>
	 *
	 * @param meshSuccess
	 *            The response handler for a Mesh datasource
	 * @param webResponseHandler
	 *            The response handler for a web datasource
	 * @param error
	 *            The general error handler
	 * @return completable
	 */
	private Completable refreshNow(
		TriConsumer<BranchResponse, String, MeshWebrootResponse> meshSuccess,
		BiConsumer<String, HttpClientResponse> webResponseHandler,
		QuadConsumer<BranchResponse, String, String, Throwable> error) {
		log.debug("Refreshing data sources");

		List<Completable> completables = new ArrayList<>();
		for (Map.Entry<String, String> entry : meshDataKeys.entrySet()) {
			String path = entry.getKey();
			String key = entry.getValue();
			ParameterProvider nodeParameters = new NodeParametersImpl().setResolveLinks(LinkType.SHORT);

			completables.add(branchProvider.getBranches().flatMapCompletable(branch -> {
				if (!meshUrlBlacklist.getOrDefault(branch.getUuid(), Collections.emptySet()).contains(path)) {
					log.debug("Loading {} for branch {}", path, branch.getName());
					return meshClient.webroot(projectName, path, nodeParameters, new VersioningParametersImpl().published().setBranch(branch.getName()))
						.toSingle()
						.observeOn(RxHelper.scheduler(vertx))
						.doOnSuccess(result -> meshSuccess.accept(branch, path, result))
						.doOnError(cause -> error.accept(branch, key, path, cause))
						.toCompletable();
				} else {
					return Completable.complete();
				}
			}, true));
		}

		if (httpClient != null) {
			for (Map.Entry<String, WebDataOptions> entry : webDataOptions.entrySet()) {
				Completable entryRequest = httpClient.rxRequest(HttpMethod.GET, entry.getValue().getUrl())
					.observeOn(RxHelper.scheduler(vertx))
					.flatMap(HttpClientRequest::rxSend)
					.doOnSuccess(rs -> webResponseHandler.accept(entry.getKey(), rs))
					.doOnError(cause -> error.accept(null, entry.getKey(), entry.getValue().getUrl(), cause))
					.toCompletable();


				completables.add(entryRequest);
			}
		}

		return Completable.mergeDelayError(completables);
	}

	/**
	 * Registers Mesh datasources.
	 *
	 * @param meshData
	 *            A mapping of routing context keys to Mesh paths
	 */
	private void initMeshData(Map<String, List<String>> meshData) {
		if (meshData == null) {
			return;
		}

		log.info("Configuring Mesh data sources");

		for (Map.Entry<String, List<String>> entry : meshData.entrySet()) {
			String key = entry.getKey();

			for (String path : entry.getValue()) {
				if (!path.startsWith("/")) {
					path = "/" + path;
				}

				log.info("  Adding path for {}: {}", key, path);
				meshDataKeys.put(path, key);
			}
		}

	}

	/**
	 * Registers web datasources.
	 *
	 * <p>
	 * When {@link DataProviderOptions#isPreventStartupOnError() preventStartupOnError} is <code>true</code>, an
	 * {@link InvalidConfigurationException} is thrown when <code>webDataConfig</code> contains a key that is also in
	 * {@link #meshData}. When the startup should not be prevented a warning is logged instead.
	 * </p>

	 * @param webDataConfig
	 *            A mapping of routing context keys to <code>WebDataOptions</code>
	 * @param preventStartUp
	 *            Whether to throw an {@link InvalidConfigurationException} when a duplicate key is encountered
	 * @throws InvalidConfigurationException
	 *             When the options of a web datasource do not contain an URL or an invalid post processor, or
	 *             when a duplicate key is encountered.
	 */
	private void initWebData(Map<String, WebDataOptions> webDataConfig, boolean preventStartUp) throws InvalidConfigurationException {
		if (webDataConfig == null) {
			return;
		}

		log.info("Configuring web data sources");

		Set<String> meshKeys = new HashSet<>(meshDataKeys.values());
		JsonObject dummyData = new JsonObject().put(META_DATA_KEY, new JsonObject().put(IS_FRESH_KEY, Boolean.FALSE));

		for (Map.Entry<String, WebDataOptions> entry : webDataConfig.entrySet()) {
			String key = entry.getKey();

			if (meshKeys.contains(key)) {
				if (preventStartUp) {
					throw new InvalidConfigurationException(
						"dataProvider.webData." + key,
						"Duplicate data provider key: " + key);
				}

				log.warn("Duplicate key \"{}\", ignoring web data entry", key);

				continue;
			}

			WebDataOptions val = entry.getValue();

			if (val.getPostProcessor() != null && getPostProcessorInstance(key, val) == null) {
				throw new InvalidConfigurationException(
					"dataProvider/webData/" + key,
					"Could not instantiate post processor " + val.getPostProcessor());
			}

			log.info("  Adding URL for {}: {}", key, val.getUrl());
			webDataOptions.put(entry.getKey(), val);
			webData.put(entry.getKey(), dummyData);
		}
	}

	/**
	 * Add mesh data for the currently used branch to the routing context
	 * @param rc routing context
	 */
	private Completable addMeshData(RoutingContext rc) {
		return RxUtils.justNullable(wrap(rc).getBranch())
			.switchIfEmpty(branchProvider.findBranch(b -> Boolean.TRUE.equals(b.getLatest())))
			.doOnComplete(() -> log.debug("No suitable branch found"))
			.observeOn(RxUtils.scheduler(rc))
			.doOnSuccess(branch -> addData(rc, meshData.computeIfAbsent(branch.getUuid(), b -> createMeshDummyData())))
			.ignoreElement();
	}

	/**
	 * Adds the specified mappings to the routing context.
	 *
	 * For every configured element, a timestamp is also stored in the routing context with the key <code>ENTRY_KEY + "_ts"</code>. Since this is intended for
	 * the caching handler, this timestamp will be set to the current time when either
	 * <ul>
	 * <li>no meta data exists for the entry, or</li>
	 * <li>the meta data indicates that the last refresh failed, or</li>
	 * <li>the timestamp in the meta data is missing</li>
	 * </ul>
	 *
	 * @param rc
	 *            The current routing context
	 * @param entries
	 *            The data to be added to the routing context
	 */
	private void addData(RoutingContext rc, Map<String, JsonObject> entries) {
		for (Map.Entry<String, JsonObject> entry : entries.entrySet()) {
			String key = entry.getKey();
			JsonObject data = entry.getValue();

			if (log.isDebugEnabled()) {
				int numEntries = JsonUtils.countEntries(data);

				log.debug("  Adding \"{}\" ({} entr{})", key, numEntries, numEntries == 1 ? "y" : "ies");
			}

			if (log.isTraceEnabled()) {
				log.trace(data.encodePrettily());
			}

			JsonObject meta = data.getJsonObject(META_DATA_KEY, new JsonObject());
			long ts = meta.getLong(TIMESTAMP_KEY, -1L);

			rc.put(key, data);
			rc.put(key + "_ts", ts);

			if (!meta.getBoolean(IS_FRESH_KEY, Boolean.FALSE)) {
				log.debug("Data for \"{}\" is stale, last successful update timestamp: {}", key, ts);
			}
		}
	}

	/**
	 * Checks if the current request is to one of the pre-loaded pages, and if so overrides the pre-loaded data with the one loaded by the content path handler.
	 *
	 * @param rc
	 *            The current routing context
	 */
	private void overrideData(RoutingContext rc) {
		Object node = rc.get(RC_NODE_TO_RENDER);

		if (node instanceof JsonObject) {
			String path = wrap(rc).isPreview()
				? "/" + rc.pathParam("param0") + ((JsonObject) node).getJsonObject("fields").getString("filename")
				: rc.currentRoute().getPath();

			// NOTE: This is the reason meshDataKeys maps from path to key.
			if (meshDataKeys.containsKey(path)) {
				log.debug("Data source itself was loaded, overriding preloaded values for {}", path);
				rc.put(meshDataKeys.get(path), node);
			}
		} else {
			log.warn("No valid JSON object \"{}\" found in routing context", RC_NODE_TO_RENDER);
			log.warn("This should have been provided by the content path handler");
		}

		rc.next();
	}

	/**
	 * Checks the content of the received response against the specified content type for the datasource.
	 *
	 * @param options
	 *            The options of the currently loaded datasource
	 * @param response
	 *            The received response
	 * @param body
	 *            The body of the response
	 *
	 * @return The response body converted to the respective data type or <code>null</code> if the response does not match the configured data type.
	 */
	private Object checkWebResponse(WebDataOptions options, HttpClientResponse response, Buffer body) {
		if (body == null) {
			log.error("Missing body from {}", options.getUrl());

			return null;
		}

		Object ret = null;
		String type = response.getHeader(HttpHeaders.CONTENT_TYPE.toString());

		if (type != null) {
			// Remove encoding information if present.
			int idx = type.indexOf(";");

			if (idx >= 0) {
				type = type.substring(0, idx);
			}
		}

		switch (options.getFormat()) {
		case JSON:
			if (!HttpConstants.APPLICATION_JSON.equals(type)) {
				log.error(
					"Invalid response type {} from {}, expected {}",
					type,
					options.getUrl(),
					HttpConstants.APPLICATION_JSON);

				if (log.isTraceEnabled()) {
					log.trace("Response body:\n{}", body.toString());
				}
			} else {
				try {
					ret = JsonUtils.jsonObjectOf(body.toString());
				} catch (DecodeException e) {
					log.error("Received malformed JSON from {}", options.getUrl(), e);
				}
			}

			break;

		case RAW:
			ret = body.toString();
			break;

		default:
			log.warn("Unexpected data format {}", options.getFormat());
		}

		return ret;
	}

	/**
	 * Updates the meta data of the given data object.
	 *
	 * Currently the meta data contains a timestamp of the last successful load, and a flag whether the last refresh was successful or not.
	 *
	 * @param data
	 *            The data object to be updated
	 * @param isFresh
	 *            Flag indicating whether the last refresh was successful.
	 */
	private void updateMetaData(JsonObject data, boolean isFresh) {
		JsonObject meta = data.getJsonObject(META_DATA_KEY, new JsonObject());

		if (isFresh) {
			if (!meta.isEmpty()) {
				log.warn("Freshly loaded data entry already has attached meta data");

				if (log.isTraceEnabled()) {
					log.trace("Loaded object:\n{}", data.encodePrettily());
				} else {
					log.warn("Enable TRACE logging for more information");
				}
			}

			meta.put(TIMESTAMP_KEY, Instant.now().getEpochSecond());
		}

		meta.put(IS_FRESH_KEY, isFresh);
		data.put(META_DATA_KEY, meta);
	}

	/**
	 * Get an instance of the post processor specified by the given options.
	 *
	 * <p>
	 * The instance is retrieved from {@link #postProcessorInstances} and created if there is none yet.
	 * </p>
	 *
	 * @param key The key of the web data source in the configuration
	 * @param options The options of the web datasource to get a post processor for.
	 * @return An instance of the post processor for the given web datasource
	 */
	private DataProviderPostProcessor getPostProcessorInstance(String key, WebDataOptions options) {
		if (options.getPostProcessor() == null) {
			return null;
		}

		if (postProcessorInstances.containsKey(key)) {
			return postProcessorInstances.get(key);
		}

		DataProviderPostProcessor postProcessor;

		if (log.isDebugEnabled()) {
			log.debug("Creating new post processor instance of {} for {}", options.getPostProcessor(), key);
		}

		postProcessor = ReflectionUtils.getInstance(
			options.getPostProcessor(),
			DataProviderPostProcessor.class,
			"data provider post processor");

		if (postProcessor != null) {
			postProcessor.init(options.getOptions());
		}

		// Even if postProcessor is null we put in the map to indicate, that we already tried to instantiate it.
		postProcessorInstances.put(key, postProcessor);

		return postProcessor;
	}

	/**
	 * Store the response of a loaded web datasource in {@link #webData}.
	 *
	 * @param key
	 *            The routing context key to use
	 * @param response
	 *            The received response
	 *
	 * @return <code>true</code> if the response status is OK and it contains the expected data type, and <code>false</code> otherwise.
	 */
	private boolean processWebResponse(String key, HttpClientResponse response) {
		WebDataOptions opt = webDataOptions.get(key);

		if (response.statusCode() != HttpResponseStatus.OK.code()) {
			processError(
				null,
				key,
				opt.getUrl(),
				new Exception("Unexpected response status: " + response.statusCode()));

			return false;
		}

		response.rxBody().doOnSuccess(buf -> {
			Object body = checkWebResponse(opt, response, buf);

			if (body == null) {
				return;
			}

			DataProviderPostProcessor postProcessor = getPostProcessorInstance(key, opt);

			JsonObject data = null;

			if (postProcessor != null) {
				try {
					data = postProcessor.process(body);
				} catch (Throwable e) {
					log.error(
						"Postprocessing result for \"{}\" failed (reason: {}), falling back to JSON or plain text representation",
						key,
						e.getMessage(),
						e);
				}
			}

			if (data == null) {
				if (body instanceof JsonObject) {
					data = (JsonObject) body;
				} else {
					data = new JsonObject().put("data", body.toString());
				}
			}

			updateMetaData(data, true);

			webData.put(key, data);

			if (log.isDebugEnabled()) {
				int contentLength = Integer.parseInt(response.getHeader(HttpHeaders.CONTENT_LENGTH.toString()));

				log.debug(
					"Refreshed {} from {} ({} byte{})",
					key,
					opt.getUrl(),
					contentLength,
					contentLength == 1 ? "" : "s");
			}

			if (log.isTraceEnabled()) {
				log.trace(body.toString());
			}
		})
		.subscribe();

		return true;
	}

	/**
	 * Checks if the given response is a {@link NodeResponse} and stores the
	 * data in {@link #meshData}.
	 *
	 * @param branch
	 *            The branch
	 * @param path
	 *            The path of the loaded resource
	 * @param response
	 *            The response to store
	 */
	private boolean processMeshResponse(BranchResponse branch, String path, MeshWebrootResponse response) {
		if (response.isBinary()) {
			response.close();
			processError(branch, meshDataKeys.get(path), path, new Exception("Not a NodeResponse"));

			return false;
		}

		NodeResponse nodeResponse = response.getNodeResponse();

		if (nodeResponse == null) {
			response.close();
			log.warn("Empty NodeResponse for {}", path);

			return false;
		}

		// JsonObject.mapFrom() gets confused by the structure of the node response
		// and puts a superfluous "node" field in the "fields".
		JsonObject data = new JsonObject(nodeResponse.toJson());

		String key = meshDataKeys.get(path);

		updateMetaData(data, true);

		meshData.computeIfAbsent(branch.getUuid(), b -> new ConcurrentHashMap<>()).put(key, data);

		if (log.isDebugEnabled()) {
			int numEntries = JsonUtils.countEntries(data);

			log.debug("Refreshed {} from {} ({} entr{})", key, path, numEntries, numEntries == 1 ? "y" : "ies");
		}

		if (log.isTraceEnabled()) {
			log.trace(data.encodePrettily());
		}

		return true;
	}

	/**
	 * Marks the specified entry meta data as erroneous if it exists.
	 *
	 * @param entries
	 *            The map of entries
	 * @param key
	 *            The key of the entry to mark
	 */
	private void markErroneousEntry(Map<String, JsonObject> entries, String key) {
		JsonObject entry = entries.get(key);

		if (entry != null) {
			log.debug("Marking \"{}\" as stale", key);
			updateMetaData(entry, false);
		}
	}

	/**
	 * Logs the error and removes the corresponding URL from the list of pages to load if {@link #retryOnError} is <code>true</code>.
	 *
	 * @param branch
	 *            The branch (for mesh data, otherwise null)
	 * @param key
	 *            The key this datasource would be stored under
	 * @param url
	 *            The path of the resources which should have been loaded
	 * @param cause
	 *            The cause of the error
	 */
	private void processError(BranchResponse branch, String key, String url, Throwable cause) {
		if (cause instanceof MeshRestClientMessageException && ((MeshRestClientMessageException) cause).getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
			logFailure("Could not refresh data source \"{}\": {} in branch {}", key, url, branch.getName());
		} else {
			logFailure("Could not refresh data source \"{}\": {}", key, url, cause);
		}

		if (!retryOnError) {
			logFailure("Removing entry from list of data sources: key {}, url: {}", key, url);

			if (branch != null) {
				meshUrlBlacklist.computeIfAbsent(branch.getUuid(), k -> new HashSet<>()).add(url);
			} else {
				webDataOptions.remove(key);
			}
		} else {
			if (branch != null) {
				if (meshData.containsKey(branch.getUuid())) {
					markErroneousEntry(meshData.get(branch.getUuid()), key);
				}
			} else {
				markErroneousEntry(webData, key);
			}
		}
	}

	/**
	 * Log the given failure message with the arguments.
	 *
	 * <p>
	 *     This wrapper is intended to distinguish whether the failure should
	 *     be logged with loglevel ERROR or WARN.
	 * </p>
	 *
	 * @param fmt The Log message format string.
	 * @param args The arguments for the format string.
	 */
	private void logFailure(String fmt, Object ...args) {
		if (logFailuresAsErrors) {
			log.error(fmt, args);
		} else {
			log.warn(fmt, args);
		}
	}

	/**
	 * Add dummy values which only contain the metadata for datasource entries.
	 * @return Mapping from routing context keys to empty dummy values.
	 */
	private Map<String, JsonObject> createMeshDummyData() {
		Map<String, JsonObject> dummies = new HashMap<>();
		JsonObject dummyData = new JsonObject().put(META_DATA_KEY, new JsonObject().put(IS_FRESH_KEY, Boolean.FALSE));

		for (String key : meshDataKeys.values()) {
			dummies.put(key, dummyData);
		}

		return dummies;
	}
}
