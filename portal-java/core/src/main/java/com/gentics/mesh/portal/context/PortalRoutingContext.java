package com.gentics.mesh.portal.context;

import static com.gentics.mesh.portal.util.MiscUtils.getEnumValue;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.parameter.VersioningParameters;
import com.gentics.mesh.parameter.client.VersioningParametersImpl;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.api.content.PreviewMode;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.applications.PortalApplication;
import com.gentics.mesh.portal.config.option.CompatibilityOptions;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.mesh.rest.client.MeshRestClient;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.LanguageHeader;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.UserContext;

/**
 * Wrapper for {@link RoutingContext} that allows setting of additional properties/objects
 */
public class PortalRoutingContext implements RoutingContext {

	public final static String PARAM_RENDER_MODE = "renderMode";

	public static final String PREVIEW_KEY = "gpj.content.preview";

	public static final String NAVIGATION_KEY = "navigation";

	public static final String AUTH_ENABLED_KEY = "gpj.auth.enabled";

	public static final String BRANCH_KEY = "gpj.branch";

	public static final String DISABLE_CACHE_KEY = "gpj.cache.disabled";

	/** The cache name and {@link RoutingContext} key for user roles. */
	public static final String USER_ROLES_KEY = "gpj.cache.userRoles";

	private static final String USER_CLIENT_KEY = "gpj.auth.userClient";

	private static final String MISSING_PERMISSION_KEY = "gpj.content.missingPerm";

	private static final String PORTAL_APPLICATIONS = "gpj.applications";

	private static final String ENABLE_NAVIGATION_KEY = "gpj.navigation.enabled";

	private static final String LANGUAGE_SOURCE_KEY = "gpj.language.priority";

	private final Logger log = LoggerFactory.getLogger(PortalRoutingContext.class);

	private final RoutingContext rc;

	/**
	 * Wrap the routing context
	 *
	 * @param rc
	 *            routing context
	 * @return wrapper
	 */
	public static PortalRoutingContext wrap(RoutingContext rc) {
		return rc instanceof PortalRoutingContext ? (PortalRoutingContext) rc : new PortalRoutingContext(rc);
	}

	/**
	 * Create a new plugin context which will wrap the {@link RoutingContext} of the handled request.
	 *
	 * @param rc
	 *            routing context
	 */
	public PortalRoutingContext(RoutingContext rc) {
		this.rc = rc;
	}

	/**
	 * Get the wrapped routing context.
	 * @return The wrapped routing context.
	 */
	public RoutingContext wrapped() {
		return rc;
	}

	@Override
	public HttpServerRequest request() {
		return rc.request();
	}

	@Override
	public HttpServerResponse response() {
		return rc.response();
	}

	@Override
	public void next() {
		List<PortalApplication> apps = rc.get(PORTAL_APPLICATIONS);
		if (apps != null && !apps.isEmpty()) {
			PortalApplication nextApp = apps.remove(0);
			nextApp.handle(this);
			return;
		}
		rc.next();
	}

	@Override
	public void fail(int statusCode) {
		rc.fail(statusCode);
	}

	@Override
	public void fail(Throwable throwable) {
		rc.fail(throwable);
	}

	@Override
	public void fail(int statusCode, Throwable throwable) {
		rc.fail(statusCode, throwable);
	}

	@Override
	public RoutingContext put(String key, Object obj) {
		return rc.put(key, obj);
	}

	@Override
	public <T> T get(String key) {
		return rc.get(key);
	}

	@Override
	public <T> T get(String key, T defaultValue) {
		return rc.get(key, defaultValue);
	}

	@Override
	public <T> T remove(String key) {
		return rc.remove(key);
	}

	@Override
	public Map<String, Object> data() {
		return rc.data();
	}

	@Override
	public Vertx vertx() {
		return rc.vertx();
	}

	@Override
	public @Nullable String mountPoint() {
		return rc.mountPoint();
	}

	@Override
	public Route currentRoute() {
		return rc.currentRoute();
	}

	@Override
	public String normalizedPath() {
		return rc.normalizedPath();
	}

	@Override
	public RequestBody body() {
		return rc.body();
	}

	@Override
	public List<FileUpload> fileUploads() {
		return rc.fileUploads();
	}

	@Override
	public void cancelAndCleanupFileUploads() {
		rc.cancelAndCleanupFileUploads();
	}

	@Override
	public @Nullable Session session() {
		return rc.session();
	}

	@Override
	public boolean isSessionAccessed() {
		return rc.isSessionAccessed();
	}

	@Override
	public @Nullable User user() {
		return rc.user();
	}

	@Override
	public UserContext userContext() {
		return rc.userContext();
	}

	@Override
	public @Nullable Throwable failure() {
		return rc.failure();
	}

	@Override
	public int statusCode() {
		return rc.statusCode();
	}

	/**
	 * Get the status code of the current request.
	 *
	 * <p>
	 *     <strong>NOTE</strong> that this method should only be used to the get status code, when the response is
	 *     about to be {@link HttpServerResponse#end() ended} and a valid HTTP status code is necessary.
	 * </p>
	 *
	 * @return The status code of the current request if it has already been set, and {@link HttpResponseStatus#INTERNAL_SERVER_ERROR} otherwise
	 */
	public int sanitizedStatusCode() {
		int status = rc.statusCode();

		return status > 0 ? status : HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
	}

	@Override
	public @Nullable String getAcceptableContentType() {
		return rc.getAcceptableContentType();
	}

	@Override
	public ParsedHeaderValues parsedHeaders() {
		return rc.parsedHeaders();
	}

	@Override
	public int addHeadersEndHandler(Handler<Void> handler) {
		return rc.addHeadersEndHandler(handler);
	}

	@Override
	public boolean removeHeadersEndHandler(int handlerID) {
		return rc.removeHeadersEndHandler(handlerID);
	}

	@Override
	public int addBodyEndHandler(Handler<Void> handler) {
		return rc.addBodyEndHandler(handler);
	}

	@Override
	public boolean removeBodyEndHandler(int handlerID) {
		return rc.removeBodyEndHandler(handlerID);
	}

	@Override
	public int addEndHandler(Handler<AsyncResult<Void>> handler) {
		return rc.addEndHandler(handler);
	}

	@Override
	public boolean removeEndHandler(int handlerID) {
		return rc.removeEndHandler(handlerID);
	}

	@Override
	public boolean failed() {
		return rc.failed();
	}

	@Override
	public void setAcceptableContentType(@Nullable String contentType) {
		rc.setAcceptableContentType(contentType);
	}

	@Override
	public void reroute(HttpMethod method, String path) {
		rc
			.put(Constants.RC_REROUTE_ACTIVE, Boolean.TRUE)
			.reroute(method, path);
	}

	/**
	 * Reroute to the given path with the specified status code.
	 *
	 * @param path The path to reroute to
	 * @param status The reroute status code
	 */
	public void reroute(String path, int status) {
		rc
			.put(Constants.RC_REROUTE_ACTIVE, Boolean.TRUE)
			.put(Constants.RC_REROUTE_STATUS, status)
			.reroute(path);
	}

	/**
	 * Send a <em>303 See Other</em> response redirecting to the specified
	 * location.
	 *
	 * <p>
	 *     When the {@code redirect} is empty, this method will just call
	 *     {@link RoutingContext#next() rc.next()}.
	 * </p>
	 *
	 * @see HandlerUtils#sanitizeRedirect(String, RedirectMode)
	 * @param redirect The redirect URL or path
	 * @param mode The redirect mode
	 */
	public void redirect(String redirect, RedirectMode mode) {
		redirect(redirect, mode, HttpResponseStatus.SEE_OTHER);
	}

	/**
	 * Send a redirect response with the specified status code, und location.
	 *
	 * <p>
	 *     When the {@code redirect} is empty, this method will just call
	 *     {@link RoutingContext#next() rc.next()}.
	 * </p>
	 *
	 * <p>
	 *     The {@code status} has to be one of
	 *     <ul>
	 *         <li>301 Moved Permanently</li>
	 *         <li>302 Found</li>
	 *         <li>303 See Other</li>
	 *         <li>307 Temporary Redirect, or</li>
	 *         <li>308 Permanent Redirect, or</li>
	 *     </ul>
	 * </p>
	 *
	 * @see HandlerUtils#sanitizeRedirect(String, RedirectMode)
	 * @param redirect The redirect URL or path
	 * @param mode The redirect mode
	 * @param status The response status
	 */
	public void redirect(String redirect, RedirectMode mode, HttpResponseStatus status) {
		if (StringUtils.isBlank(redirect)) {
			log.warn("No redirect path specified, calling next handler");
			rc.next();

			return;
		}

		if (status != HttpResponseStatus.MOVED_PERMANENTLY
				&& status != HttpResponseStatus.FOUND
				&& status != HttpResponseStatus.SEE_OTHER
				&& status != HttpResponseStatus.TEMPORARY_REDIRECT
				&& status != HttpResponseStatus.PERMANENT_REDIRECT) {
			if (log.isWarnEnabled()) {
				log.warn(
					"Invalid redirect status {} {}, falling back to default",
					status.code(),
					status.reasonPhrase());
			}

			status = HttpResponseStatus.SEE_OTHER;
		}

		String sanitizedRedirect = HandlerUtils.sanitizeRedirect(redirect, mode);

		log.debug(
			"Redirecting to {} with status {} {}",
			sanitizedRedirect,
			status.code(),
			status.reasonPhrase());

		rc.response()
			.setStatusCode(status.code())
			.putHeader(HttpHeaders.LOCATION, sanitizedRedirect)
			.end();
	}

	@Override
	public List<LanguageHeader> acceptableLanguages() {
		return rc.acceptableLanguages();
	}

	@Override
	public Map<String, String> pathParams() {
		return rc.pathParams();
	}

	@Override
	public @Nullable String pathParam(String name) {
		return rc.pathParam(name);
	}

	@Override
	public MultiMap queryParams() {
		return rc.queryParams();
	}

	@Override
	public MultiMap queryParams(Charset encoding) {
		return rc.queryParams(encoding);
	}

	@Override
	public @Nullable List<String> queryParam(String query) {
		return rc.queryParam(query);
	}

	/**
	 * Set the user client
	 *
	 * @param userClient
	 *            user client
	 * @return fluent API
	 */
	public PortalRoutingContext setUserClient(MeshRestClient userClient) {
		put(USER_CLIENT_KEY, userClient);
		return this;
	}

	/**
	 * Get the user client or null if not set
	 *
	 * @return user client or null
	 */
	public MeshRestClient getUserClient() {
		return rc.get(USER_CLIENT_KEY);
	}

	/**
	 * Check whether there is an authentication handler registered.
	 *
	 * @see #enableAuthentication()
	 * @return {@code true} when an authentication handler called
	 * 		{@link #enableAuthentication()}, and {@code false} otherwise
	 */
	public boolean isAuthenticationEnabled() {
		return Boolean.TRUE.equals(rc.get(AUTH_ENABLED_KEY));
	}

	/**
	 * Set the flag to indicate that an authentication handler is available.
	 *
	 * @see #isAuthenticationEnabled()
	 * @return fluent API
	 */
	public PortalRoutingContext enableAuthentication() {
		rc.put(AUTH_ENABLED_KEY, true);

		return this;
	}

	public JsonObject nodeToRender() {
		return rc.get(Constants.RC_NODE_TO_RENDER);
	}

	/**
	 * Set the list of object ID's that should be fetched, where permissions were missing
	 *
	 * @param missingPerm
	 *            list of object ID's with missing permissions
	 * @return fluent API
	 */
	public PortalRoutingContext setMissingPerm(List<String> missingPerm) {
		if (missingPerm != null) {
			put(MISSING_PERMISSION_KEY, missingPerm);
		}
		return this;
	}

	/**
	 * Get the list of object ID's with missing permission
	 *
	 * @return list of object ID's, may be null or empty
	 */
	public List<String> getMissingPerm() {
		return rc.get(MISSING_PERMISSION_KEY);
	}

	/**
	 * Check whether permissions were missing while fetching data
	 *
	 * @return true iff permissions were missing
	 */
	public boolean hasMissingPerm() {
		List<String> missingPerm = getMissingPerm();
		return missingPerm != null && !missingPerm.isEmpty();
	}

	/**
	 * Set the http status code for which rerouting is done
	 *
	 * @deprecated Use {@link #reroute(String, int)} instead
	 * @param statusCode
	 *            rerouting status code
	 * @return fluent API
	 */
	@Deprecated
	public PortalRoutingContext setRerouteStatusCode(int statusCode) {
		put(Constants.RC_REROUTE_STATUS, statusCode);
		return this;
	}

	/**
	 * Indicates whether a {@link RoutingContext#reroute(String) rereout()}
	 * has occurred.
	 *
	 * @return {@code true} when {@code reroute()} was called, and {@code false}
	 * 		otherwise
	 */
	public boolean isRerouting() {
		return Boolean.TRUE.equals(rc.get(Constants.RC_REROUTE_ACTIVE));
	}

	/**
	 * Get the http status code for which rerouting is done, return 0 if no rerouting is done
	 *
	 * @return http status code (may be 0)
	 */
	public int getRerouteStatusCode() {
		Object rerouteStatus = rc.get(Constants.RC_REROUTE_STATUS);
		if (rerouteStatus instanceof Integer) {
			return (Integer) rerouteStatus;
		} else {
			return 0;
		}
	}

	public PortalRoutingContext setUserRolesHash(String rolesHash) {
		rc.put(USER_ROLES_KEY, rolesHash);

		return this;
	}

	public String getUserRolesHash() {
		String hash = rc.get(USER_ROLES_KEY);

		if (hash == null) {
			log.warn("User roles hash not available");

			return "missing-user-roles";
		}

		return hash;
	}

	/**
	 * Disable caching for all remaining handlers for this request.
	 *
	 * @return fluent API
	 */
	public PortalRoutingContext disableCache() {
		put(DISABLE_CACHE_KEY, Boolean.TRUE);

		return this;
	}

	/**
	 * Check whether caching is disabled for this request.
	 *
	 * @return <code>true<code> when caching is disabled, <code>false</code>otherwise
	 */
	public boolean cacheDisabled() {
		return Boolean.TRUE.equals(rc.get(DISABLE_CACHE_KEY));
	}

	/**
	 * Set the list of portal applications, through which the routing context must be passed
	 * @param apps list of applications
	 * @return fluent API
	 */
	public PortalRoutingContext setPortalApplications(List<PortalApplication> apps) {
		put(PORTAL_APPLICATIONS, apps);
		return this;
	}

	/**
	 * Set the branch to be used for getting content
	 * @param branch branch
	 * @return fluent API
	 */
	public PortalRoutingContext setBranch(BranchResponse branch) {
		put(BRANCH_KEY, branch);
		return this;
	}

	/**
	 * Get the branch or null, if none set
	 * @return branch (or null)
	 */
	public BranchResponse getBranch() {
		return rc.get(BRANCH_KEY);
	}

	/**
	 * Set the flag that navigation is needed for this request.
	 * @return fluent API
	 */
	public PortalRoutingContext enableNavigation() {
		put(ENABLE_NAVIGATION_KEY, Boolean.TRUE);

		return this;
	}

	/**
	 * Checks whether navigation is needed for this request
	 * @return <code>true</code> if navigation is needed for this request, and <code>false</code> otherwise
	 */
	public boolean navigationEnabled() {
		return Boolean.TRUE.equals(rc.get(ENABLE_NAVIGATION_KEY));
	}

	/**
	 * Set the navigation for this request.
	 * @param navigation The navigation for this request
	 * @return fluent API
	 */
	public PortalRoutingContext setNavigation(JsonArray navigation) {
		put(NAVIGATION_KEY, navigation);

		return this;
	}

	/**
	 * Get the navigation for this request.
	 * @return The navigation for this request
	 */
	public JsonArray getNavigation() {
		return rc.get(NAVIGATION_KEY);
	}

	/**
	 * Set the preview mode for this request to the specified mode.
	 *
	 * <p>
	 *     The mode will only be set when it is either <code>PREVIEW</code>, or <code>EDIT</code>.
	 * </p>
	 *
	 * @param compatibility The portal compatibility options
	 * @return fluent API
	 */
	public PortalRoutingContext enablePreview(CompatibilityOptions compatibility) {
		String modeName = rc.request().getParam(PARAM_RENDER_MODE);
		// Older versions of GCMS did not provide information whether a preview was for actual preview or editmode.
		// In this case we should not set the preview mode to UNKNOWN as this looks very strange in handlebars
		// templates which use the {{# renderMode }} helper.
		PreviewMode defaultMode = compatibility.isPreview() ? PreviewMode.PREVIEW : PreviewMode.UNKNOWN;
		PreviewMode mode = getEnumValue(PreviewMode.values(), modeName).orElse(defaultMode);

		if (mode != PreviewMode.PREVIEW && mode != PreviewMode.EDIT) {
			log.warn("Unexpected preview mode: {}", mode);
		} else {
			log.debug("Setting preview mode: {}", mode);
		}

		put(PREVIEW_KEY, mode);

		disableCache();

		return this;
	}

	/**
	 * Check whether this request is for a preview.
	 * @return <code>true</code> if the request is for a preview, and <code>false</code> otherwise
	 */
	public boolean isPreview() {
		return rc.get(PREVIEW_KEY) != null;
	}


	/**
	 * Set the used language for this request.
	 *
	 * @param lang The language for this request
	 * @param source The source of the detected language
	 * @return fluent API
	 */
	public PortalRoutingContext setLanguage(String lang, LanguageSource source) {
		if (StringUtils.isBlank(lang)) {
			log.debug("Refusing to unset language");

			return this;
		}

		if (source == null) {
			log.warn("No language source specified, falling back to default");

			source = LanguageSource.DEFAULT;
		}

		if (source.hasHigherPriority(rc.get(LANGUAGE_SOURCE_KEY))) {
			log.debug("Setting language to \"{}\" from {}", lang, source);

			rc.put(Constants.RC_LANGUAGE, lang.toLowerCase());
			rc.put(LANGUAGE_SOURCE_KEY, source);
		} else {
			log.debug("Ignoring lower priority  language \"{}\" from {}", lang, source);
		}

		return this;
	}

	/**
	 * Add the language cookie iff the language for the current request is not a
	 * default fallback, and the current value of the cookie differs from the
	 * current language.
	 *
	 * @param cookieProvider The cookie provider to use
	 * @return Fluent API
	 */
	public PortalRoutingContext setLanguageCookie(CookieProvider cookieProvider) {
		LanguageSource source = rc.get(LANGUAGE_SOURCE_KEY);

		if (source != null && source.isNonDefault()) {
			Cookie cookie = cookieProvider.get(rc, Constants.COOKIE_LANG);
			String lang = getLanguage();

			if (cookie == null || !cookie.getValue().equals(lang)) {
				log.debug("Setting language cookie: {} (source: {})", lang, source);

				response().addCookie(cookieProvider.create(Constants.COOKIE_LANG, lang));
			}
		}

		return this;
	}

	/**
	 * Add the language cookie iff the language for the current request is not a default fallback, and the current
	 * value of the cookie differs from the current language.
	 *
	 * @deprecated Use {@link #setLanguageCookie(CookieProvider)} instead
	 * @return Fluent API
	 */
	@Deprecated
	public PortalRoutingContext setLanguageCookie(boolean secure) {
		LanguageSource source = rc.get(LANGUAGE_SOURCE_KEY);

		if (source != null && source.isNonDefault()) {
			Cookie cookie = rc.request().getCookie(Constants.COOKIE_LANG);
			String lang = getLanguage();

			if (cookie == null || !cookie.getValue().equals(lang)) {
				log.debug("Setting language cookie: {} (source: {})", lang, source);

				cookie = Cookie.cookie(Constants.COOKIE_LANG, lang)
					.setHttpOnly(true)
					.setSecure(secure)
					.setSameSite(CookieSameSite.STRICT)
					.setPath("/");

				rc.response().addCookie(cookie);
			}
		}

		return this;
	}

	/**
	 * Get the language for the current request.
	 * @return The language for the current request
	 */
	public String getLanguage() {
		String lang = rc.get(Constants.RC_LANGUAGE);

		// TODO can we get the portal default language easily here?
		return lang == null ? "en" : lang;
	}

	/**
	 * Get the language of the currently rendered node.
	 *
	 * <p>
	 *     If the language of the rendered node cannot be determined, the result
	 *     will be the return value of {@link #getLanguage()}.
	 * </p>
	 *
	 * <p>
	 *     <strong>Note</strong> that this method can only return a different
	 *     value than {@link #getLanguage()} when it is called <em>after</em>
	 *     the {@link com.gentics.mesh.portal.api.RouteOrder#CONTENT_ROUTES}.
	 * </p>
	 *
	 * @return The language of the currently rendered node, or
	 * 		{@code getLangauge()} as fallback
	 */
	public String getContentLanguage() {
		Optional<String> lang = Optional.ofNullable(nodeToRender())
			.map(node -> node.getString("language"));

		return lang.orElseGet(this::getLanguage);
	}

	/**
	 * Get VersioningParameters instance to get published content of the currently set branch (or default branch, if no branch set)
	 * @return param instance
	 */
	public VersioningParameters getVersion() {
		VersioningParameters param = new VersioningParametersImpl().published();
		BranchResponse branch = getBranch();
		if (branch != null) {
			param.setBranch(branch.getName());
		}
		return param;
	}
}
