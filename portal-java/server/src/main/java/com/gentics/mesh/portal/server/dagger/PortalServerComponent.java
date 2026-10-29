package com.gentics.mesh.portal.server.dagger;


import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.config.PortalConfig;
import dagger.BindsInstance;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = { PortalModule.class, BindModule.class, MicrometerModule.class, AuthenticationModule.class })
public interface PortalServerComponent extends BaseComponent {

	BootstrapInitializer runner();

	@Component.Builder
	interface Builder {

		@BindsInstance
		Builder configuration(PortalConfig config);

		PortalServerComponent build();
	}
}
