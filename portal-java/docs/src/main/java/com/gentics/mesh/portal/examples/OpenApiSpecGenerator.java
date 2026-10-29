package com.gentics.mesh.portal.examples;

import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Provider;

import com.gentics.mesh.portal.server.core.FormGenStaticHandler;
import org.apache.commons.io.FileUtils;

import com.gentics.mesh.portal.DataProviderVerticle;
import com.gentics.mesh.portal.api.PortalVersion;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.applications.PortalApplicationHandler;
import com.gentics.mesh.portal.branch.QueryParameterBranchSelector;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
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
import com.gentics.mesh.portal.openapi.PortalOpenAPIv3Generator;
import com.gentics.mesh.portal.search.SearchHandler;
import com.gentics.mesh.portal.server.core.PortalHttpVerticle;
import com.gentics.mesh.portal.sitemap.SitemapHandler;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.vertx.openapi.model.Format;
import com.gentics.vertx.openapi.model.OpenAPIGenerationException;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;

/**
 * Builds Portal | Java's route table offline - without starting an HTTP server, binding a socket, or
 * contacting a Mesh backend - and renders it into an OpenAPI v3 document.
 *
 * <p>
 * This is the Portal | Java analogue of Gentics Mesh's compile-time {@code OpenAPIMockAPIGenerator}: instead of
 * exercising the real Dagger-wired application (which would require a reachable Mesh server, Keycloak, etc., as
 * done by the e2e test suite), every handler that {@link PortalHttpVerticle#addRoutes(Router)} does not itself
 * call {@code addRoutes} on is replaced with a Mockito mock. Route registration (unlike request handling) never
 * invokes those collaborators, so the mocks are never exercised - only their identity as a route target matters.
 * </p>
 *
 * <p>
 * Handlers whose own {@code addRoutes} contributes interesting, documented routes ({@link SitemapHandler},
 * {@link SearchHandler}, {@link CachingHandler}, {@link ContentLanguageHandler}, {@link PoweredByHandler},
 * {@link AdditionalHeaderHandler}, {@link OpenApiHandler}) are constructed for real, with only their own
 * external boundary dependencies (Mesh REST clients, cache/metrics providers, cookie storage) mocked out.
 * </p>
 */
public class OpenApiSpecGenerator {

	private final PortalConfig config;
	private final PortalOpenAPIv3Generator generator;

	public OpenApiSpecGenerator() throws MalformedURLException {
		this.config = defaultConfig();
		this.generator = new PortalOpenAPIv3Generator(PortalVersion.plainVersion(), config.getServerUrl());
	}

	/**
	 * Generate the OpenAPI document for the current route table.
	 *
	 * @param format
	 *            Output format
	 * @return The rendered OpenAPI document
	 */
	public String generate(Format format) throws IOException, OpenAPIGenerationException {
		// A real (but otherwise idle) Vertx instance is used instead of a mock: it never binds a socket or talks
		// to the network, but some Vert.x-internal collaborators built while assembling the verticle - notably
		// HandlebarsTemplateEngine, which eagerly touches Vertx#sharedData() - do not tolerate a bare mock.
		Vertx vertx = Vertx.vertx();

		try {
			Router router = Router.router(vertx);

			buildVerticle(vertx).addRoutes(router);

			Map<Router, String> routerRepo = new HashMap<>();
			routerRepo.put(router, "");

			return generator.generate("Gentics Portal|Java", routerRepo, format, false, false, Optional.empty(), Optional.empty());
		} finally {
			vertx.close();
		}
	}

	/**
	 * A minimal but valid configuration, analogous to what Mesh's {@code ExampleGenerator} uses to create a
	 * default {@code server.yml}: just enough for every getter touched while routes are being registered
	 * (as opposed to while a request is being handled) to return a sane, non-null value.
	 */
	private static PortalConfig defaultConfig() throws MalformedURLException {
		PortalConfig config = new PortalConfig();

		config.setMeshProject("openapi-spec-generation");
		config.setServerUrl(new URL("http://localhost"));

		// Caching is opt-in behaviour backed by an external cache implementation; since none is configured here,
		// and the one route it registers is hidden from the OpenAPI document in any case, leave it disabled
		// rather than deep-stubbing a fake CacheProvider.
		config.getCachingOptions().setEnabled(false);

		return config;
	}

	/**
	 * Construct a {@link PortalHttpVerticle} with all of its route-registering collaborators wired for real, and
	 * everything else (handlers that are only ever used as opaque route targets during registration) mocked.
	 */
	private PortalHttpVerticle buildVerticle(Vertx vertx) {
		SecurityHandler securityHandler = new SecurityHandler(config);
		HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create(vertx);

		CookieProvider cookieProvider = mock(CookieProvider.class);
		CacheProvider cacheProvider = mock(CacheProvider.class);
		MetricsService metricsService = mock(MetricsService.class);
		CachingHandler cachingHandler = new CachingHandler(vertx, config, cacheProvider, metricsService);

		ContentLanguageHandler contentLanguageHandler = new ContentLanguageHandler(cookieProvider);

		QueryStore queryStore = mock(QueryStore.class);
		MeshRestClientProvider meshRestClientProvider = mock(MeshRestClientProvider.class);
		SitemapHandler sitemapHandler = new SitemapHandler(config, queryStore, meshRestClientProvider);

		MeshRestClient adminClient = mock(MeshRestClient.class);
		SearchHandler searchHandler = new SearchHandler(config, engine, adminClient);

		PoweredByHandler poweredByHandler = new PoweredByHandler();
		AdditionalHeaderHandler additionalHeaderHandler = new AdditionalHeaderHandler(config);
		OpenApiHandler openApiHandler = new OpenApiHandler(securityHandler, config);

		Provider<StaticHandler> staticHandlerProvider = StaticHandler::create;

		return new PortalHttpVerticle(
			mock(PortalApplicationHandler.class),
			mock(QueryParameterBranchSelector.class),
			cacheProvider,
			cachingHandler,
			config,
			mock(ContentPathHandler.class),
			contentLanguageHandler,
			mock(CroppingGisHandler.class),
			mock(DataProviderVerticle.class),
			engine,
			mock(ErrorHandler.class),
			mock(GisHandler.class),
			LoggerHandler.create(),
			mock(NavigationHandler.class),
			poweredByHandler,
			mock(ContentPreviewHandler.class),
			mock(RedirectHandler.class),
			sitemapHandler,
			staticHandlerProvider,
			new FormGenStaticHandler(config),
			mock(TemplateHandler.class),
			additionalHeaderHandler,
			cookieProvider,
			securityHandler,
			searchHandler,
			openApiHandler);
	}
	/**
	 * Build-time entry point which renders Portal | Java's current route table into an {@code openapi.yaml} file.
	 *
	 * <p>
	 * Invoked via {@code exec-maven-plugin} from a downstream module (one that depends on this one, so that this
	 * class is already compiled by the time it runs) at the {@code generate-resources} phase, before the OpenAPI
	 * document is consumed by {@code openapi-generator-maven-plugin} to produce a typed Java client.
	 * </p>
	 */

	private static final long SPEC_AGE_MILLIS = 1000 * 60 * 5;

	/**
	 * @param args
	 *            args[0]: output folder for the generated {@code api/openapi.yaml}
	 */
	public static void main(String[] args) throws Exception {
		if (args == null || args.length < 1) {
			System.err.println("PortalOpenApiSpecGenerator: no output folder provided");
			return;
		}

		File outputFolder = new File("target", args.length > 0 ? args[0] : "output/api");
		File openapiYaml = new File(outputFolder, "openapi.yaml");

		if (outputFolder.exists() && openapiYaml.exists()
			&& (System.currentTimeMillis() - openapiYaml.lastModified()) < SPEC_AGE_MILLIS) {
			System.out.println("PortalOpenApiSpecGenerator: openapi.yaml is fresh enough, no generation required");
			return;
		}

		outputFolder.mkdirs();

		String yaml = new OpenApiSpecGenerator().generate(Format.YAML);

		FileUtils.writeStringToFile(openapiYaml, yaml, StandardCharsets.UTF_8);
		System.out.println("PortalOpenApiSpecGenerator: wrote " + openapiYaml.getPath());
	}
}
