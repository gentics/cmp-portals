package com.gentics.mesh.portal.sitemap;

import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.parameter.client.VersioningParametersImpl;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.mesh.portal.util.RxUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * A handler to create XML sitemaps with {@link SitemapGenerator}.
 */
@Singleton
public class SitemapHandler extends AbstractBaseHandler {

	private static final Logger log = LoggerFactory.getLogger(SitemapHandler.class);

	private final QueryStore queryStore;
	private final String projectName;
	private final SitemapGenerator generator;
	private final MeshRestClientProvider meshRestClientProvider;

	@Inject
	public SitemapHandler(PortalConfig portalConfig, QueryStore queryStore, MeshRestClientProvider meshRestClientProvider) {
		this.queryStore = queryStore;
		this.projectName = portalConfig.getMeshProject();
		this.generator = new SitemapGenerator(portalConfig);
		this.meshRestClientProvider = meshRestClientProvider;
	}

	@Override
	public void addRoutes(Router router) {
		router.route("/sitemap.xml")
			.order(RouteOrder.API_ROUTES.getOrder())
			.handler(this)
			.failureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()));
	}

	/**
	 * Get the sitemap via GraphQL and transform to XML.
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		GraphQLRequest request = new GraphQLRequest().setQuery(queryStore.getSitemapQuery())
			.setVariables(new JsonObject().
				put("lang", Arrays.asList("de", "en")));

		meshRestClientProvider.getDefaultClient(rc)
			.graphql(projectName, request, new VersioningParametersImpl().setVersion("published")).toSingle()
			.observeOn(RxUtils.scheduler(rc))
			.map(errorLogger)
			.subscribe(
				response -> generateSitemap(rc, response),
				error -> handleError(rc, error));
	}

	/**
	 * Generate the <em>sitemap.xml</em> from the GraphQL response.
	 *
	 * <p>
	 *     When the response is empty, or cannot be transformed the response will be a
	 *     <em>500 Internal Server Error</em>.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param response The GraphQL response with the sitemap data
	 */
	private void generateSitemap(RoutingContext rc, GraphQLResponse response) {
		JsonObject data = response.getData();

		if (data == null) {
			log.error("Loading information for sitemap failed");
			rc.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

			return;
		}

		try {
			List<String> sitemap = generator.generateSitemap(data);

			rc.response().putHeader(CONTENT_TYPE, "text/xml");
			rc.response().end(String.join(" ", sitemap));
		} catch (MalformedURLException e) {
			log.error("Sitemap generation failed: {}", e.getMessage(), e);
			rc.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
		}
	}

	/**
	 * Logs the error and fails the request with <em>500 Internal Server Error</em>.
	 *
	 * @param rc The current routing context
	 * @param error The error
	 */
	private void handleError(RoutingContext rc, Throwable error) {
		log.error("Loading information for sitemap failed: {}", error.getMessage(), error);
		rc.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
	}
}
