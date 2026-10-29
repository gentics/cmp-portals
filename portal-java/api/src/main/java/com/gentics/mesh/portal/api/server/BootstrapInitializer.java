package com.gentics.mesh.portal.api.server;

import java.util.function.Consumer;

import io.vertx.ext.web.Router;

/**
 * Initializer which contains the logic that is needed to deploy all verticles and also register all custom handlers. If you write custom handlers those should
 * be injected and used in the implementation.
 */
public interface BootstrapInitializer {

	public static <T> void NOOP_CONSUMER(T r) {
	}

	/**
	 * Start the portal.
	 * 
	 * @throws Exception
	 */
	void start() throws Exception;

	/**
	 * Stop the portal.
	 * 
	 * @throws Exception
	 */
	void stop() throws Exception;

	/**
	 * Deploy multiple http verticles and register the given handlers.
	 * 
	 * @param routeInjector
	 * @throws Exception
	 */
	void deployHttpVerticles(Consumer<Router> routeInjector) throws Exception;

	/**
	 * Deploy multiple http verticles.
	 * 
	 * @throws Exception
	 */
	default void deployHttpVerticles() throws Exception {
		deployHttpVerticles(BootstrapInitializer::NOOP_CONSUMER);
	}
}
