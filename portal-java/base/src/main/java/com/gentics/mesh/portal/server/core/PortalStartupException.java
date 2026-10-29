package com.gentics.mesh.portal.server.core;

/**
 * Exception that is thrown when one of the portal start-up checks fails.
 */
public class PortalStartupException extends Exception {
	public PortalStartupException(String message) {
		super("Startup check failed: " + message);
	}

	public PortalStartupException(String message, Throwable cause) {
		super("Startup check failed: " + message, cause);
	}
}
