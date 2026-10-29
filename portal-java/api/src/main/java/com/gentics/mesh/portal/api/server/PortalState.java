package com.gentics.mesh.portal.api.server;

/**
 * Different states of the portal.
 */
public enum PortalState {
	/** The portal is still starting up. */
	STARTING,
	/** All HTTP verticles have been deployed and the portal is ready. */
	READY,
	/** An IO error happened when trying to connect to Mesh. */
	NETWORK_ERROR,
	/** One of the portal start-up checks failed. */
	STARTUP_CHECK_ERROR,
	/** The portal is currently shutting down. */
	STOPPING
}
