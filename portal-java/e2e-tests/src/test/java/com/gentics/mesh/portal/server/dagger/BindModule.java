package com.gentics.mesh.portal.server.dagger;

import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.content.NavigationTransformer;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.applications.PortalApplicationRegistry;
import com.gentics.mesh.portal.applications.TestPortalApplicationRegistry;
//import com.gentics.mesh.portal.authentication.ServerLoginRedirectHandler;
import com.gentics.mesh.portal.authentication.ServerLoginRedirectHandler;
import com.gentics.mesh.portal.content.BasicNavigationTransformer;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;

import dagger.Binds;
import dagger.Module;

/**
 * Module which contains the bindings for the portal test context.
 */
@Module
public abstract class BindModule {

	@Binds
	abstract BootstrapInitializer bindBoot(TestBootstrapInitializer e);

	@Binds
	abstract PortalApplicationRegistry bindPortalApplicationRegistry(TestPortalApplicationRegistry e);

	@Binds
	abstract NavigationTransformer bindNavigationTransformer(BasicNavigationTransformer e);

	@Binds
	abstract LoginRedirectHandler bindLoginRedirectHandler(ServerLoginRedirectHandler e);
}
