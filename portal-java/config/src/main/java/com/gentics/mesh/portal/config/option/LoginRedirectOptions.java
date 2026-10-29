package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

import javax.annotation.Nonnull;

public class LoginRedirectOptions implements Config {

	public static final String DEFAULT_PATH = "/auth/loggedin";
	public static final String DEFAULT_REDIRECT = "/";

	public static final String ENV_LOGIN_REDIRECT_PATH = "GPJ_LOGIN_REDIRECT_PATH";
	public static final String ENV_LOGIN_REDIRECT_DEFAULT = "GPJ_LOGIN_REDIRECT_DEFAULT";
	public static final String ENV_LOGIN_REDIRECT_CMS_PAGE = "GPJ_LOGIN_REDIRECT_CMS_PAGE";

	@JsonProperty
	@JsonPropertyDescription("The path the portal will redirect to after a successful login. This may be a path for a custom handler, or to a page from the CMS. The handler processing this request should use the AUTH_REDIRECT cookie value.")
	@EnvironmentVariable(name = ENV_LOGIN_REDIRECT_PATH, description = "Override the redirect after successful login. (Default: " + DEFAULT_PATH + ")")
	private String path = DEFAULT_PATH;

	@JsonProperty
	@JsonPropertyDescription("The default redirect when the AUTH_REDIRECT cookie is not set")
	@EnvironmentVariable(name = ENV_LOGIN_REDIRECT_DEFAULT, description = "Override the default redirect when the AUTH_REDIRECT cookie is not set. (Default: /)")
	private String defaultRedirect = DEFAULT_REDIRECT;

	@JsonProperty
	@JsonPropertyDescription("Whether the redirect is for a page from the CMS. If so the portal will try language fallback, and instruct the LoginRedirectHandler to do nothing. (Default: false)")
	@EnvironmentVariable(name = ENV_LOGIN_REDIRECT_CMS_PAGE, description = "Override whether the redirect is a path to a CMS page. (Default: false)")
	private boolean cmsPage = false;

	public LoginRedirectOptions() {
	}

	@JsonCreator
	public LoginRedirectOptions(String path) {
		this.path = path;
	}

	@Nonnull
	public String getPath() {
		if (path == null) {
			path = DEFAULT_PATH;
		}

		return path;
	}

	public LoginRedirectOptions setPath(String path) {
		this.path = path;

		return this;
	}

	public String getDefaultRedirect() {
		if (defaultRedirect == null) {
			defaultRedirect = DEFAULT_REDIRECT;
		}

		return defaultRedirect;
	}

	public LoginRedirectOptions setDefaultRedirect(String defaultRedirect) {
		this.defaultRedirect = defaultRedirect;

		return this;
	}

	public boolean isCmsPage() {
		return cmsPage;
	}

	public LoginRedirectOptions setCmsPage(boolean cmsPage) {
		this.cmsPage = cmsPage;

		return this;
	}
}
