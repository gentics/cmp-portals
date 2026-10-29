package com.gentics.mesh.portal.context;

/**
 * Trust level for redirect URLs or paths.
 */
public enum RedirectMode {
	/**
	 * The redirect is either loaded from the portal configuration, or the CMS
	 * and can therefore be trusted.
	 */
	TRUSTED,
	/** The redirect is not fully trusted, but may be an external URL. */
	EXTERNAL,
	/** The redirect has to be an internal path. */
	INTERNAL
}
