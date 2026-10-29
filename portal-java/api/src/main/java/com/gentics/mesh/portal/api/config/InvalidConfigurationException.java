package com.gentics.mesh.portal.api.config;

public class InvalidConfigurationException extends RuntimeException {
	private static final long serialVersionUID = 5305104643759404570L;

	public InvalidConfigurationException(String key, String msg) {
		super("Invalid configuration for \"" + key + "\": " + msg);
	}

	public InvalidConfigurationException(String key, String msg, Throwable cause) {
		super("Invalid configuration for \"" + key + "\": " + msg, cause);
	}
}
