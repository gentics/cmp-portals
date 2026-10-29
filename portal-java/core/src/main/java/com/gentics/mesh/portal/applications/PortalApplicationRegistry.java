package com.gentics.mesh.portal.applications;

import com.gentics.mesh.portal.api.config.InvalidConfigurationException;

/**
 * Interface for injected application registries.
 */
public interface PortalApplicationRegistry {
	/**
	 * Initialises all portal applications.
	 *
	 * @throws InvalidConfigurationException When a portal application cannot be initialised
	 */
	void registerApplications() throws InvalidConfigurationException;

	/**
	 * Get the portal application with the specified ID.
	 *
	 * @param appId The ID of the portal application to get
	 *
	 * @return The portal application with the specified ID or <code>null</code> if no such application is available
	 */
	PortalApplication get(String appId);

	/**
	 * Checks whether a portal application with the specified key exists.
	 *
	 * @param appId The application key to check
	 * @return <code>true</code> if {@link #get} would return a portal application instance with the specified ID,
	 * 	and <code>false</code> otherwise
	 */
	boolean contains(String appId);
}
