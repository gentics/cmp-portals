package com.gentics.mesh.portal.handler;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.config.PortalConfig;

import com.gentics.mesh.portal.config.option.SecurityOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class AdditionalHeaderHandler extends AbstractBaseHandler {
	private final PortalConfig portalConfig;

	@Inject
	public AdditionalHeaderHandler(PortalConfig portalConfig) {
		this.portalConfig = portalConfig;
	}

	@Override
	public void addRoutes(Router router) {
		router.route().handler(this);
	}

	@Override
	public void handle(RoutingContext ctx) {
		Optional.ofNullable(portalConfig)
			.map(PortalConfig::getSecurityOptions)
			.map(SecurityOptions::getAdditionalResponseHeaders)
			.ifPresent(headers -> headers.forEach(ctx.response()::putHeader));

		ctx.next();
	}
}
