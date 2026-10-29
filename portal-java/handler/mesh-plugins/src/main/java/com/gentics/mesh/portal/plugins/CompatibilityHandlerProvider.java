package com.gentics.mesh.portal.plugins;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.core.HandlerProvider;
import com.gentics.mesh.portal.context.UrlParameterInjector;
import com.gentics.mesh.portal.util.HandlerUtils;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides search and autocomplete handlers registered to <code>/search</code> and <code>/searchAutocomplete</code>
 * respectively.
 *
 * <p>
 *     Those paths were the default values when the search was a module in the portal framework instead of a Mesh
 *     plugin.
 * </p>
 */
@Singleton
public class CompatibilityHandlerProvider {

	private static final String SEARCH_PLUGIN = "search";

	private static final Logger log = LoggerFactory.getLogger(CompatibilityHandlerProvider.class);

	private final String projectName;
	private final HandlerProvider handlerProvider;

	@Inject
	public CompatibilityHandlerProvider(PortalConfig config, HandlerProvider handlerProvider) {
		this.projectName = config.getMeshProject();
		this.handlerProvider = handlerProvider;
	}


	/**
	 * Create a {@link BasicPluginHandler} with a special {@link PluginHandler#addRoutes(Router) route} for
	 * the search plugin.
	 *
	 * @param name The name of the handler in the Mesh plugin
	 * @param portalPath The path to register the handler to
	 * @param forTesting Whether the handler should be registered for testing purposes
	 * @return A <code>PluginHandler</code> with a registered route suitable for the search plugin
	 */
	private PluginHandler createSearchPluginHandler(String name, String portalPath, boolean forTesting) {
		String meshPath = String.format("/api/%s/%s/plugins/search/%s", Constants.MESH_API_VERSION, projectName, name);

		if (log.isInfoEnabled()) {
			log.info("Creating compatibility {} handler: {} -> {}", name, portalPath, meshPath);
		}

		Handler<RoutingContext> proxy = handlerProvider.pluginProxy(
			"^" + portalPath,
			meshPath,
			true,
			new UrlParameterInjector().add(HandlerUtils::addBranchParameter),
			forTesting);

		return new BasicPluginHandler(SEARCH_PLUGIN, proxy) {
			@Override
			public void addRoutes(Router router) {
				router.route(portalPath)
					.order(RouteOrder.BEFORE_BODY_HANDLER.getOrder())
					.handler(proxy());
			}
		};
	}

	/**
	 * Create a handler for <code>/search</code>.
	 *
	 * @see #createSearchPluginHandler(String, String, boolean)
	 *
	 * @return A handler for <code>/search</code>
	 */
	public PluginHandler createSearchHandler() {
		return createSearchPluginHandler("search", "/search", false);
	}

	/**
	 * Create a handler for <code>/search</code> for tests.
	 *
	 * @see #createSearchPluginHandler(String, String, boolean)
	 *
	 * @return A handler for <code>/search</code> for tests.
	 */
	public PluginHandler createTestSearchHandler() {
		return createSearchPluginHandler("search", "/search", true);
	}

	/**
	 * Create a handler for <code>/searchAutocomplete</code>.
	 *
	 * @see #createSearchPluginHandler(String, String, boolean)
	 *
	 * @return A handler for <code>/searchAutocomplete</code>
	 */
	public PluginHandler createAutocompleteHandler() {
		return createSearchPluginHandler("autocomplete", "/searchAutocomplete", false);
	}
}
