package com.gentics.mesh.portal.server.core;

import io.reactivex.Completable;

/**
 * Generic interface for checking various functionality
 * of Portal|Java at startup.
 *
 * @author plyhun
 *
 */
public interface PortalFunctionStartupChecker {

	/**
	 * Performs the check.
	 *
	 * @return {@code true} if check passed and {@code false} otherwise.
	 */
	boolean check();

	/**
	 * Performs the check asynchronously.
	 *
	 * <p>
	 *     Default implementation calls {@link #check()} and completes with a
	 *     generic {@link PortalStartupException} stating which check returned
	 *     {@code false}.
	 * </p>
	 *
	 * @return {@link Completable} that will complete normally when the check
	 * 		passes and complete with an error if the check fails.
	 */
	default Completable checkAsync() {
		return Completable.defer(() -> check()
			? Completable.complete()
			: Completable.error(new PortalStartupException(String.format("%s returned false", getClass().getName()))));
	}
}
