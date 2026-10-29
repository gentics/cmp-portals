package com.gentics.mesh.portal.server.dagger;

import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.authentication.oauth2.KeycloakAuthenticationHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.content.LanguageVariantLoader;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;

import javax.inject.Singleton;

@Module
public class AuthenticationModule {

	@Provides
	@Singleton
	KeycloakAuthenticationHandler keycloakAuthenticationHandler(
			Lazy<BootstrapInitializer> boot,
			Lazy<PortalInfo> portalInfo,
			PortalConfig config,
			Vertx vertx,
			LanguageVariantLoader languageVariantLoader,
			CookieProvider cookieProvider,
			EncryptionService encryptionService,
			LoginRedirectHandler loginRedirectHandler) {
		return new KeycloakAuthenticationHandler(boot, portalInfo, config, vertx, languageVariantLoader, cookieProvider, encryptionService, loginRedirectHandler);
	}
}
