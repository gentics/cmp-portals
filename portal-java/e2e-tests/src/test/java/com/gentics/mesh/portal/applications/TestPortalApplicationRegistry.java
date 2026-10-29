package com.gentics.mesh.portal.applications;

import com.gentics.mesh.portal.api.config.InvalidConfigurationException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class TestPortalApplicationRegistry implements PortalApplicationRegistry {

	private final Map<String, PortalApplication> applications;

	@Inject
	public TestPortalApplicationRegistry() {
		this.applications = new HashMap<>();
	}

	@Override
	public void registerApplications() throws InvalidConfigurationException {
		PortalApplication app = new EchoLanguageApp();

		applications.put(app.getApplicationKey(), app);
	}

	@Override
	public PortalApplication get(String appId) {
		return applications.get(appId);
	}

	@Override
	public boolean contains(String appId) {
		return applications.containsKey(appId);
	}
}
