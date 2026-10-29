package com.gentics.mesh.portal.server.dagger;

import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.content.NavigationTransformer;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.applications.BasicPortalApplicationRegistry;
import com.gentics.mesh.portal.applications.PortalApplicationRegistry;
import com.gentics.mesh.portal.authentication.ServerLoginRedirectHandler;
import com.gentics.mesh.portal.content.BasicNavigationTransformer;
import com.gentics.mesh.portal.server.BasicBootstrapInitializer;
import dagger.Binds;
import dagger.Module;

@Module
public abstract class BindModule {

	@Binds
	abstract BootstrapInitializer bindBoot(BasicBootstrapInitializer e);

	@Binds
	abstract PortalApplicationRegistry bindPortalApplicationRegistry(BasicPortalApplicationRegistry e);

	@Binds
	abstract NavigationTransformer bindNavigationTransformer(BasicNavigationTransformer e);

	@Binds
	abstract LoginRedirectHandler bindLoginRedirectHandler(ServerLoginRedirectHandler e);
}
