package com.gentics.mesh.portal.authentication.oauth2;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.authentication.AuthenticationCookie;
import com.gentics.mesh.portal.api.authentication.LoginRedirectHandler;
import com.gentics.mesh.portal.api.authentication.PortalUser;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.authentication.AuthenticationBaseHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.PortalConfigLoader;
import com.gentics.mesh.portal.config.option.AuthenticationOptions;
import com.gentics.mesh.portal.config.option.KeycloakOptions;
import com.gentics.mesh.portal.content.LanguageVariantLoader;
import com.gentics.mesh.portal.context.RedirectMode;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.MiscUtils;

import dagger.Lazy;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.RoleBasedAuthorization;
import io.vertx.ext.auth.impl.http.SimpleHttpClient;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.authorization.KeycloakAuthorization;
import io.vertx.ext.auth.oauth2.impl.OAuth2API;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.impl.OAuth2AuthHandlerImpl;

/**
 * An {@link com.gentics.mesh.portal.api.authentication.AuthenticationHandler}
 * implementation using the OAuth2 functionality of Keycloak.
 */
@Singleton
public class KeycloakAuthenticationHandler extends AuthenticationBaseHandler {

	/** The initialization state of the authentication handler. */
	private enum State {
		/** Auto discovery has not yet started. */
		UNINITIALIZED,
		/** Auto discovery was started, but has not finished yet. */
		INITIALIZING,
		/** The OAuth2 handler has been fully initialized and can be used for routes. */
		READY
	}

	/** The name of the authorization refresh cookie. */
	public static final String COOKIE_AUTH_REFRESH = "gpj.auth.refresh";

	/** The name of the if token cookie. */
	public static final String COOKIE_AUTH_ID = "gpj.auth.id";

	public static final String FIELD_TOKEN = "token";
	public static final String FIELD_ACCESS_TOKEN = "access_token";
	public static final String FIELD_REFRESH_TOKEN = "refresh_token";
	public static final String FIELD_ID_TOKEN = "id_token";
	public static final String FIELD_POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";

	private static final Logger log = LoggerFactory.getLogger(KeycloakAuthenticationHandler.class);

	private final Lazy<BootstrapInitializer> lazyBoot;
	private final Lazy<PortalInfo> lazyPortalInfo;
	private final PortalConfig config;
	private final Vertx vertx;
	private final KeycloakOptions keycloakOptions;
	/**
	 * List of routers to add handlers for, since the initialization is done
	 * asynchronously.
	 */
	private final List<Router> routers;

	private OAuth2AuthProviderImpl authnProvider;
	private KeycloakAuthorization authzProvider;
	private OAuth2API authApi;
	private OAuth2AuthHandlerImpl authHandler;
	private State state = State.UNINITIALIZED;
	private int expireLeeway;

	@Inject
	public KeycloakAuthenticationHandler(Lazy<BootstrapInitializer> lazyBoot, Lazy<PortalInfo> lazyPortalInfo,
			PortalConfig config, Vertx vertx, LanguageVariantLoader languageVariantLoader,
			CookieProvider cookieProvider, EncryptionService encryptionService,
			LoginRedirectHandler loginRedirectHandler) {
		super(config, languageVariantLoader, cookieProvider, encryptionService, loginRedirectHandler);

		JsonObject keycloakConfig = PortalConfigLoader.loadKeycloakOptions();

		this.lazyBoot = lazyBoot;
		this.lazyPortalInfo = lazyPortalInfo;
		this.config = config;
		this.vertx = vertx;
		this.keycloakOptions = fixOptions(config.getAuthOptions().getKeycloakOptions(), keycloakConfig);
		this.routers = new ArrayList<>();

		Objects.requireNonNull(keycloakOptions, "Keycloak configuration is missing");

		if (StringUtils.isBlank(keycloakOptions.getRealmUrl())) {
			throw new InvalidConfigurationException("authentication.keycloak.realmUrl", "Realm URL must be specified");
		}

		if (StringUtils.isBlank(keycloakOptions.getClientId())) {
			throw new InvalidConfigurationException("authentication.keycloak.clientId", "Realm URL must be specified");
		}
	}

	@Override
	protected PortalUser upgradeUser(User user) {
		return new OAuth2PortalUser(user).dirtCookies();
	}

	/**
	 * Setup the routes immediately if Keycloak is already {@link #state
	 * initialized}, or add it to the list of routers to setup.
	 *
	 * <p>
	 * The Keycloak {@link #initKeycloak() initialization} will be started if that
	 * did not happen already.
	 * </p>
	 *
	 * @see #initKeycloak()
	 * @param router The portal router
	 */
	@Override
	public void addRoutes(Router router) {
		synchronized (routers) {
			if (state == State.READY) {
				if (log.isDebugEnabled()) {
					log.debug("Setting up router with initialized Keycloak: {}", routerId(router));
				}
				setupRouter(router);
				return;
			}
			if (log.isDebugEnabled()) {
				log.debug("Delaying router setup until after initialization: {}", routerId(router));
			}
			routers.add(router);

			if (state == State.UNINITIALIZED) {
				log.debug("Starting Keycloak initialization");
				initKeycloak();
			}
		}
	}

	/**
	 * Check is the handler is ready for request serve
	 *
	 * @return
	 */
	public boolean isReady() {
		return state == State.READY;
	}

	/**
	 * Create a user from the {@link #COOKIE_AUTH}, {@link #COOKIE_AUTH_REFRESH} and
	 * {@link #COOKIE_AUTH_ID} cookies.
	 *
	 * @param rc       The current routing context
	 * @param callback The callback to pass the created user to
	 */
	@Override
	public void createUser(RoutingContext rc, Handler<AsyncResult<? extends PortalUser>> callback) {
		createUser(rc, false, callback);
	}

	/**
	 * Create a user from the possibly expired {@link #COOKIE_AUTH},
	 * {@link #COOKIE_AUTH_REFRESH} and {@link #COOKIE_AUTH_ID} cookies.
	 *
	 * <p>
	 * <strong>IMPORTANT:</strong> Users created with this method should
	 * <em>not</em> be used for authentication purposes. The created user objects
	 * are intended to extract information, which can still be considered valid when
	 * the signature is correct, even if the token is already expired (e.g. username
	 * for caching purposes, or forwarding a request to a Mesh plugin endpoint which
	 * contains the username).
	 * </p>
	 *
	 * @param rc       The current routing context
	 * @param callback The callback to pass the created user to
	 */
	public void createUserUnsafe(RoutingContext rc, Handler<AsyncResult<? extends PortalUser>> callback) {
		createUser(rc, true, callback);
	}

	/**
	 * Create a user from the {@link #COOKIE_AUTH}, {@link #COOKIE_AUTH_REFRESH} and
	 * {@link #COOKIE_AUTH_ID} cookies.
	 *
	 * <p>
	 * The created user is passed to the given {@code callback}.
	 * </p>
	 *
	 * @param rc           The current routing context
	 * @param allowExpired Whether expired (but otherwise valid) access tokens
	 *                     should be accepted
	 * @param callback     The callback to pass the created user to
	 */
	private void createUser(RoutingContext rc, boolean allowExpired,
			Handler<AsyncResult<? extends PortalUser>> callback) {
		String cookieValue = getCookieValue(rc, COOKIE_AUTH);

		if (cookieValue == null) {
			log.trace("No authentication cookie provided");
			callback.handle(Future.succeededFuture());

			return;
		}

		JsonObject authInfo = new JsonObject().put(FIELD_TOKEN, cookieValue);

		if (keycloakOptions.isRpInitiatedLogout()) {
			cookieValue = getCookieValue(rc, COOKIE_AUTH_ID);

			if (cookieValue == null) {
				log.warn("ID token cookie was not set, but portal is configured to use RP initiated logout");
			} else {
				authInfo.put("id_token", cookieValue);
			}
		}

		log.debug("Creating user from authentication cookies");

		authnProvider.authenticate(new TokenCredentials(authInfo))
				.flatMap(user -> refreshTokensIfNecessary(user, getCookieValue(rc, COOKIE_AUTH_REFRESH),
						keycloakOptions.isRpInitiatedLogout() ? getCookieValue(rc, COOKIE_AUTH_ID) : StringUtils.EMPTY,
						allowExpired))
				.onComplete(callback::handle);
	}

	/**
	 * Checks if the users access token is expired, using the leeway returned by
	 * auto-discovery.
	 *
	 * <p>
	 * When the access token is expired the authentication providers
	 * {@link OAuth2Auth#refresh(User, Handler) refresh()} method is used to refresh
	 * the token.
	 * </p>
	 *
	 * @param user         The user to check for expiration.
	 * @param refreshToken The users refresh token (when creating a user from
	 *                     cookies).
	 * @param idToken      The users ID token (when creating a user from cookies).
	 * @param allowExpired Whether to accept expired access tokens. When this. is
	 *                     {@code true} the refresh method will <em>not</em> be
	 *                     called.
	 * @return A future containing the possibly refreshed user.
	 */
	private Future<PortalUser> refreshTokensIfNecessary(User user, String refreshToken, String idToken,
			boolean allowExpired) {
		if (allowExpired || !user.expired(expireLeeway)) {
			return Future.succeededFuture(new OAuth2PortalUser(user, refreshToken, idToken));
		}

		user.principal().put(FIELD_REFRESH_TOKEN, refreshToken);

		return authnProvider.refresh(user).onSuccess(ignored -> log.debug("Token refresh successful"))
				.map(refreshedUser -> new OAuth2PortalUser(refreshedUser).dirtCookies());
	}

	/**
	 * Add auth cookies to the response, considering Keycloak options.
	 */
	@Override
	protected void addAuthCookies(RoutingContext rc, Collection<AuthenticationCookie> cookies) {
		super.addAuthCookies(rc, keycloakOptions.isRpInitiatedLogout() ? cookies
				: cookies.stream().filter(c -> !COOKIE_AUTH_ID.equals(c.getName())).collect(Collectors.toList()));
	}

	/**
	 * Check if the current request has a user set, and if not redirect to Keycloak
	 * login.
	 *
	 * @see OAuth2AuthHandler#handle(Object)
	 * @param rc The current routing context
	 */
	@Override
	public void handleAuthentication(RoutingContext rc) {
		authHandler.handle(rc);
	}

	/**
	 * Create a {@link RoleBasedAuthorization} from the given string.
	 *
	 * <p>
	 * When {@code name} contains a colon ('{@code :}'), the part before the colon
	 * is interpreted as the resource name, and the second part as the role. When no
	 * colon is present, the whole string is used as the role.
	 * </p>
	 *
	 * @param name The name of the authorization.
	 * @return A {@code RoleBasedAuthorization} created from the given name.
	 */
	@Override
	public Authorization createAuthorization(String name) {
		if (StringUtils.isBlank(name)) {
			return RoleBasedAuthorization.create("");
		}

		String[] parts = name.split(":");

		return parts.length > 1 ? RoleBasedAuthorization.create(parts[1]).setResource(parts[0])
				: RoleBasedAuthorization.create(parts[0]);
	}

	@Override
	public void handleAuthorizationInternal(RoutingContext rc, Authorization required) {
		User user = rc.user();

		if (!(user instanceof OAuth2PortalUser)) {
			log.debug("Request is not authenticated as a valid OAuth2PortalUser");
			rc.fail(HttpResponseStatus.UNAUTHORIZED.code());

			return;
		}

		authzProvider.getAuthorizations(user).map(ignored -> required.match(user)).onComplete(rs -> {
			if (rs.failed()) {
				log.warn("Authorization check for user {} failed: {}", user, rs.cause().getMessage());
			}

			if (Boolean.TRUE.equals(rs.result())) {
				log.trace("User {} is authorized for {}", user, MiscUtils.lazyString(() -> prettyPrint(required)));

				rc.next();
			} else {
				log.trace("User {} is not authorized for {}", user, MiscUtils.lazyString(() -> prettyPrint(required)));

				rc.fail(HttpResponseStatus.FORBIDDEN.code());
			}
		});
	}

	/**
	 * Log the user out.
	 *
	 * <p>
	 * When {@link KeycloakOptions#isRpInitiatedLogout() RP initiated logout} is
	 * enabled, the logout request to the portal will be answered with a redirect to
	 * the {@code end_session_endpoint} of Keycloak.
	 *
	 * The redirect will contain the users ID token, and a
	 * {@code post_logout_redirect_uri} to the configured
	 * {@link AuthenticationOptions#getLogoutRedirect() logout redirect}.
	 * </p>
	 *
	 * <p>
	 * When RP initiated logout is <em>not</em> enabled, the user will be logged out
	 * from Keycloak by calling Keycloaks {@code end_session_endpoint} from the
	 * portal (akin to a backchannel logout).
	 * </p>
	 *
	 * @param rc       The current routing context
	 * @param callback The callback to call after the logout actions are done
	 */
	@Override
	public void logout(RoutingContext rc, Handler<AsyncResult<Boolean>> callback) {
		User user = rc.user();

		if (user instanceof OAuth2PortalUser) {
			OAuth2PortalUser portalUser = (OAuth2PortalUser) user;

			if (keycloakOptions.isRpInitiatedLogout()) {
				wrap(rc).redirect(
						authApi.endSessionURL(portalUser.idToken(),
								new JsonObject().put(FIELD_POST_LOGOUT_REDIRECT_URI, authOptions.getLogoutRedirect())),
						RedirectMode.EXTERNAL);

				// Inform the callback that the logout request has been
				// terminated.
				callback.handle(Future.succeededFuture(true));
			} else {
				// Make the request to Keycloak to terminate the session and
				// inform the callback that the logout request has not been
				// terminated yet.
				logout(portalUser.meshAccessToken(), portalUser.refreshToken()).map(ignored -> false)
						.onComplete(callback::handle);
			}
		} else {
			// This should not happen(tm). The portal cookies should already
			// have been deleted by the base authentication handler, but the
			// Keycloak cookies might still be there.
			callback.handle(Future.failedFuture("No OAuth2PortalUser found, cannot logout"));
		}
	}

	/**
	 * Load the Keycloak options from the portal configuration, possibly adding
	 * settings from the optional <em>keycloak.json</em> and start the OpenID
	 * Connect auto discovery process.
	 */
	private void initKeycloak() {
		String realmUrl = keycloakOptions.getRealmUrl();
		String clientId = keycloakOptions.getClientId();
		OAuth2Options oauthClientOptions = new OAuth2Options().setSite(realmUrl).setClientId(clientId);

		if (StringUtils.isNotBlank(keycloakOptions.getClientSecret())) {
			oauthClientOptions.setClientSecret(keycloakOptions.getClientSecret());
		}

		log.info("Initiating OpenID connect discovery for client {} from {}", clientId, realmUrl);
		state = State.INITIALIZING;

		KeycloakAuth.discover(vertx, oauthClientOptions).andThen(this::discoveryCallback);
	}

	/**
	 * Extract the authentication redirect from the {@code state} query parameter of
	 * the request to the {@link KeycloakOptions#getCallbackPath() callback
	 * endpoint}.
	 *
	 * @param rc The current routing context
	 */
	private void prepareAuthRedirect(RoutingContext rc) {
		String redirect = rc.request().getParam("state");
		String loginEndpoint = authOptions.getLoginEndpoint();

		if (redirect != null) {
			if (redirect.startsWith(loginEndpoint + "/")) {
				redirect = redirect.substring(loginEndpoint.length());
			}

			log.debug("Preparing authentication redirect to {}", redirect);
			rc.put(RC_AUTH_REDIRECT, redirect).next();
		} else {
			// Something suspicious is going on, fail the request to be safe.
			log.warn("Missing callback state");
			rc.fail(HttpResponseStatus.BAD_REQUEST.code());
		}
	}

	/**
	 * Create an authentication handler and setup routes with the created
	 * authentication provider from the auto discovery result.
	 *
	 * @param rs The auto discovery result
	 */
	private void discoveryCallback(AsyncResult<OAuth2Auth> rs) {
		if (rs.failed()) {
			Throwable cause = rs.cause();
			// Direct connection
			if (cause instanceof IOException
					// Nginx failure
					|| (cause instanceof NoStackTraceThrowable && cause.getMessage() != null
							&& cause.getMessage().contains("Bad Gateway"))) {
				int retryPeriodSeconds = keycloakOptions.getRetryPeriodSeconds();
				if (retryPeriodSeconds > 0) {
					log.info("Retrying Keycloak configuration in {} seconds", retryPeriodSeconds);
					vertx.setTimer(retryPeriodSeconds * 1000, unused -> initKeycloak());
					return;
				} else if (retryPeriodSeconds == 0) {
					log.error("Keycloak is not reachable. Terminating Gentics Portal | java.");
					try {
						lazyBoot.get().stop();
					} catch (Exception e) {
						log.error("Error terminating Gentics Portal | java", e);
					}
					return;
				}
			}
			log.error("Could not configure Keycloak via OpenID Connect discovery: {}", cause.getMessage(), cause);

			return;
		}

		OAuth2Auth authn = rs.result();

		if (!(authn instanceof OAuth2AuthProviderImpl)) {
			log.error("Could not configure Keycloak via OpenID Connect discovery");

			return;
		}

		authnProvider = (OAuth2AuthProviderImpl) authn;
		authzProvider = KeycloakAuthorization.create();

		OAuth2Options authConfig = this.authnProvider.getConfig();

		// This is a bit of a hack: We want to be able to create users from
		// expired tokens (see createUserUnsage()), so we have to set an
		// unlimited leeway for the JWT. The actual configured leeway is used
		// when the token is checked by the portal itself, not during creation
		// by Vert.x.
		expireLeeway = authConfig.getJWTOptions().getLeeway();
		authConfig.getJWTOptions().setLeeway(Integer.MAX_VALUE);

		this.authApi = new OAuth2API(vertx, authConfig);

		URL callbackUrl;

		try {
			callbackUrl = new URL(config.getServerUrl(),
					config.getAuthOptions().getKeycloakOptions().getCallbackPath());
		} catch (MalformedURLException e) {
			log.error("Could not create callback URL: {}", e.getMessage(), e);

			return;
		}

		authHandler = (OAuth2AuthHandlerImpl) OAuth2AuthHandler.create(vertx, authn, callbackUrl.toString())
				.withScope("openid");
		// The callback will be mounted internally on the specified order minus one.
		authHandler.onOrder(RouteOrder.AUTHENTICATION_ROUTES.getOrder() + 2);

		synchronized (routers) {
			routers.forEach(this::setupRouter);
			routers.clear();
			state = State.READY;
		}

		if (log.isInfoEnabled()) {
			log.info("Initialized {} with callback URL {}", getClass().getSimpleName(), callbackUrl);
		}
	}

	/**
	 * Add the authentication routes for the given router.
	 *
	 * <p>
	 * <strong>Note</strong> that this method must only be called, after
	 * {@link #initKeycloak() auto discovery} has finished and an authentication
	 * handler has been initialized.
	 * </p>
	 *
	 * @param router The router to setup
	 */
	private void setupRouter(Router router) {
		String callbackPath = config.getAuthOptions().getKeycloakOptions().getCallbackPath();

		if (log.isDebugEnabled()) {
			log.debug("Setting up router {} with callback path {}", routerId(router), callbackPath);
		}

		addRoutes(router, () -> {
			// Since the OAuth2 handlers callback handler will initiate a
			// reroute, we have to register our callback redirect setter
			// beforehand.
			Route callbackRoute = router.get(callbackPath);

			callbackRoute.order(RouteOrder.AUTHENTICATION_ROUTES.getOrder()).handler(this::prepareAuthRedirect)
					.handler(this::handlePreAuth);
			// The route cannot be reused because of how Vert.x does
			// the callback setup.
			authHandler.setupCallback(router.get(callbackPath));
		});
	}

	/**
	 * Load possibly missing settings from the optional <em>keycloak.json</em>.
	 *
	 * @param options The Keycloak options from the portal configuration
	 * @return The given options but filled with values from <em>keycloak.json</em>
	 *         if present
	 */
	private KeycloakOptions fixOptions(KeycloakOptions options, JsonObject keycloakConfig) {
		if (options == null || keycloakConfig.isEmpty()) {
			return options;
		}

		if (StringUtils.isBlank(options.getRealmUrl())) {
			options.setRealmUrl(createRealmUrlFromConfig(keycloakConfig));
		}

		if (StringUtils.isBlank(options.getClientId())) {
			String clientId = keycloakConfig.getString("resource");

			log.trace("Using client ID from keycloak.json: {}", clientId);
			options.setClientId(clientId);
		}

		if (StringUtils.isBlank(options.getClientSecret())) {
			log.trace("Using client secret from keycloak.json");
			options.setClientSecret(JsonUtils.getValue(keycloakConfig, "credentials.secret", null));
		}

		return options;
	}

	/**
	 * Create the realm URL from the {@code auth-server-url} and {@code realm}
	 * fields in the <em>keycloak.json</em>.
	 *
	 * @return The realm URL extracted from the <em>keycloak.json</em>
	 */
	private String createRealmUrlFromConfig(JsonObject keycloakConfig) {
		String realmUrl = null;
		String authServerUrl = keycloakConfig.getString("auth-server-url");
		String realm = keycloakConfig.getString("realm");

		if (StringUtils.isNotBlank(authServerUrl) && StringUtils.isNotBlank(realm)) {
			realmUrl = String.format("%s/realms/%s", authServerUrl, realm);

			log.trace("Created realm URL from data in keycloak.json: {}", realmUrl);
		} else {
			log.trace("Could not create realm URL from data in keycloak.json");
		}

		return realmUrl;
	}

	// https://github.com/eclipse-vertx/vertx-auth/commit/248094677f5c9345fa5e6d6a39bff4fb66675c1c#diff-082c328c10b3e74e45541efd70170cdd0758cbd857f77c39bef1c2c6456b8afa
	// Removing a public API item with no deprecation/documentation/migration practice should be a valid reason for a severe penalty.
	/**
	 * Sign out an end-user.
	 * <p>
	 * see:
	 */
	private Future<Void> logout(String accessToken, String refreshToken) {
		final JsonObject headers = new JsonObject();
		final OAuth2Options config = authnProvider.getConfig();

		headers.put("Authorization", "Bearer " + accessToken);

		final JsonObject form = new JsonObject();

		form.put("client_id", config.getClientId());

		if (config.getClientSecret() != null) {
			form.put("client_secret", config.getClientSecret());
		}

		if (refreshToken != null) {
			form.put("refresh_token", refreshToken);
		}

		headers.put("Content-Type", "application/x-www-form-urlencoded");
		final Buffer payload = SimpleHttpClient.jsonToQuery(form);
		// specify preferred accepted accessToken type
		headers.put("Accept", "application/json,application/x-www-form-urlencoded;q=0.9");

		return authApi.fetch(HttpMethod.POST, config.getLogoutPath(), headers, payload).mapEmpty();
	}
}
