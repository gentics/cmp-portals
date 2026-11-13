package com.gentics.mesh.portal.server.dagger;

import com.gentics.mesh.portal.api.branch.BranchProvider;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.branch.BranchProviderImpl;
import com.gentics.mesh.portal.cache.CacheProviderImpl;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.StaticHandlerOptions;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.VertxOptions;
import com.gentics.mesh.portal.monitoring.OkHttpCallStateEventListener;
import com.gentics.mesh.portal.queries.QueryStoreImpl;
import com.gentics.mesh.portal.server.CookieProviderImpl;
import com.gentics.mesh.portal.server.core.PortalInfoImpl;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientConfig;
import com.gentics.mesh.rest.client.MeshWebsocket;
import com.gentics.mesh.rest.monitoring.MonitoringClientConfig;
import com.gentics.mesh.rest.monitoring.MonitoringRestClient;
import com.gentics.mesh.rest.monitoring.impl.MonitoringOkHttpClientImpl;
import com.github.jknack.handlebars.helper.EachHelper;
import dagger.Module;
import dagger.Provides;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import io.vertx.ext.web.templ.handlebars.impl.HandlebarsTemplateEngineImpl;
import io.vertx.micrometer.MicrometerMetricsFactory;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

@Module
public class PortalModule {

	private final static String SECRET_HEADER_NAME = "FG-ACCESSTOKEN";

	private static final Logger log = LoggerFactory.getLogger(PortalModule.class);

	@Provides
	@Singleton
	public LoggerHandler loggerHandler() {
		return LoggerHandler.create();
	}

	@Provides
	@Singleton
	public static MeshRestClient meshRestClient(PortalConfig config, @CustomOkListener OkHttpClient okClient) {
		MeshOptions meshOptions = config.getMeshOptions();
		MeshRestClientConfig clientConfig = MeshRestClientConfig.newConfig()
			.setHost(meshOptions.getHost())
			.setPort(meshOptions.getPort())
			.setSsl(meshOptions.isSsl())
			.setProtocolVersion(meshOptions.getProtocolVersion())
			.build();

		MeshRestClient meshClient = MeshRestClient.create(clientConfig, okClient);
		meshClient.setAPIKey(config.getMeshOptions().getApiKey());
		return meshClient;
	}

	@Provides
	@Singleton
	public static MonitoringRestClient monitoringClient(PortalConfig config, OkHttpClient okHttpClient) {
		MeshOptions meshOptions = config.getMeshOptions();

		MonitoringClientConfig clientConfig = new MonitoringClientConfig.Builder()
			.setHost(meshOptions.getHost())
			.setPort(meshOptions.getMonitoringPort())
			.build();

		return new MonitoringOkHttpClientImpl(clientConfig, okHttpClient);
	}

	@Provides
	@Singleton
	public static OkHttpClient okClient(PortalConfig config, MetricsService metricsService, PortalInfo portalInfo) {
		return okClient(config, metricsService, true, null);
	}

	@Provides
	@Singleton
	@CustomOkListener
	public static OkHttpClient okClientCustomListener(PortalConfig config, MetricsService metricsService, PortalInfo portalInfo) {
		return okClient(config, metricsService, true, portalInfo);
	}

	@Provides
	@Singleton
	@NoRedirect
	public static OkHttpClient okClientNoRedirect(PortalConfig config, MetricsService metricsService, PortalInfo portalInfo) {
		return okClient(config, metricsService, false, null);
	}

	/**
	 * Create a new instance of the OkHttpClient.
	 * @param config portal configuration
	 * @param metricsService metrics service
	 * @param followRedirects true to follow redirects
	 * @return client instance
	 */
	protected static OkHttpClient okClient(PortalConfig config, MetricsService metricsService, boolean followRedirects, PortalInfo portalInfo) {
		Duration networkTimeout = Duration.ofSeconds(config.getNetworkTimeout());
		Dispatcher dispatcher = new Dispatcher();

		dispatcher.setMaxRequests(config.getMeshOptions().getMaxRequests());
		dispatcher.setMaxRequestsPerHost(config.getMeshOptions().getMaxRequestsPerHost());

		Builder builder = new OkHttpClient.Builder()
			.callTimeout(networkTimeout)
			.connectTimeout(networkTimeout)
			.writeTimeout(networkTimeout)
			.readTimeout(networkTimeout)
			.followRedirects(followRedirects)
			.followSslRedirects(followRedirects)
			.dispatcher(dispatcher);

		if (config.getMeshOptions().getProtocolVersion() != null) {
			switch (config.getMeshOptions().getProtocolVersion()) {
			case DEFAULT:
				builder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
				break;
			case HTTP_1_1:
				builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
				break;
			case HTTP_2:
				builder.protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE));
				break;
			default:
				break;
			}
		}

		MeterRegistry registry = metricsService.getMetricRegistry();
		OkHttpCallStateEventListener callStateListener = OkHttpCallStateEventListener
			.builder(registry, "okhttp.requests")
			.portalInfo(portalInfo)
			.maxTimeouts(config.getMonitoringOptions().getMaxTimeouts())
			.build();

		builder.eventListener(callStateListener);

		if (metricsService.isEnabled()) {
			registry.gauge("okhttp.requests.queue", Tags.of("calls", "max"), dispatcher, Dispatcher::getMaxRequests);
			registry.gauge("okhttp.requests.queue", Tags.of("calls", "maxperhost"), dispatcher, Dispatcher::getMaxRequestsPerHost);
			registry.gauge("okhttp.requests.queue", Tags.of("calls", "running"), dispatcher, Dispatcher::runningCallsCount);
			registry.gauge("okhttp.requests.queue", Tags.of("calls", "queued"), dispatcher, Dispatcher::queuedCallsCount);
		}

		return builder.build();
	}

	@Provides
	@Singleton
	public static MeshWebsocket meshWebsocket(MeshRestClient meshClient, @CustomOkListener OkHttpClient okHttpClient) {
		MeshWebsocket eventbus = meshClient.eventbus();

		eventbus.errors().subscribe(e -> {
			log.error("Eventbus error: {}", e.getMessage(), e);
			log.debug("Resetting Mesh REST client connections");

			// The OkHttp client with the @CustomOkListener qualifier is the
			// one used to create the Mesh REST client, so we evict all idle
			// connections from that client.
			okHttpClient.connectionPool().evictAll();
		});

		return eventbus;
	}

	@Provides
	@Singleton
	public static HttpClient httpClient(Vertx vertx, PortalConfig config) {
		HttpClientOptions options = new HttpClientOptions();
		MeshOptions meshOptions = config.getMeshOptions();

		options.setDefaultHost(meshOptions.getHost());
		options.setDefaultPort(meshOptions.getPort());
		options.setSsl(meshOptions.isSsl());
		options.setIdleTimeout(config.getNetworkTimeout());
		options.setConnectTimeout(config.getNetworkTimeout());

		switch (meshOptions.getProtocolVersion()) {
		case HTTP_2:
			options.setProtocolVersion(HttpVersion.HTTP_2);
			break;
		case HTTP_1_1:
			options.setProtocolVersion(HttpVersion.HTTP_1_1);
			break;
		default:
			options.setProtocolVersion(HttpClientOptions.DEFAULT_PROTOCOL_VERSION);
			break;
		}
		return vertx.createHttpClient(options);
	}

	@Provides
	@Singleton
	public static io.vertx.reactivex.core.http.HttpClient rxHttpClient(HttpClient client) {
		return io.vertx.reactivex.core.http.HttpClient.newInstance(client);
	}

	@Provides
	@Singleton
	public static HandlebarsTemplateEngine handlebarsTemplateEngine(Vertx vertx) {
		return new HandlebarsTemplateEngineImpl(vertx, ".hbs");
	}

	@Provides
	@Singleton
	public static Vertx vertx(PortalConfig config, MetricsService metricsService, MetricsOptions metricsOptions, MeterRegistry meterRegistry) {
		VertxOptions options = config.getVertxOptions();
		io.vertx.core.VertxOptions vertxOptions = new io.vertx.core.VertxOptions();

		if (options.isDevelopmentMode()) {
			log.warn("Increasing blocked thread check interval for development mode, this should not be active on productive systems");
			vertxOptions.setBlockedThreadCheckInterval(Duration.ofHours(1).toMillis());
		}

		vertxOptions
			.setWorkerPoolSize(options.getWorkerPoolSize())
			.setEventLoopPoolSize(options.getEventPoolSize());

		// Prefer epoll handling
		vertxOptions.setPreferNativeTransport(true);

		Vertx vertx;
		if (metricsService.isEnabled()) {
			log.info("Enabling metrics service: {}", metricsService.getClass().getSimpleName());

			vertxOptions.setMetricsOptions(metricsOptions);
			vertx = Vertx.builder().with(vertxOptions).withMetrics(new MicrometerMetricsFactory(meterRegistry)).build();
		} else {
			vertx = Vertx.vertx(vertxOptions);
		}

		if (vertx.isNativeTransportEnabled()) {
			log.info("Running with native transports enabled");
		} else {
			log.warn("Current environment does not support native transports");
		}
		return vertx;
	}

	@Provides
	@Singleton
	static io.vertx.reactivex.core.Vertx rxVertx(Vertx vertx) {
		return new io.vertx.reactivex.core.Vertx(vertx);
	}

	@Provides
	@Singleton
	public static CorsHandler corsHandler() {
		return CorsHandler.create().addOrigin("*").allowedHeader("Content-Type").allowedHeader(SECRET_HEADER_NAME).allowedMethod(GET).allowedMethod(POST);
	}

	@Provides
	@Singleton
	public static TemplateHandler templateHandler(HandlebarsTemplateEngine engine) {
		return TemplateHandler.create(engine);
	}

	@Provides
	public static StaticHandler staticHandler(PortalConfig config) {
		return createStaticHandler(config.getStaticHandlerOptions().getPath(), config.getStaticHandlerOptions());
	}

	/**
	 * Create a static handler serving the files in the given path, using the caching settings of the options.
	 *
	 * @param path path to the static files
	 * @param staticHandlerOptions options for caching
	 * @return static handler
	 */
	public static StaticHandler createStaticHandler(String path, StaticHandlerOptions staticHandlerOptions) {
		var handler = StaticHandler.create(path);

		handler.setDirectoryListing(false)
			.setCachingEnabled(staticHandlerOptions.isCaching())
			.setMaxAgeSeconds(staticHandlerOptions.getMaxAge());

		return handler;
	}

	@Provides
	@Singleton
	public static BranchProvider branchProvider(PortalConfig config, MeshRestClient client, MeshWebsocket eventbus) {
		return new BranchProviderImpl(config, client, eventbus);
	}

	@Provides
	@Singleton
	public static CacheProvider cacheProvider(PortalConfig config, MeshRestClientProvider clientProvider, MeshWebsocket eventbus,
		io.vertx.reactivex.core.Vertx rxVertx) {
		return new CacheProviderImpl(config, clientProvider, eventbus, rxVertx);
	}

	@Provides
	@Singleton
	public static CookieProvider cookieProvider(PortalConfig config) {
		return new CookieProviderImpl(config);
	}

	@Provides
	@Singleton
	public static PortalInfo portalInfo(PortalConfig config, Vertx vertx) {
		return new PortalInfoImpl(config, vertx);
	}

	@Provides
	public static EachHelper eachHelper() {
		return (EachHelper) EachHelper.INSTANCE;
	}

	@Provides
	public static QueryStore queryStore(PortalConfig config) {
		return new QueryStoreImpl(config);
}
}
