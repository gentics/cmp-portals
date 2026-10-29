package com.gentics.mesh.portal.api.config;

public interface Config extends OverridableOptions {

	/**
	 * Validate the configuration.
	 * @throws InvalidConfigurationException
	 */
	default void validate() {
		// NOOP
	}

}
