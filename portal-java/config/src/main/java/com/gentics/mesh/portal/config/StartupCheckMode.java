package com.gentics.mesh.portal.config;

/**
 * Options how to handle portal start-up checks.
 */
public enum StartupCheckMode {
	/** No start-up checks will be performed. */
	OFF,
	/** A failing start-up check will abort the portal start. */
	ABORT,
	/** A failing start-up check will cause the /health/ready endpoint to report that the portal is not ready. */
	UNREADY
}
