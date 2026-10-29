package com.gentics.mesh.portal.server;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.PortalConfigLoader;
import com.gentics.mesh.portal.log.LoggingConfigurator;
import com.gentics.mesh.portal.server.core.PortalStarter;
import com.gentics.mesh.portal.server.dagger.DaggerPortalServerComponent;

public class MeshPortalServer {

	public static void main(String[] args) {
		LoggingConfigurator.init();

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		PortalStarter.start(() -> DaggerPortalServerComponent.builder().configuration(config).build());
	}
}
