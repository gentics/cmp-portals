package com.gentics.mesh.portal.config;

/**
 * How to send the API token to Mesh.
 */
public enum MeshAuthenticationMode {
	/** Set the {@code Authentication} HTTP header and the {@code mesh.token} cookie. */
	BOTH,
	/** Set only the {@code mesh.token} cookie. */
	COOKIE,
	/** Set only the {@code Authentication} HTTP header. */
	HEADER
}
