package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.config.PortalConfig;
import io.reactivex.annotations.NonNull;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class AuthenticationOptions implements Config {

	public static final String LOGIN_ENDPOINT_ENV = "AUTH_LOGIN_ENDPOINT";
	public static final String LOGOUT_ENDPOINT_ENV = "AUTH_LOGOUT_ENDPOINT";
	public static final String INSECURE_SSL_ENV = "AUTH_INSECURE_SSL";
	public static final String LOGOUT_REDIRECT_ENV = "AUTH_LOGOUT_REDIRECT";
	public static final String REDIRECT_COOKIE_MAX_AGE_ENV = "AUTH_REDIRECT_COOKIE_MAX_AGE";
	public static final String USE_UNAUTHORIZED_REDIRECT_ENV = "AUTH_USE_UNAUTHORIZED_REDIRECT";
	public static final String USE_SESSION_COOKIES_ENV = "AUTH_USE_SESSION_COOKIES";

	public static final String DEFAULT_LOGIN_ENDPOINT = "/auth/login";
	public static final String DEFAULT_LOGOUT_ENDPOINT = "/auth/logout";
	public static final String DEFAULT_LOGOUT_REDIRECT = "/";
	public static final boolean DEFAULT_INSECURE_SSL = false;
	public static final int DEFAULT_REDIRECT_COOKIE_MAX_AGE = -1;
	public static final boolean DEFAULT_UNAUTHORIZED_REDIRECT = false;
	public static final boolean DEFAULT_USE_SESSION_COOKIES = false;


	@JsonProperty(defaultValue = DEFAULT_LOGIN_ENDPOINT)
	@JsonPropertyDescription("Path to the API endpoint that can be called to start login procedure.")
	@EnvironmentVariable(name = LOGIN_ENDPOINT_ENV, description = "The path of the login endpoint. Default: " + DEFAULT_LOGIN_ENDPOINT)
	private String loginEndpoint = DEFAULT_LOGIN_ENDPOINT;

	@JsonProperty(defaultValue = DEFAULT_LOGOUT_ENDPOINT)
	@JsonPropertyDescription("Path to the API endpoint that can be called to start logout procedure.")
	@EnvironmentVariable(name = LOGOUT_ENDPOINT_ENV, description = "The path of the logout endpoint. Default: " + DEFAULT_LOGOUT_ENDPOINT)
	private String logoutEndpoint = DEFAULT_LOGOUT_ENDPOINT;

	@JsonProperty(defaultValue = "" + DEFAULT_INSECURE_SSL)
	@JsonPropertyDescription("Flag to allow insecure SSL connections to the OAuth2 server.")
	@EnvironmentVariable(name = INSECURE_SSL_ENV, description = "'true' if insecure SSL connections should be trusted. Default: " + DEFAULT_INSECURE_SSL)
	private boolean insecureSsl = DEFAULT_INSECURE_SSL;

	@JsonProperty(defaultValue = DEFAULT_LOGOUT_REDIRECT)
	@JsonPropertyDescription("Path to which the user will be redirected after logout. The default is to redirect to /")
	@EnvironmentVariable(name = LOGOUT_REDIRECT_ENV, description = "The url to redirect after logout.")
	private String logoutRedirect = DEFAULT_LOGOUT_REDIRECT;

	@JsonProperty(defaultValue = DEFAULT_REDIRECT_COOKIE_MAX_AGE + " (session cookie)")
	@JsonPropertyDescription("Maximum age of the authentication redirect cookie in seconds, or -1 for session cookies.")
	@EnvironmentVariable(name = REDIRECT_COOKIE_MAX_AGE_ENV, description = "The max age of the redirect cookie in seconds. Default: session cookie")
	private int redirectCookieMaxAge = DEFAULT_REDIRECT_COOKIE_MAX_AGE;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether to redirect anonymous requests to protected resources to the 401 error page")
	@EnvironmentVariable(name = USE_UNAUTHORIZED_REDIRECT_ENV, description = "Override whether to redirect anonymous requests to protected resources to the 401 error page")
	private boolean useUnauthorizedRedirect = DEFAULT_UNAUTHORIZED_REDIRECT;

	// TODO Here for backwards compatibility. Remove in future version.
	@Deprecated
	private boolean redirectCookieSameSiteStrict = true;

	@JsonProperty("protectedEndpoints")
	@JsonPropertyDescription("List of endpoints that are protected.")
	private List<EndpointOptions> endPoints = new ArrayList<>();

	@JsonProperty("keycloak")
	@JsonPropertyDescription("Keycloak specific authentication options")
	private KeycloakOptions keycloakOptions;

	@JsonProperty("singleSignOut")
	@JsonPropertyDescription("Single sign out configuration")
	private SingleSignOutOptions singleSignOutOptions = new SingleSignOutOptions();

	@JsonProperty("loginRedirect")
	@JsonPropertyDescription("Configuration for redirects after a successful login")
	private LoginRedirectOptions loginRedirectOptions = new LoginRedirectOptions();

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Ignore the max age from original authentication cookies and create session cookies instead")
	@EnvironmentVariable(name = USE_SESSION_COOKIES_ENV, description = "Override whether to ignore the max age from original authentication cookies and create session cookies instead")
	private boolean useSessionCookies = DEFAULT_USE_SESSION_COOKIES;

	@Deprecated
	private String checkEndpoint = null;

	@Deprecated
	private int maxTemporaryEntries;

	@Deprecated
	private String callbackHost;

	@Deprecated
	private String callbackEndpoint;

	@Deprecated
	private String notAuthorisedPage;

	private String makeAbsolute(String path, String defaultValue) {
		if (path == null) {
			return defaultValue;
		}

		if (!path.matches("^(?:https?://|/).*")) {
			return "/" + path;
		}

		return path;
	}

	@JsonIgnore
	public boolean isDefault() {
		return DEFAULT_LOGIN_ENDPOINT.equals(loginEndpoint)
			&& DEFAULT_LOGOUT_ENDPOINT.equals(logoutEndpoint)
			&& DEFAULT_LOGOUT_REDIRECT.equals(logoutRedirect)
			&& DEFAULT_INSECURE_SSL == insecureSsl
			&& endPoints.isEmpty()
			&& DEFAULT_REDIRECT_COOKIE_MAX_AGE == redirectCookieMaxAge
			&& singleSignOutOptions != null
			&& singleSignOutOptions.isDefault();

	}

	/** @deprecated Moved to {@link KeycloakOptions#getCallbackPath()}. */
	@Deprecated
	public String getCallbackEndpoint() {
		return callbackEndpoint;
	}

	@Deprecated
	public void setCallbackEndpoint(String callbackEndpoint) {
		this.callbackEndpoint = callbackEndpoint;
	}

	/**
	 * @deprecated This entry will not be used. Set
	 * {@link PortalConfig#getServerUrl() serverUrl} accordingly instead.
	 */
	@Deprecated
	public String getCallbackHost() {
		return callbackHost;
	}

	@Deprecated
	public void setCallbackHost(String callbackHost) {
		this.callbackHost = callbackHost;
	}

	/** @deprecated No longer used. */
	@Deprecated
	public String getCheckEndpoint() {
		return checkEndpoint;
	}

	@Deprecated
	public void setCheckEndpoint(String checkEndpoint) {
		this.checkEndpoint = checkEndpoint;
	}

	public String getLoginEndpoint() {
		if (StringUtils.isBlank(loginEndpoint)) {
			loginEndpoint = DEFAULT_LOGIN_ENDPOINT;
		}

		return loginEndpoint;
	}

	public void setLoginEndpoint(String loginEndpoint) {
		this.loginEndpoint = makeAbsolute(loginEndpoint, DEFAULT_LOGIN_ENDPOINT);
	}

	public void setLogoutEndpoint(String logoutEndpoint) {
		this.logoutEndpoint = makeAbsolute(logoutEndpoint, DEFAULT_LOGOUT_ENDPOINT);
	}

	public String getLogoutEndpoint() {
		if (StringUtils.isBlank(loginEndpoint)) {
			loginEndpoint = DEFAULT_LOGOUT_ENDPOINT;
		}

		return logoutEndpoint;
	}

	public String getLogoutRedirect() {
		if (StringUtils.isBlank(logoutRedirect)) {
			logoutRedirect = DEFAULT_LOGOUT_REDIRECT;
		}

		return logoutRedirect;
	}

	public void setLogoutRedirect(String logoutRedirect) {
		this.logoutRedirect = makeAbsolute(logoutRedirect, DEFAULT_LOGOUT_REDIRECT);
	}

	/** @deprecated Configure a 401/403 redirect instead */
	@Deprecated
	public String getNotAuthorisedPage() {
		return notAuthorisedPage;
	}

	@Deprecated
	public void setNotAuthorisedPage(String notAuthorisedPage) {
		this.notAuthorisedPage = notAuthorisedPage;
	}

	public List<EndpointOptions> getEndPoints() {
		return endPoints;
	}

	public void setEndPoints(List<EndpointOptions> endPoints) {
		this.endPoints = endPoints;
	}

	public boolean isInsecureSsl() {
		return insecureSsl;
	}

	public void setInsecureSsl(boolean insecureSsl) {
		this.insecureSsl = insecureSsl;
	}

	/** @deprecated No longer needed. */
	@Deprecated
	public int getMaxTemporaryEntries() {
		return maxTemporaryEntries;
	}

	@Deprecated
	public void setMaxTemporaryEntries(int maxTemporaryEntries) {
		this.maxTemporaryEntries = maxTemporaryEntries;
	}

	public int getRedirectCookieMaxAge() {
		return redirectCookieMaxAge;
	}

	public void setRedirectCookieMaxAge(int redirectCookieMaxAge) {
		this.redirectCookieMaxAge = redirectCookieMaxAge;
	}

	public boolean isUseUnauthorizedRedirect() {
		return useUnauthorizedRedirect;
	}

	public AuthenticationOptions setUseUnauthorizedRedirect(boolean useUnauthorizedRedirect) {
		this.useUnauthorizedRedirect = useUnauthorizedRedirect;

		return this;
	}

	public boolean isRedirectCookieSameSiteStrict() {
		return redirectCookieSameSiteStrict;
	}

	/**
	 * @deprecated Not used anymore, use {@link SecurityOptions#setCookieSameSitePolicy(CookieSameSite)} instead.
	 * @param redirectCookieSameSiteStrict
	 */
	@Deprecated
	public void setRedirectCookieSameSiteStrict(boolean redirectCookieSameSiteStrict) {
		this.redirectCookieSameSiteStrict = redirectCookieSameSiteStrict;
	}

	public SingleSignOutOptions getSingleSignOutOptions() {
		return singleSignOutOptions;
	}

	public void setSingleSignOutOptions(SingleSignOutOptions singleSignOutOptions) {
		this.singleSignOutOptions = singleSignOutOptions;
	}

	@Nonnull
	public LoginRedirectOptions getLoginRedirectOptions() {
		if (loginRedirectOptions == null) {
			loginRedirectOptions = new LoginRedirectOptions();
		}

		return loginRedirectOptions;
	}

	public AuthenticationOptions setLoginRedirectOptions(LoginRedirectOptions loginRedirectOptions) {
		this.loginRedirectOptions = loginRedirectOptions;

		return this;
	}

	@NonNull
	public KeycloakOptions getKeycloakOptions() {
		if (keycloakOptions == null) {
			keycloakOptions = new KeycloakOptions();
		}

		return keycloakOptions;
	}

	public AuthenticationOptions setKeycloakOptions(KeycloakOptions keycloakOptions) {
		this.keycloakOptions = keycloakOptions;

		return this;
	}

	@Override
	public void overrideWithEnv() {
		Config.super.overrideWithEnv();

		getKeycloakOptions().overrideWithEnv();
		getSingleSignOutOptions().overrideWithEnv();
	}

	@Override
	public void validate() {
		getKeycloakOptions().validate();
		getLoginRedirectOptions().validate();
	}

	@Override
	public String toString() {
		return JsonObject.mapFrom(this).encodePrettily();
	}

	public boolean isUseSessionCookies() {
		return useSessionCookies;
	}

	public void setUseSessionCookies(boolean useSessionCookies) {
		this.useSessionCookies = useSessionCookies;
	}
}
