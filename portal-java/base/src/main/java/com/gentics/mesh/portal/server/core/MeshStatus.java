package com.gentics.mesh.portal.server.core;

/**
 * Mesh stati for startup check.
 */
enum MeshStatus {
	/** Mesh returned an empty list of plugins. */
	UNKNOWN,
	/** The portal does not use the correct API key. */
	FORBIDDEN,
	/** Mesh is not reachable, or required plugins are missing, or not all plugins are fully initialized. */
	NOT_READY,
	/** Mesh is reachable, all plugins are fully initialized, and no required plugins are missing. */
	READY
}
