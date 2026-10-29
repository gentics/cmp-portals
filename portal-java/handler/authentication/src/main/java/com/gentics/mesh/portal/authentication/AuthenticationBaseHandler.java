package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.authentication.AuthenticationCookie;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.authentication.PortalUser;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.AuthenticationOptions;
import com.gentics.mesh.portal.config.option.EndpointOptions;
import com.gentics.mesh.portal.config.option.LoginRedirectOptions;
import com.gentics.mesh.portal.config.option.PermissionMode;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.content.LanguageVariantLoader;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.context.RedirectMode;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.util.AuthUtils;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.AndAuthorization;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.NotAuthorization;
import io.vertx.ext.auth.authorization.OrAuthorization;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.authorization.WildcardPermissionBasedAuthorization;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.UserContextInternal;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Generic authentication handler.
 *
 * <p>
 *     Actual user generation and starting the authentication process is done
 *     by concrete implementations
 * </p>
 *
 * <p>
 *     This abstract base class will
 *     <ul>
 *         <li>
 *             Take care that authentication cookies are created and deleted
 *         </li>
 *         <li>
 *             A user is created and provided via {@link RoutingContext#user()}
 *             as early as possible
 *         </li>
 *         <li>
 *             All authentication relevant routes are added
 *         </li>
 *         <li>
 *             Missing permissions for loading pages from Mesh are handled
 *         </li>
 *         <li>
 *             Login and logout redirects are performed
 *         </li>
 *     </ul>
 * </p>
 */
public abstract class AuthenticationBaseHandler extends AbstractBaseHandler implements AuthenticationHandler {

	private final Logger log = LoggerFactory.getLogger(AuthenticationBaseHandler.class);

	/** Default redirection path, when neither the respective cookie nor a <code>Referer</code> header are present. */
	public static final String DEFAULT_REDIRECT = "/";
	/** The name of the group in the regex for the login endpoint. */
	public static final String PATH_PARAM_LOGIN_REDIRECT = "redirectPath";

	/** Query parameter to specify custom logout redirect. */
	private static final String PARAM_CUSTOM_LOGOUT_REDIRECT = "redirect";

	/** The routing context key for the authentication redirect. */
	protected static final String RC_AUTH_REDIRECT = "gpj.auth.redirect";
	/** The routing context key for the flag indication an authentication in progress. */
	protected static final String RC_AUTH_ACTIVE = "gpj.auth.active";
	/** Protected endpoints from the configuration. */
	protected final Map<String, EndpointOptions> protectedEndpoints = new HashMap<>();

	/** The authentication options from the server.yml. */
	protected final AuthenticationOptions authOptions;
	/** The cookie provider to use. */
	protected final CookieProvider cookieProvider;
	/** The encryption service to use. */
	protected  final EncryptionService encryptionService;
	/** Whether to encrypt authentication cookies. */
	protected final boolean encryptCookies;
	/** Whether to redirect to the configured 401 error page. */
	protected final boolean useUnauthorizedRedirect;

	/** The handler which performs the redirect after logout. */
	private final Handler<RoutingContext> defaultLogoutRedirectHandler;
	/** The handler which performs the final redirects after a successful login. */
	private final LoginRedirectHandler loginRedirectHandler;
	private final Function<RoutingContext, Single<String>> loginRedirectPathMapper;

	public AuthenticationBaseHandler(
				PortalConfig config,
				LanguageVariantLoader languageVariantLoader,
				CookieProvider cookieProvider,
				EncryptionService encryptionService,
				LoginRedirectHandler loginRedirectHandler) {
		this.authOptions = config.getAuthOptions();
		this.cookieProvider = cookieProvider;
		this.encryptionService = encryptionService;
		this.encryptCookies = config.getSecurityOptions().isEncryptCookies();
		this.defaultLogoutRedirectHandler = createLogoutRedirectHandler(authOptions, languageVariantLoader);
		this.loginRedirectPathMapper = loginRedirectPathMapper(authOptions.getLoginRedirectOptions(), languageVariantLoader);
		this.loginRedirectHandler = loginRedirectHandler;
		this.useUnauthorizedRedirect = authOptions.isUseUnauthorizedRedirect()
			&& config.getRedirectOptions().getStatusPages().containsKey(HttpResponseStatus.UNAUTHORIZED.codeAsText().toString());

		if (authOptions.isUseUnauthorizedRedirect() && !useUnauthorizedRedirect) {
			log.warn("Setting authentication.useUnauthorizedRedirect is enabled, but no redirect is configured for redirects.statusPages.401");
			log.warn("Unauthorized requests will trigger authentication directly");
		}

		log.info("Created authentication handler: {}", getClass().getSimpleName());
	}

	protected abstract PortalUser upgradeUser(User user);

	/**
	 * Add the default routes for authentication.
	 *
	 * <p>
	 *     When this method is <em>not</em> overridden by an actual
	 *     implementation, the {@link #handlePreAuth(RoutingContext)} and
	 *     {@link #handlePostAuth(RoutingContext)} will never be called, since
	 *     it depends on the actual implementation for what routes the
	 *     {@code handlePreAuth()} handler should be called (the
	 *     {@code handlePostAuth} will then be called automatically).
	 * </p>
	 *
	 * <p>
	 *     If an implementation does not need those handlers at all, they should
	 *     still override this method with a call to
	 *     {@link #addRoutes(Router, Action)} with a dummy action.
	 * </p>
	 *
	 * @param router The portal router
	 */
	@Override
	public void addRoutes(Router router) {
		addRoutes(router, null);
	}

	/**
	 * Create and set a user created from cookies and perform post
	 * authentication actions.
	 *
	 * <p>
	 *     This handler will
	 *     <ol>
	 *         <li>
	 *             Tell the other handlers that authentication is available
	 *         </li>
	 *         <li>
	 *             Handle post authentication when there is a user present,
	 *             and the {@link #RC_AUTH_ACTIVE} flag is set in the routing
	 *             context
	 *         </li>
	 *         <li>
	 *             Let the concrete implementation create a user from the
	 *             authentication cookies
	 *         </li>
	 *     </ol>
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc).enableAuthentication();

		if (rc.user() != null) {
			if (Boolean.TRUE.equals(rc.get(RC_AUTH_ACTIVE))) {
				handlePostAuth(rc);
			} else {
				if (prc.isRerouting()) {
					log.trace("User already set, nothing to do");
				} else {
					log.warn("User was already set for non-reroute request");
				}

				rc.next();
			}
		} else {
			createUser(rc, rs -> {
				if (rs.failed()) {
					log.error("Auth user creation failed, request is anonymous", rs.cause());
					rc.next();
				} else {
					PortalUser user = rs.result();
	
					if (user != null) {
						if (log.isDebugEnabled()) {
							log.debug("User created: {}/{}", user.username(), user.userId());
						}
	
						((UserContextInternal) rc.userContext()).setUser(user);
						rc.put("userInfo", user.userInfo());

						if (user.authCookiesChanged()) {
							log.debug("Updating authentication cookies");
							addAuthCookies(rc, user.authCookies());
						}
					} else {
						log.debug("Request is anonymous");
					}
					rc.next();
				}
			});
		}
	}

	/**
	 * Add authentication routes.
	 *
	 * <p>
	 *     This method will create routes for
	 *     <ul>
	 *         <li>
	 *             the configured login and logout endpoints, which will handle
	 *             login and logout respectively
	 *         </li>
	 *         <li>
	 *             the configured protected endpoints, which will force user
	 *             authentication
	 *         </li>
	 *         <li>
	 *             all requests which will create and set a user
	 *         </li>
	 *         <li>
	 *             the missing permissions handler for content
	 *         </li>
	 *     </ul>
	 * </p>
	 *
	 * <p>
	 *     The {@code routeInjector} is called at the very end, and should
	 *     create a route which calls the {@link #handlePreAuth(RoutingContext)}
	 *     method, such that the authentication handler can recognize when a
	 *     request contains all the necessary information to perform an
	 *     authentication from credentials.
	 * </p>
	 *
	 * @param router The portal router
	 * @param routeInjector Additional action to perform after adding
	 * 		the default routes.
	 */
	protected final void addRoutes(Router router, Action routeInjector) {
		loginRedirectHandler.addRoutes(router);

		String path = authOptions.getLoginEndpoint();

		if (StringUtils.isNotBlank(path)) {
			// When the request is for /login the path param capturing group will be null, otherwise it contains
			// the path to redirect to.
			InternalEndpointBuilder.wrap(router)
				.withDisplayName("Login")
				.withDescription("Login endpoint")
				.withPathRegex(String.format("%s(?<%s>/.*)?", path, PATH_PARAM_LOGIN_REDIRECT))
				.withRAMLPath(path + "/:redirectPath")
				.withOrder(RouteOrder.AUTHENTICATION_ROUTES.getOrder())
				.withUriParameter("redirectPath", "Path to redirect to after authentication is finished", "/en/welcome")
				.withHandler(this::handleLogin)
				.withFailureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()))
				.withExampleResponse(HttpResponseStatus.SEE_OTHER, "Re-routed to the redirect path")
				.build();
		}

		router.route()
			.order(RouteOrder.AUTHENTICATION_ROUTES.getOrder())
			.handler(this);

		path = authOptions.getLogoutEndpoint();

		if (StringUtils.isNotBlank(path)) {
			InternalEndpointBuilder.wrap(router)
				.withDisplayName("Logout")
				.withDescription("Logout endpoint")
				.withPath(path)
				.withOrder(RouteOrder.AUTHENTICATION_ROUTES.getOrder())
				.withHandler(this::handleLogout)
				.withHandler(this::handleCustomLogoutRedirect)
				.withHandler(defaultLogoutRedirectHandler)
				.withFailureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()))
				.withExampleResponse(HttpResponseStatus.SEE_OTHER, "Re-routed to the default path")
				.build();
		}

		for (EndpointOptions endpoint : authOptions.getEndPoints()) {
			if (addEndpoint(endpoint)) {
				router.route(endpoint.getPath())
					.order(RouteOrder.AUTHENTICATION_ROUTES.getOrder())
					.handler(this::handleAuthentication)
					.handler(this::handleAuthorization)
					.failureHandler(HandlerUtils.unrecoverableFailureHandler(getClass()));
			}
		}

		router.route()
			.method(HttpMethod.GET)
			.order(RouteOrder.AFTER_CONTENT_ROUTES.getOrder())
			.handler(this::handleMissingPerms);


		if (routeInjector == null) {
			log.warn("Possible authentication handler problem:\n"
				+ "    The AuthenticationHandler implementation did not inject custom routes, which\n"
				+ "    means handlePreAuth() and handlePostAuth() will probably never be called");
		} else {
			try {
				routeInjector.run();
			} catch (Exception e) {
				log.error("Could not inject custom routes: {}", e.getMessage(), e);
			}
		}
	}

	/**
	 * Set the {@link #RC_AUTH_ACTIVE} flag in the routing context.
	 *
	 * @param rc The current routing context
	 */
	protected void handlePreAuth(RoutingContext rc) {
		log.debug("Preparing authentication process");
		rc.put(RC_AUTH_ACTIVE, true)
			.next();
	}

	/**
	 * Perform post authentication actions.
	 *
	 * <p>
	 *     This handler will
	 *     <ol>
	 *         <li>
	 *             get a final user from the concrete implemtations
	 *         </li>
	 *         <li>
	 *             set the authentications cookies for that user
	 *         </li>
	 *         <li>
	 *             initiate a redirect to
	 *             <ul>
	 *                 <li>
	 *                     the path specified in the routing context under
	 *                     {@link #RC_AUTH_REDIRECT} set by the actual
	 *                     authentication handler
	 *                 </li>
	 *                 <li>
	 *                     the path specified in the routing context under the
	 *                     key {@link #RC_AUTH_REDIRECT}
	 *                 </li>
	 *                 <li>
	 *                     the default redirect {@code /}
	 *                 </li>
	 *             </ul>
	 *             whichever is found first.
	 *         </li>
	 *     </ol>
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	protected void handlePostAuth(RoutingContext rc) {
		log.debug("Authentication finished, starting post authentication actions");

		PortalUser user = upgradeUser(rc.user());

		if (user == null) {
			log.warn("AuthenticationHandler implementation did not generate a valid PortalUser");
		} else {
			addAuthCookies(rc, user.authCookies());
		}

		loginRedirectPathMapper.apply(rc)
			.subscribe(path -> {
				String redirect = rc.get(RC_AUTH_REDIRECT);

				redirect = redirect == null ? DEFAULT_REDIRECT : StringUtils.prependIfMissing(redirect, "/");

				wrap(rc).redirect(path + redirect, RedirectMode.INTERNAL);
			});
	}

	/**
	 * Get the value of the specified cookie and decrypt it if necessary.
	 *
	 * @param rc The current routing context
	 * @param cookieName The name of the cookie to get
	 * @return The value of the specified cookie, which has been decrupted if
	 * 		{@link SecurityOptions#isEncryptCookies()} is {@code true}
	 */
	protected String getCookieValue(RoutingContext rc, String cookieName) {
		Cookie cookie = cookieProvider.get(rc, cookieName);

		if (cookie == null) {
			return null;
		}

		return encryptCookies
			? encryptionService.decrypt(cookie.getValue())
			: cookie.getValue();
	}

	/**
	 * Check if the current user is authorized for the current request.
	 *
	 * <p>
	 *     Required permissions for a certain path can be configured in
	 *     {@link AuthenticationOptions#getEndPoints() authentication.protectedEndpoints}.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	void handleAuthorization(RoutingContext rc) {
		String path = rc.currentRoute().getPath();
		PortalUser user = (PortalUser) rc.user();

		if (user == null) {
			log.warn("Authorization handling invoked on a non-authenticated request for {}", path);
			// We fail this request even if the endpoint does not need special
			// permissions, since the protected endpoints need at least an
			// authenticated user nevertheless.
			rc.fail(HttpResponseStatus.UNAUTHORIZED.code());

			return;
		}

		EndpointOptions endpoint = protectedEndpoints.get(path);

		if (endpoint == null || endpoint.getPermissions().isEmpty()) {
			log.debug("Path {} does not need special authorization", path);
			rc.next();

			return;
		}

		if (log.isDebugEnabled()) {
			log.debug(
				"Checking authorization for user {} on {} ({} {})",
				user.username(),
				path,
				endpoint.getPermissionMode().name(),
				endpoint.getPermissions());
		}

		handleAuthorizationInternal(rc, getRequiredAuthorization(endpoint));

	}

	private Authorization getRequiredAuthorization(EndpointOptions endpoint) {
		Collector<Authorization, ? extends Authorization, ? extends Authorization> collector = endpoint.getPermissionMode() == PermissionMode.ANY
			? Collector.of(
				OrAuthorization::create,
				(acc, authz) -> acc.addAuthorization(authz),
				(u, v) -> {
					v.getAuthorizations().forEach(u::addAuthorization);

					return u;
				})
			: Collector.of(
				AndAuthorization::create,
				(acc, authz) -> acc.addAuthorization(authz),
				(u, v) -> {
					v.getAuthorizations().forEach(u::addAuthorization);

					return u;
				});

		return endpoint.getPermissions().stream()
			.map(this::createAuthorization)
			.collect(collector);
	}

	/**
	 * Add the specified cookies to the response.
	 *
	 * <p>
	 *     Before adding them, the {@link Cookie#setSecure(boolean) secure flag}
	 *     is set accordingly.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param cookies The cookies to set
	 */
	protected void addAuthCookies(RoutingContext rc, Collection<AuthenticationCookie> cookies) {
		AuthUtils.addAuthCookies(rc, cookieProvider, cookies, encryptCookies ? encryptionService::encrypt : Function.identity(), this.authOptions.isUseSessionCookies());
	}

	/**
	 * Delete the authentication cookies remove the user.
	 *
	 * <p>
	 *     The method {@link AuthenticationHandler#logout(RoutingContext, Handler)}
	 *     is called to perform implementation specific logout actions. When
	 *     the async result is {@code true} the method already ended the
	 *     response to the request, and nothing more is left to do. Otherwise
	 *     {@link RoutingContext#next() rc.next()} is called to continue the
	 *     logout process.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	void handleLogout(RoutingContext rc) {
		if (rc.user() != null) {
			AuthUtils.deleteAuthCookies(cookieProvider, rc);

			logout(rc, rs -> {
				if (log.isDebugEnabled()) {
					log.debug("Removing user {}", rc.user());
				}

				rc.userContext().clear();

				if (rs.failed()) {
					Throwable cause = rs.cause();
					String msg = "Logout failed: {}";

					if (log.isTraceEnabled()) {
						log.error(msg, cause, cause.getMessage());
					} else {
						log.error(msg, cause.getMessage());
					}

					rc.fail(rs.cause());
				} else if (rs.result()) {
					log.debug("Terminal logout successful, nothing more to do");
				} else {
					log.debug("Logout successful");
					rc.next();
				}
			});
		} else {
			log.warn("Logout handler called while no user was logged in");
			rc.next();
		}
	}

	/**
	 * Create a handler that will perform a redirect after logout.
	 *
	 * <p>
	 *     When language fallback is possible for the configured
	 *     {@link AuthenticationOptions#getLogoutRedirect() logout redirect},
	 *     the best language match for the path is loaded via the
	 *     {@link LanguageVariantLoader}, otherwise the handler will redirect
	 *     to the configured URL (or / if no redirect is configured).
	 * </p>
	 *
	 * @param authOptions The portals authentication configuration
	 * @param languageVariantLoader The language variant loader of the portal
	 * @return A handler that will perform a redirect after a logout
	 */
	private Handler<RoutingContext> createLogoutRedirectHandler(AuthenticationOptions authOptions, LanguageVariantLoader languageVariantLoader) {
		String logoutRedirect = authOptions.getLogoutRedirect();

		if (languageVariantLoader.canLoadLanguageVariant(logoutRedirect)) {
			log.info("Configuring logout with language fallback: {}", logoutRedirect);

			return rc -> languageVariantLoader.getLanguageVariant(rc, logoutRedirect)
				.subscribe(langFallback -> wrap(rc).redirect(langFallback, RedirectMode.TRUSTED));
		}

		String finalRedirect;

		if (StringUtils.isBlank(logoutRedirect)) {
			log.warn("No logout redirect configured, falling back to default");
			finalRedirect = DEFAULT_REDIRECT;
		} else {
			finalRedirect = logoutRedirect;
		}

		log.info("Configuring absolute logout URL: {}", finalRedirect);

		return rc -> wrap(rc).redirect(finalRedirect, RedirectMode.TRUSTED);
	}

	private Function<RoutingContext, Single<String>> loginRedirectPathMapper(LoginRedirectOptions loginRedirectOptions, LanguageVariantLoader languageVariantLoader) {
		String redirect = loginRedirectOptions.getPath();
		Consumer<String> logSuccess = path -> log.debug("Login successful, redirecting to {}", path);

		if (loginRedirectOptions.isCmsPage() && languageVariantLoader.canLoadLanguageVariant(redirect)) {
			return rc -> languageVariantLoader.getLanguageVariant(rc, redirect)
				.doOnSubscribe(ignored -> log.debug("Loading language fallback for login redirect {}", redirect))
				.doOnError(cause -> log.error("Could not load language variant for {}: {}", redirect, cause.getMessage()))
				.onErrorReturnItem(redirect)
				.doOnSuccess(logSuccess);
		}

		return rc -> Single.just(redirect)
			.doOnSuccess(logSuccess);
	}

	private void handleCustomLogoutRedirect(RoutingContext rc) {
		String redirect = rc.queryParams().get(PARAM_CUSTOM_LOGOUT_REDIRECT);

		if (StringUtils.isNotBlank(redirect)) {
			log.debug("Following custom redirect");
			wrap(rc).redirect(redirect, RedirectMode.INTERNAL);
		} else {
			rc.next();
		}
	}

	/**
	 * Reroute to the login path containing referer information, or start user
	 * authentication if the redirect information is already present.
	 *
	 * @param rc The current routing context
	 */
	void handleLogin(RoutingContext rc) {
		if (Boolean.TRUE.equals(rc.get(RC_AUTH_ACTIVE))) {
			log.debug("Authentication already in progress, skipping login handling");
			rc.next();

			return;
		}

		String redirect = rc.pathParam(PATH_PARAM_LOGIN_REDIRECT);

		if (redirect == null) {
			// This is the original request to /login, add the redirect to the path, and reroute.
			redirect = HandlerUtils.sanitizeRedirect(rc.request().getHeader(HttpHeaders.REFERER), RedirectMode.INTERNAL);
			redirect = StringUtils.prependIfMissing(redirect, "/");

			if (StringUtils.isBlank(redirect)) {
				redirect = DEFAULT_REDIRECT;
			}

			rc.reroute(authOptions.getLoginEndpoint() + redirect);

			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Forcing authentication via {}", getClass().getSimpleName());
		}

		handleAuthentication(rc);
	}

	/**
	 * Check if the the page could not be loaded from Mesh due to missing
	 * permissions and either start authentication or fail the request
	 * with <em>403 Forbidden</em>.
	 *
	 * @param rc The current routing context
	 */
	void handleMissingPerms(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);

		if (prc.nodeToRender() == null && prc.hasMissingPerm()) {
			log.debug("Requested page needs authorization");

			if (rc.user() == null) {
				if (useUnauthorizedRedirect) {
					log.debug("User is not authenticated");
					rc.fail(HttpResponseStatus.UNAUTHORIZED.code());

					return;
				}

				log.debug("Forcing user authentication");
				handleAuthentication(rc);
			} else {
				log.debug("Logged in user is not authorized");
				rc.fail(HttpResponseStatus.FORBIDDEN.code());
			}
		} else {
			rc.next();
		}
	}

	/**
	 * Performs some sanity checks on configured {@link AuthenticationOptions#getEndPoints() protected endpoints}
	 * and adds them to {@link #protectedEndpoints}.
	 *
	 * @param endpoint The endpoint to add
	 * @return <code>true</code> when a valid path was configured in the endpoint, and <code>false</code> otherwise
	 */
	private boolean addEndpoint(EndpointOptions endpoint) {
		String path = endpoint.getPath();
		int idx = path.indexOf('*');

		if (idx >= 0) {
			if (idx != path.length() - 1) {
				// A wildcard is only allowed once at the end of a path.
				log.warn("Ignoring invalid path \"{}\"", path);

				return false;
			}

			path = path.substring(0, idx);
		}

		protectedEndpoints.put(path, endpoint);

		if (log.isDebugEnabled()) {
			String details;

			if (endpoint.getPermissions().isEmpty()) {
				details = "without special permissions";
			} else {
				details = String.format(
					"with permissions {%s %s}",
					endpoint.getPermissionMode().name(),
					String.join(" ", endpoint.getPermissions()));
			}

			log.debug("Adding {} as protected endpoint {}", endpoint.getPath(), details);
		}

		return true;
	}

	/**
	 * Get simple string representation of the given router in the form
	 * {@code className@identityHashCode}.
	 *
	 * @param router The router to get an ID for
	 * @return A simple string representation of the given router
	 */
	protected String routerId(Router router) {
		if (router == null) {
			return "(null)";
		}

		return router.getClass().getSimpleName()
			+ "@" + System.identityHashCode(router);
	}

	/**
	 * Creates a human-readable (more or less) representation of the given
	 * {@code Authorization}.
	 *
	 * <p>
	 *     This is intended for debugging and logging. When used in a logging
	 *     statement make sure to wrap it in a supplier (e.g. with
	 *     {@link com.gentics.mesh.portal.util.MiscUtils#lazyString(Supplier)},
	 *     since the string generation is rather expensive.
	 * </p>
	 *
	 * @param authz The {@code Authorization} to stringify
	 * @return A string representation of the given {@code Authorization}
	 */
	protected String prettyPrint(Authorization authz) {
		StringBuilder ret = new StringBuilder("[");

		if (authz instanceof OrAuthorization) {
			ret.append("ANY ");

			((OrAuthorization) authz).getAuthorizations().stream()
				.map(this::prettyPrint)
				.forEach(ret::append);
		} else if (authz instanceof AndAuthorization) {
			ret.append("ALL ");

			((AndAuthorization) authz).getAuthorizations().stream()
				.map(this::prettyPrint)
				.forEach(ret::append);
		} else if (authz instanceof NotAuthorization) {
			ret.append("NOT ")
				.append(prettyPrint(((NotAuthorization) authz).getAuthorization()));
		} else if (authz instanceof RoleBasedAuthorization) {
			String resource = ((RoleBasedAuthorization) authz).getResource();
			ret.append("ROLE ");

			if (StringUtils.isNotBlank(resource)) {
				ret.append(resource).append(':');
			}

			ret.append(((RoleBasedAuthorization) authz).getRole());
		} else if (authz instanceof PermissionBasedAuthorization) {
			String resource = ((PermissionBasedAuthorization) authz).getResource();
			ret.append("PERM ");

			if (StringUtils.isNotBlank(resource)) {
				ret.append(resource).append(':');
			}

			ret.append(((PermissionBasedAuthorization) authz).getPermission());
		} else if (authz instanceof WildcardPermissionBasedAuthorization) {
			String resource = ((WildcardPermissionBasedAuthorization) authz).getResource();
			ret.append("ROLE ");

			if (StringUtils.isNotBlank(resource)) {
				ret.append(resource).append(':');
			}

			ret.append(((WildcardPermissionBasedAuthorization) authz).getPermission());
		}

		return ret.append("]").toString();
	}
}
