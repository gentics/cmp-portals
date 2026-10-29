package com.gentics.mesh.portal.api.server;

/**
 * Miscellaneous information about the current state of the portal.
 *
 * <p>
 *     Currently the only available info is the startup state of the portal.
 * </p>
 */
public interface PortalInfo {

	/**
	 * The current state of the portal.
	 *
	 * @return The current state of the portal
	 */
	PortalState getState();

	/**
	 * Set the current state directly.
	 * @param state The new portal state.
	 */
	void setState(PortalState state);

	/**
	 * Check if the portal state should be changed because of the given error.
	 *
	 * <p>Currently this check only tests if the given error is a
	 * {@link java.net.SocketException} or a {@link java.net.SocketTimeoutException},
	 * and if so sets the state to {@link PortalState#NETWORK_ERROR}.
	 * </p>
	 *
	 * @param error That error that occured
	 */
	void checkState(Throwable error);

	/**
	 * Reset the portal state back to {@link PortalState#READY} when it was
	 * in an error state caused by network errors.
	 */
	// TODO: Rename to resetNetworkErrors() on next feature release.
	void resetErrors();

	/**
	 * Convenience method to check if the portal startup is complete.
	 *
	 * @return <code>true</code> when the portal startup is complete, and <code>false</code> otherwise.
	 */
	default boolean ready() {
		return getState() == PortalState.READY;
	}

	/**
	 * Tell the portal info that the portal is about to shut down.
	 */
	void shutdown();
}
