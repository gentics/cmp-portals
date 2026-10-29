package com.gentics.mesh.portal.plugins;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.core.HandlerProvider;
import com.gentics.mesh.portal.context.UrlParameterInjector;
import com.gentics.mesh.portal.util.HandlerUtils;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Basic handler for forwarding requests to Mesh plugins.
 *
 * <p>
 *     This implementation will only register the route <code>/api/PLUGIN_NAME/*</code> with the
 *     {@link com.gentics.mesh.portal.api.RouteOrder#API_ROUTES API_ROUTES} route order. It will
 *     forward all requests from <code>/api/PLUGIN_NAME/*</code> to Mesh with the path
 *     <code>/api/v2/PROJECT_NAME/plugins/PLUGIN_NAME/*</code>, where <code>PLUGIN_NAME</code> is the result of
 *     {@link #name()} and <code>PROJECT_NAME</code> is the configured
 *     {@link PortalConfig#getMeshProject() mesh project}.
 * </p>
 */
public class BasicPluginHandler implements PluginHandler {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	/** The name of the Mesh plugin. */
	private final String pluginName;

	/** The proxy handler that will forward all requests to Mesh. */
	private final Handler<RoutingContext> proxy;

	/**
	 * Default constructor.
	 *
	 * @param pluginName The name of the plugin to send proxy requests to
	 * @param config The portal configuration
	 * @param allowAdminClient Whether the proxy should use the admin client credentials when no authentication
	 * 		cookie is present
	 * @param handlerProvider The provider for the proxy handler
	 */
	public BasicPluginHandler(
			String pluginName,
			PortalConfig config,
			boolean allowAdminClient,
			HandlerProvider handlerProvider) {
		this.pluginName = pluginName;

		String portalPath = String.format("^/api/%s/", pluginName);
		String meshPath = String.format(
			"/api/%s/%s/plugins/%s/",
			Constants.MESH_API_VERSION,
			config.getMeshProject(),
			pluginName);

		this.proxy = handlerProvider.pluginProxy(
			portalPath,
			meshPath,
			allowAdminClient,
			new UrlParameterInjector().add(HandlerUtils::addBranchParameter),
			false);

		if (log.isInfoEnabled()) {
			log.info(
				"Handler for Mesh plugin \"{}\" created: {} -> {}",
				pluginName,
				portalPath.substring(1),
				meshPath);
		}
	}

	/**
	 * Setup this basic handler for the given plugin name and proxy.
	 *
	 * <p>
	 *     This constructor is meant for sub classes which need more complex forwarding rules, and therefore create
	 *     their own proxies.
	 * </p>
	 *
	 * @param pluginName The name of the plugin to send proxy requests to
	 * @param proxy The proxy to use for this plugin handler
	 */
	public BasicPluginHandler(String pluginName, Handler<RoutingContext> proxy) {
		this.pluginName = pluginName;
		this.proxy = proxy;
	}


	/**
	 * Provides a proxy handler that will forward <code>/api/PLUGIN_NAME/*</code> to
	 * <code>/api/v2/PROJECT_NAME/plugins/PLUGIN_NAME/*</code>.
	 *
	 * @return A proxy handler to foward a request to Mesh
	 */
	@Override
	public Handler<RoutingContext> proxy() {
		return proxy;
	}

	/**
	 * Get the name of the Mesh plugin this handler forwards to.
	 *
	 * @return The name of the Mesh plugin this handler forwards to
	 */
	@Override
	public String name() {
		return pluginName;
	}
}
