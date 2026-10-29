package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.LoginRedirectOptions;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;

/**
 * Simple login redirect which responds with minimal HTML markup that should
 * redirect to the path specified in the {@code AUTH_REDIRECT} cookie.
 *
 * <p>
 *     The redirect cookie may not be available when the handler ist first
 *     called, due to SameSite cookie policies. When no redirect cookie is
 *     available, the handler will respond with minimal HTML that will
 *     redirect back to this handler, but with the {@code force-redirect}
 *     query parameter.
 * </p>
 *
 * <p>
 *     The next request to this handler should supply the cookie, and if not
 *     the parameter will cause the handler to redirect to the configured
 *     default path ({@link LoginRedirectOptions#getDefaultRedirect()}).
 * </p>
 *
 * <p>
 *     The markup uses a {@code &lt;meta http-equiv=refresh%gt;}} tag to
 *     make the browser navigate to the correct path.
 * </p>
 */
public class ClientLoginRedirectHandler extends AbstractLoginRedirectHandler {

	private static final String FMT_RESPONSE = "<!DOCTYPE html><htmL><head>"
		+ "<title>Gentics Portal | java</title>"
		+ "<meta http-equiv=\"refresh\" content=\"0; URL=%s\">"
		+ "</head><body></body></html>";

	@Inject
	public ClientLoginRedirectHandler(PortalConfig config, CookieProvider cookieProvider) {
		super(config, cookieProvider);
	}

	/**
	 * Just extract the redirect from the cookie and call the next handler.
	 *
	 * @param rc The current routing context
	 */
	@Override
	protected void prepareRedirect(RoutingContext rc) {
		extractRedirect(rc).next();
	}

	@Override
	public void handle(RoutingContext rc) {
		String redirect = getRedirect(rc);

		log.debug("Triggering redirect to {}", redirect);

		rc.response()
			.setStatusCode(HttpResponseStatus.OK.code())
			.putHeader(HttpHeaders.CONTENT_TYPE, "text/html")
			.end(String.format(FMT_RESPONSE, redirect));
	}
}
