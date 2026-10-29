package com.gentics.mesh.portal.error;

import com.gentics.mesh.rest.client.MeshRestClientMessageException;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Logs errors unless they are explicitly silenced like HTTP status 4xx errors.
 */
@Singleton
public class ErrorHandler implements Handler<RoutingContext> {

	private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

	@Inject
	public ErrorHandler() {
	}

	/**
	 * Logs the reason for the failed request with {@link Logger#error(Object)}
	 * unless it is explicitly {@link #isSilentError(RoutingContext) silenced}.
	 *
	 * <p>
	 *     Silenced errors will be logged with loglevel TRACE.
	 * </p>
	 *
	 * @param rc The current routing context.
	 */
	@Override
	public void handle(RoutingContext rc) {
		if (isSilentError(rc)) {
			log.trace("Silenced error: ", rc.failure());
		} else if (rc.failed()) {
			String path = toPath(rc);
			Throwable failure = rc.failure();

			if (rc.response().ended()) {
				log.warn("Cannot handle erroneous request for {} ({}): response has already been sent", toPath(rc), failure != null ? failure.getMessage() : "N/A");

				return;
			} else if (failure instanceof MeshRestClientMessageException) {
				MeshRestClientMessageException err = (MeshRestClientMessageException) failure;
				log.error("Mesh client error for request: {} with message {}", path, err.getMessage(), err);
			} else {
				log.error("Error for request: {}", path, rc.failure());
			}
		}

		rc.next();
	}

	/**
	 * Checks if the error in the current request should be silenced.
	 *
	 * <p>
	 *     Error logging is silenced when one of these conditions apply:
	 *     <ol>
	 *         <li>The failure cause is {@code null}</li>
	 *         <li>The failure cause message is "{@code Response is closed}"</li>
	 *         <li>The failure cause is a {@link MeshRestClientMessageException}, and its status is a 4xx HTTP status</li>
	 *     </ol>
	 * </p>
	 *
	 * @param rc The current routing context.
	 * @return {@code true} when the error should be silenced, and {@code false} otherwise.
	 */
	private boolean isSilentError(RoutingContext rc) {
		Throwable cause = rc.failure();

		if (cause == null) {
			int status = rc.statusCode();

			if (status > 0) {
				return status >= 400 && status < 500;
			}

			return false;
		}

		// Suppress errors when the response is already closed.
		if ("Response is closed".equals(cause.getMessage())) {
			return true;
		}

		// Suppress 4xx errors.
		if (cause instanceof MeshRestClientMessageException) {
			int status = ((MeshRestClientMessageException) cause).getStatusCode();

			if (status >= 400 && status < 500) {
				return true;
			}
		}

		// Everything else is not silent for now.
		return false;
	}

	/**
	 * Creates the complete request path including query parameters.
	 *
	 * @param rc The current routing context
	 * @return The complete request path including query parameters.
	 */
	private String toPath(RoutingContext rc) {
		StringBuilder b = new StringBuilder();
		b.append(rc.normalizedPath());
		String query = rc.request().query();
		if (query != null) {
			b.append("?").append(rc.request().query());
		}
		return b.toString();
	}

}
