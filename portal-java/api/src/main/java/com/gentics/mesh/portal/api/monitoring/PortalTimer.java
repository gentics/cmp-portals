package com.gentics.mesh.portal.api.monitoring;

import java.util.function.Supplier;

/**
 * A wrapper for {@link com.codahale.metrics.Timer.Context} which checks if
 * monitoring is enabled before any action is performed.
 */
public interface PortalTimer extends AutoCloseable {

	/**
	 * Start the created timer.
	 *
	 * @return A reference to <code>this</code> for fluent API use
	 */
	PortalTimer start();

	/**
	 * Stop the wrapped {@link com.codahale.metrics.Timer.Context}, and log the provided message.
	 *
	 * <p>
	 *     A <code>{}</code> in the message will be replaced by the stopped duration.
	 * </p>
	 *
	 * @param message The message to log on stopping the timer
	 * @return The stopped duration
	 */
	long stop(Supplier<String> message);

	/**
	 * Stop the wrapped {@link com.codahale.metrics.Timer.Context}, and log the provided message.
	 *
	 * <p>
	 *     A <code>{}</code> in the message will be replaced by the stopped duration.
	 * </p>
	 *
	 * @param message The message to log on stopping the timer
	 * @return The stopped duration
	 */
	long stop(String message);

	/**
	 * Stop the wrapped {@link com.codahale.metrics.Timer.Context}.
	 *
	 * @return The stopped duration
	 */
	default long stop() {
		return stop((String) null);
	}

	/**
	 * Stops the wrapped {@link com.codahale.metrics.Timer.Context}.
	 */
	default void close() {
		stop();
	}
}
