package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.authentication.PortalUser;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.EndpointOptions;
import com.gentics.mesh.portal.content.LanguageVariantLoader;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Simple {@link com.gentics.mesh.portal.api.authentication.AuthenticationHandler}
 * implementation to test the {@link AuthenticationBaseHandler}.
 */
public class FakeAuthenticationHandler extends AuthenticationBaseHandler {

	public static final String PROVIDER_ID = "FAKE_AUTH_PROVIDER";
	public static final String LOGIN_REDIRECT = "fake-auth-redirect";

	private final PortalConfig config;

	public FakeAuthenticationHandler(
			PortalConfig config,
			LanguageVariantLoader languageVariantLoader,
			CookieProvider cookieProvider,
			EncryptionService encryptionService,
			LoginRedirectHandler loginRedirectHandler) {
		super(config, languageVariantLoader, cookieProvider, encryptionService, loginRedirectHandler);

		this.config = config;
	}

	@Override
	protected PortalUser upgradeUser(User user) {
		return ((FakePortalUser) user).upgrade();
	}

	@Override
	public void createUser(RoutingContext rc, Handler<AsyncResult<? extends PortalUser>> callback) {
		String expired = getCookieValue(rc, FakePortalUser.COOKIE_AUTH_EXPIRED);

		if ("true".equalsIgnoreCase(expired)) {
			callback.handle(Future.failedFuture("Authentication cookies expired"));

			return;
		}

		String username = getCookieValue(rc, COOKIE_AUTH);

		if (username == null) {
			callback.handle(Future.failedFuture("Missing authentication cookie: " + COOKIE_AUTH));

			return;
		}

		String roles = getCookieValue(rc, FakePortalUser.COOKIE_USER_AUTHORITIES);

		if (roles == null) {
			callback.handle(Future.failedFuture("Missing authentication cookie: " + FakePortalUser.COOKIE_USER_AUTHORITIES));

			return;
		}

		FakePortalUser user = new FakePortalUser(username, roles.split(","));

		callback.handle(Future.succeededFuture(user));
	}

	@Override
	public void handleAuthentication(RoutingContext rc) {
		rc.response()
			.setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
			.putHeader(HttpHeaderNames.WWW_AUTHENTICATE, "Fake realm=dummy")
			.end();
	}

	@Override
	public Authorization createAuthorization(String name) {
		return PermissionBasedAuthorization.create(name);
	}

	@Override
	public void handleAuthorizationInternal(RoutingContext rc, Authorization required) {
		((FakePortalUser) rc.user()).checkAuthorization(rc, required);
	}

	@Override
	public void logout(RoutingContext rc, Handler<AsyncResult<Boolean>> callback) {
		((FakePortalUser) rc.user()).logout();
		callback.handle(Future.succeededFuture(false));
	}

	@Override
	public void handlePreAuth(RoutingContext rc) {
		rc.put(RC_AUTH_REDIRECT, LOGIN_REDIRECT);

		super.handlePreAuth(rc);
	}

	/**
	 * Add a protected endpoint.
	 *
	 * <p>
	 *     Since those endpoints are initialized during {@link #addRoutes(Router)},
	 *     which is never called for non-integration tests, they have to be
	 *     added via this method.
	 * </p>
	 * @param endpoint The protected endpoint to add
	 * @return This authentication handler for fluent API access
	 */
	public FakeAuthenticationHandler addProtectedEndpoint(EndpointOptions endpoint) {
		protectedEndpoints.put(endpoint.getPath(), endpoint);

		return this;
	}
}
