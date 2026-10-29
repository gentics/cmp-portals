package com.gentics.mesh.portal.server.dagger;

import javax.inject.Singleton;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;

import dagger.BindsInstance;
import dagger.Component;

@Singleton
@Component(modules = { PortalModule.class, BindModule.class, MicrometerModule.class })
public interface PortalServerComponent extends BaseComponent {

	TestBootstrapInitializer runner();

	@Component.Builder
	interface Builder {

		@BindsInstance
		Builder configuration(PortalConfig config);

		PortalServerComponent build();
	}

}
