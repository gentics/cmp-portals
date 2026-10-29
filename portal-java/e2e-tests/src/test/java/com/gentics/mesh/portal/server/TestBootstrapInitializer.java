package com.gentics.mesh.portal.server;

import static io.vertx.core.http.HttpMethod.POST;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.gentics.mesh.portal.plugins.CompatibilityHandlerProvider;
import org.apache.commons.lang.StringUtils;

import com.gentics.mesh.portal.DataProviderVerticle;
import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.authentication.oauth2.KeycloakAuthenticationHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.content.RedirectHandler;
import com.gentics.mesh.portal.context.UrlParameterInjector;
import com.gentics.mesh.portal.monitoring.MonitoringServiceVerticle;
import com.gentics.mesh.portal.server.core.AbstractBootstrapInitializer;
import com.gentics.mesh.portal.server.core.HandlerProvider;
import com.gentics.mesh.portal.server.core.PortalHttpVerticle;
import com.gentics.mesh.portal.server.dagger.CustomOkListener;
import com.gentics.mesh.rest.client.MeshRestClient;

import dagger.Lazy;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Observable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import okhttp3.OkHttpClient;

/**
 * Deployes the {@link PortalHttpVerticle PortalHttpVerticles} without any special routes, as well as the {@link DataProviderVerticle} and the
 * {@link MonitoringServiceVerticle}.
 */
@Singleton
public class TestBootstrapInitializer extends AbstractBootstrapInitializer {

	private static final Logger log = LoggerFactory.getLogger(TestBootstrapInitializer.class);

	private final HandlerProvider handlerProvider;
	private final DataProviderVerticle dataProvider;
	private final MonitoringServiceVerticle monitoringService;
	private final OkHttpClient okHttpClient;
	private final RedirectHandler redirectHandler;
	private final EncryptionService encryptionService;
	private final Lazy<KeycloakAuthenticationHandler> keycloakAuthHandler;
	private final CompatibilityHandlerProvider compatibilityHandlerProvider;
	private final HandlebarsTemplateEngine engine;

	private boolean running = false;

	@Inject
	public TestBootstrapInitializer(
			PortalConfig config,
			Vertx vertx,
			Provider<PortalHttpVerticle> verticleProvider,
			MeshRestClient meshClient,
			HandlerProvider handlerProvider,
			DataProviderVerticle dataProvider,
			MonitoringServiceVerticle monitoringService,
			@CustomOkListener OkHttpClient okHttpClient,
			RedirectHandler redirectHandler,
			EncryptionService encryptionService,
			Lazy<KeycloakAuthenticationHandler> keycloakAuthHandler,
			HandlebarsTemplateEngine engine,
			CompatibilityHandlerProvider compatibilityHandlerProvider) {
		super(config, vertx, verticleProvider, meshClient, handlerProvider);

		this.handlerProvider = handlerProvider;
		this.dataProvider = dataProvider;
		this.monitoringService = monitoringService;
		this.okHttpClient = okHttpClient;
		this.redirectHandler = redirectHandler;
		this.encryptionService = encryptionService;
		this.keycloakAuthHandler = keycloakAuthHandler;
		this.engine = engine;
		this.compatibilityHandlerProvider = compatibilityHandlerProvider;
	}

	@Override
	public void start() {
		running = true;

		log.info("Starting Gentics Mesh Portal");

		vertx.deployVerticle(dataProvider, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).andThen(deploymentHandler);
		vertx.deployVerticle(monitoringService).andThen(deploymentHandler);

		deployHttpVerticles(router -> {
			Handler<RoutingContext> echoHandler = handlerProvider.pluginProxy(
				"/api/proxy/echo",
				"/echo",
				false,
				new UrlParameterInjector(),
				true);

			Handler<RoutingContext> redirectHandler = handlerProvider.pluginProxy(
					"/api/proxy/redirect",
					"/redirect",
					false,
					new UrlParameterInjector(),
					true);
			Handler<RoutingContext> formsProxy = handlerProvider.pluginProxy("^/api/forms/", "/api/v1/%s/plugins/forms/".formatted(config.getMeshProject()));

			router.route("/api/echo")
				.method(HttpMethod.POST)
				.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
				.handler(this::handleEcho);

			router.route("/api/proxy/echo")
				.method(HttpMethod.POST)
				.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
				.handler(echoHandler);

			router.route("/api/proxy/redirect")
				.method(HttpMethod.GET)
				.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
				.handler(redirectHandler);

			router.routeWithRegex("/api/proxytest/(.*)").order(RouteOrder.BEFORE_BODY_HANDLER.getOrder()).handler(
				handlerProvider.pluginProxy("^/api/proxytest", "/api/v1/%s/".formatted(config.getMeshProject()), false, new UrlParameterInjector(), true));

			router.route("/api/meshInfo")
				.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
				.handler(this::handleMeshInfo);

			router.route("/api/authenticated")
				.order(RouteOrder.API_ROUTES.getOrder())
				.handler(this::handleAuthInfo);

			// methods for the 3rd gen form management
			router.routeWithRegex("/api/forms/form/([^/]*)/(load|validate|save|submit)")
				.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
				.method(POST)
				.handler(formsProxy);

			if (StringUtils.isNotBlank(config.getAuthOptions().getKeycloakOptions().getRealmUrl())) {
				keycloakAuthHandler.get().addRoutes(router);
			}

			compatibilityHandlerProvider.createTestSearchHandler().addRoutes(router);
		});
	}

	@Override
	public void stop() {
		log.info("Stopping Gentics Mesh Portal");

		handlerProvider.okHttpClient().dispatcher().cancelAll();
		handlerProvider.cachingHandler().disable();
		handlerProvider.meshWebsocket().close();

		Observable.fromIterable(vertx.deploymentIDs())
			.flatMapCompletable(id -> rxVertx().rxUndeploy(id))
			.andThen(rxVertx().rxClose())
			.blockingAwait(10, TimeUnit.SECONDS);

		handlerProvider.okHttpClient().dispatcher().executorService().shutdown();
		handlerProvider.okHttpClient().connectionPool().evictAll();
		running = false;
	}

	/**
	 * Check if Portal | Java is currently running.
	 * 
	 * @return
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Get the OkHttpClient instance, which is used by the portal for communication with Mesh
	 * @return client
	 */
	public OkHttpClient getOkHttpClient() {
		return okHttpClient;
	}

	/**
	 * Get the redirect handler instance
	 * @return redirect handler
	 */
	public RedirectHandler getRedirectHandler() {
		return redirectHandler;
	}

	/**
	 * Get the data provider instance
	 * @return data provider
	 */
	public DataProviderVerticle getDataProvider() {
		return dataProvider;
	}

	/**
	 * Get the encryption service.
	 * @return The encryption service.
	 */
	public EncryptionService getEncryptionService() {
		return encryptionService;
	}

	/**
	 * Get the keycloak auth handler.
	 * @return
	 */
	public KeycloakAuthenticationHandler getKeycloakAuthenticationHandler() {
		return keycloakAuthHandler.get();
	}

	/**
	 * Get the Handlebars templating engine
	 * @return
	 */
	public HandlebarsTemplateEngine getHandlebarsTemplateEngine() {
		return engine;
	}

	private void handleEcho(RoutingContext rc) {
		HttpServerRequest request = rc.request();
		int contentLength = Integer.parseInt(request.getHeader(HttpHeaders.CONTENT_LENGTH));
		Buffer content = Buffer.buffer(contentLength);

		System.out.printf("[%s] Handling echo request\n", Thread.currentThread().getName());

		request.handler(b -> {
			int numBytes = b.length();

			content.appendBuffer(b);

			System.out.printf(
				"[%s] Received %d byte%s (total: %d)\n",
				Thread.currentThread().getName(),
				numBytes,
				numBytes == 1 ? "" : "s",
				content.length());
		});

		request.endHandler(ignored -> {
			System.out.printf("[%s] Request finished, received %d bytes\n", Thread.currentThread().getName(), content.length());

			rc.response()
				.setStatusCode(HttpResponseStatus.OK.code())
				.end("ECHO: " + content);
		});
	}

	private void handleMeshInfo(RoutingContext rc) {
		meshClient.getApiInfo().toSingle()
			.subscribe(
				rs -> rc.response().end(rs.toJson()),
				error -> {
					String response = new JsonObject()
						.put("meshError", error.getMessage())
						.encodePrettily();

					rc.response()
						.setStatusCode(HttpResponseStatus.GATEWAY_TIMEOUT.code())
						.end(response);
				});
	}

	private void handleAuthInfo(RoutingContext rc) {
		rc.response()
			.setStatusCode(rc.userContext().authenticated() ? HttpResponseStatus.OK.code() : HttpResponseStatus.UNAUTHORIZED.code())
			.end();
	}
}
