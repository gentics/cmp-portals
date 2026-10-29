package com.gentics.mesh.portal;

/**
 * Whether the portal startup checks are performed and should succeed.
 */
public enum TestContextStartupCheckMode {
	/** Startup checks are skipped. */
	SKIP,
	/** Startup checks are performed and portal should start. */
	SHOULD_START,
	/** Startup checks are performed and portal start should be aborted. */
	SHOULD_ABORT
}
