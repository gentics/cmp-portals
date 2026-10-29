package com.gentics.mesh.portal.api.server;

import com.gentics.mesh.portal.api.PortalEvents;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.NotImplementedException;

/**
 * Base class for verticles which start a server.
 */
public class PortalServerVerticle extends AbstractVerticle {

	private static final Logger log = LoggerFactory.getLogger(PortalServerVerticle.class);

	/** The actual port the server listens on. */
	private int actualPort;

	/**
	 * Prevent the synchronous start method from being called.
	 *
	 * <p>
	 *     All server verticles must implement {@link io.vertx.core.Verticle#start(Future)} and call
	 *     {@link #deploymentComplete(int)} with the actual server port when it is deployed.
	 * </p>
	 */
	@Override
	public void start() {
		// Server verticle _must_ implement Verticle#start(Future<Void>)
		throw new NotImplementedException();
	}

	/**
	 * Sets {@link #actualPort} and publishes the {@link PortalEvents#SERVER_VERTICLE_DEPLOYED} event.
	 *
	 * @param port The actual port the server is listening on.
	 */
	protected void deploymentComplete(int port) {
		if (log.isInfoEnabled()) {
			log.info("Deployment of {} complete", getClass().getSimpleName());
		}

		JsonObject data = new JsonObject()
			.put("id", deploymentID())
			.put("verticle", getClass().getName())
			.put("port", port);

		actualPort = port;

		if (log.isDebugEnabled()) {
			log.debug(
			"Sending event {} on Vert.x instance {}: {}",
				PortalEvents.SERVER_VERTICLE_DEPLOYED,
				vertx.toString(),
				data.encode());
		}

		vertx.eventBus().publish(PortalEvents.SERVER_VERTICLE_DEPLOYED.address(), data);
	}

	/**
	 * Get the actual port the server is listening on.
	 *
	 * @return The actual port the server is listening on
	 */
	public int actualPort() {
		return actualPort;
	}
}
