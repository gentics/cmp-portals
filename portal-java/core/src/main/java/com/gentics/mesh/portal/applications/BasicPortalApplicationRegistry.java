package com.gentics.mesh.portal.applications;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.api.config.InvalidConfigurationException;

/**
 * Simple registry containing no applications.
 *
 * This class provides a protected Map from application keys to {@link PortalApplication PortalApplications},
 * and can be used as a base class for registries which only need to be backed by a simple Map.
 */
@Singleton
public class BasicPortalApplicationRegistry implements PortalApplicationRegistry {

	/** The registered portal applications. */
	protected final Map<String, PortalApplication> applications = new HashMap<>();

	/**
	 * Default constructor.
	 */
	@Inject
	public BasicPortalApplicationRegistry() {
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.applications.PortalApplicationRegistry#get(java.lang.String)
	 */
	@Override
	public PortalApplication get(String appId) {
		return applications.get(appId);
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.applications.PortalApplicationRegistry#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String appId) {
		return applications.containsKey(appId);
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.applications.PortalApplicationRegistry#registerApplications()
	 */
	@Override
	public void registerApplications() throws InvalidConfigurationException {
	}
}
