package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.LoginRedirectOptions;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Base implementation for {@link LoginRedirectHandler LoginRedirectHandlers}.
 *
 * <p>
 *     This abstract base class implements the {@link LoginRedirectHandler#addRoutes(Router)}
 *     method, and adds a handler that will copy the value of the {@code AUTH_REDIRECT}
 *     cookie to the routing context and delete the cookie itself.
 * </p>
 *
 * <p>
 *     Implementations which provide the actual {@link #handle(Object)} method,
 *     can get the redirect via {@link #getRedirect(RoutingContext)}.
 * </p>
 */
public abstract class AbstractLoginRedirectHandler extends AbstractBaseHandler implements LoginRedirectHandler {

	private static final String RC_REDIRECT = "gpj.auth.loginRedirect";

	protected final Logger log = LoggerFactory.getLogger(getClass());

	private final String handlerPath;

	protected final boolean needRedirect;
	protected final String defaultRedirect;
	protected final CookieProvider cookieProvider;

	public AbstractLoginRedirectHandler(PortalConfig config, CookieProvider cookieProvider) {
		LoginRedirectOptions loginRedirectOptions = config.getAuthOptions().getLoginRedirectOptions();

		this.handlerPath = loginRedirectOptions.getPath();
		this.needRedirect = !loginRedirectOptions.isCmsPage();
		this.defaultRedirect = loginRedirectOptions.getDefaultRedirect();
		this.cookieProvider = cookieProvider;

		log.info("Login redirect handler initialized: {}", getClass().getSimpleName());
	}

	@Override
	public void addRoutes(Router router) {
		// If the configured login redirect path is actually a page from the
		// CMS, we do not need custom redirect logic.
		if (!needRedirect) {
			return;
		}

		InternalEndpointBuilder.wrap(router)
			.withDescription("Successful authentication callback")
			.withPathRegex(String.format("%s(?<%s>/.*)?", handlerPath, AuthenticationBaseHandler.PATH_PARAM_LOGIN_REDIRECT))
			.withRAMLPath(String.format("%s/:%s", handlerPath, AuthenticationBaseHandler.PATH_PARAM_LOGIN_REDIRECT))
			.withUriParameter(AuthenticationBaseHandler.PATH_PARAM_LOGIN_REDIRECT, "Redirection path", "/authenticated")
			.withMethod(HttpMethod.GET)
			.withOrder(RouteOrder.BEFORE_API_ROUTES.getOrder())
			.withHandler(this::prepareRedirect)
			.withHandler(this)
			.hidden(true)
			.withExampleResponse(HttpResponseStatus.SEE_OTHER, "Redirect to the configured page")
			.build();
	}

	/**
	 * Get the redirect that was stored in the routing context by
	 * {@link #prepareRedirect(RoutingContext)}.
	 *
	 * @param rc The current routing context
	 * @return The value from the {@code AUTH_REDIRECT} value
	 */
	protected String getRedirect(RoutingContext rc) {
		return rc.get(RC_REDIRECT);
	}

	/**
	 * Load the value of the {@code AUTH_REDIRECT} cookie, store it in the
	 * routing context, delete the cookie and call the next handler.
	 *
	 * <p>
	 *     <strong>IMPORTANT:</strong> This is a handler method, which must
	 *     call {@link RoutingContext#next()} at the end.
	 * </p>
	 *
	 * <p>
	 *     This is the default implementation for redirect preparation. Custom
	 *     handlers may override this method, if they need more control over
	 *     when the cookie is deleted.
	 * </p>
	 *
	 * <p>
	 *     Note that custom handlers overriding this method must make sure to
	 *     call {@link #extractRedirect(RoutingContext)} at some point (either
	 *     in the overridden {@code prepareRedirect()} or the acutal
	 *     {@code handle()} method.
	 * </p>
	 *
	 * @see #extractRedirect(RoutingContext)
	 * @param rc The current routing context
	 */
	protected void prepareRedirect(RoutingContext rc) {
		extractRedirect(rc);

		rc.next();
	}

	/**
	 * Extract the value from the request path (the part after the
	 * {@link #handlerPath}) and store it in the routing context under
	 * {@link #RC_REDIRECT}.
	 *
	 * <p>
	 *     The stored value is an {@link Optional} containing the cookie value
	 *     if it was found, and an empty {@code Optional} otherwise.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @return The routing context for fluent API
	 */
	protected RoutingContext extractRedirect(RoutingContext rc) {
		String redirect = rc.pathParam(AuthenticationBaseHandler.PATH_PARAM_LOGIN_REDIRECT);

		if (StringUtils.isBlank(redirect)) {
			redirect = defaultRedirect;
		} else {
			String query = rc.request().query();

			if (StringUtils.isNotBlank(query)) {
				redirect += "?" + query;
			}
		}

		return rc.put(RC_REDIRECT, redirect);
	}
}
