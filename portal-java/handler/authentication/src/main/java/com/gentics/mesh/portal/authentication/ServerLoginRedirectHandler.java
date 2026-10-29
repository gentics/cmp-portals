package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;

/**
 * Simple login redirect handler which sends a <em>303 See Other</em> response
 * for the location specified in the {@code AUTH_REDIRECT} cookie.
 */
public class ServerLoginRedirectHandler extends AbstractLoginRedirectHandler {

	@Inject
	public ServerLoginRedirectHandler(PortalConfig config, CookieProvider cookieProvider) {
		super(config, cookieProvider);
	}

	@Override
	public void handle(RoutingContext rc) {
		String redirect = getRedirect(rc);

		log.debug("Redirecting to {}", redirect);

		rc.response()
			.setStatusCode(HttpResponseStatus.SEE_OTHER.code())
			.putHeader(HttpHeaders.LOCATION, redirect)
			.end();
	}
}
