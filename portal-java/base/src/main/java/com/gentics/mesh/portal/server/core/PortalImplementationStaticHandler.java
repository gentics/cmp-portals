package com.gentics.mesh.portal.server.core;

import java.util.concurrent.atomic.AtomicReference;

import com.gentics.mesh.portal.api.PortalEvents;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.StaticHandlerOptions;
import com.gentics.mesh.portal.server.dagger.PortalModule;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * Serves the static files of the portal implementation from {@link StaticHandlerOptions#getImplementationPath()}.
 *
 * <p>
 *     Requests for files which do not exist in that path are passed on to the next handler, which serves the static
 *     files of the portal instance. So when both contain the same path, the file of the portal implementation is
 *     served.
 * </p>
 *
 * <p>
 *     The {@link StaticHandler} caches file properties (including missing files), when caching is enabled. Since
 *     these cache entries can not be cleared, {@link #rebuild()} replaces the handler, which must be called after
 *     each {@link PortalEvents#PORTAL_IMPLEMENTATION_UPDATED update of the portal implementation}.
 * </p>
 */
public class PortalImplementationStaticHandler implements Handler<RoutingContext> {

	private final String path;
	private final StaticHandlerOptions options;
	private final AtomicReference<StaticHandler> delegate = new AtomicReference<>();

	/**
	 * Create an instance for the configured path and caching settings.
	 *
	 * @param config portal configuration
	 */
	public PortalImplementationStaticHandler(PortalConfig config) {
		this.options = config.getStaticHandlerOptions();
		this.path = options.getImplementationPath();
		rebuild();
	}

	@Override
	public void handle(RoutingContext rc) {
		delegate.get().handle(rc);
	}

	/**
	 * Replace the static handler with a new one, so that no outdated cache entries are used.
	 */
	public void rebuild() {
		delegate.set(PortalModule.createStaticHandler(path, options));
	}
}
