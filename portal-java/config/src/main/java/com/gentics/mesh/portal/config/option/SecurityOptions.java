package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import io.vertx.core.http.CookieSameSite;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SecurityOptions implements Config {

	public static final String API_KEY_ENV = "PORTAL_SEC_API_KEY";
	public static final String ALLOWED_IPS_ENV = "PORTAL_SEC_ALLOWED_IPS";
	public static final String USE_COOKIE_HOST_PREFIX_ENV = "PORTAL_SEC_COOKIE_HOST_PREFIX";
	private static final String ENCRYPT_COOKIES_ENV = "AUTH_ENCRYPT_COOKIES";
	private static final String ENCRYPTION_MODE_ENV = "PORTAL_SECURITY_ENCRYPTION_MODE";
	private static final String ENCRYPTION_PASSWORD_ENV = "PORTAL_SECURITY_ENCRYPTION_PASSWORD";
	public static final String COOKIE_SAME_SITE_ENV = "AUTH_COOKIE_SAME_SITE";

	private static final boolean DEFAULT_USE_COOKIE_HOST_PREFIX = false;
	private static final EncryptionMode DEFAULT_ENCRYPTION_MODE = EncryptionMode.DISABLED;
	public static final CookieSameSite DEFAULT_COOKIE_SAME_SITE = CookieSameSite.STRICT;

	@JsonProperty(defaultValue = "None")
	@JsonPropertyDescription("The API key required by some backend API endpoints")
	@EnvironmentVariable(name = API_KEY_ENV, description = "Override the API key")
	private String apiKey;

	@JsonProperty(defaultValue = "Empty (no IPs allowed)")
	@JsonPropertyDescription("Allowed IPs which can access backend API endpoints of the portal")
	@EnvironmentVariable(name = ALLOWED_IPS_ENV, description = "Override the list of allowed IPs to access backend API endpoints")
	private Set<String> allowedIps = new HashSet<>();

	@JsonProperty(defaultValue = "No additional headers")
	@JsonPropertyDescription("Additional HTTP headers added to each response")
	private Map<String, String> additionalResponseHeaders = new HashMap<>();

	@JsonProperty(defaultValue = "" + DEFAULT_USE_COOKIE_HOST_PREFIX)
	@JsonPropertyDescription("Whether secure cookie names will be prefixed with \"__Host-\"")
	@EnvironmentVariable(name = USE_COOKIE_HOST_PREFIX_ENV, description = "Override whether secure cookies will be prefixed with \"__Host-\"")
	private boolean useHostCookiePrefix = DEFAULT_USE_COOKIE_HOST_PREFIX;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether to encrypt the authentication cookies")
	@EnvironmentVariable(name = ENCRYPT_COOKIES_ENV, description = "Override whether to encrypt authentication cookies.")
	private boolean encryptCookies = false;

	@JsonProperty(defaultValue = "DISABLED")
	@JsonPropertyDescription("How keys for encryption are generated. DISABLED means no encryption, PASSWORD_ONLY_KEY uses the configured password, AUTO_GENERATED_KEY creates a secret key during portal startup.")
	@EnvironmentVariable(name = ENCRYPTION_MODE_ENV, description = "Override the encryption mode.")
	private EncryptionMode encryptionMode = DEFAULT_ENCRYPTION_MODE;

	@JsonProperty(defaultValue = "(empty)")
	@JsonPropertyDescription("The password to use for encryption. Only used when encryptionMode is set to PASSWORD_ONLY_KEY.")
	@EnvironmentVariable(name = ENCRYPTION_PASSWORD_ENV, description = "Override the encryption password")
	private String encryptionPassword = null;

	@JsonProperty(defaultValue = "STRICT")
	@JsonPropertyDescription("The SameSite policy to use for cookies (NONE, LAX or STRICT)")
	@EnvironmentVariable(name = COOKIE_SAME_SITE_ENV, description = "Override the SameSite policy for cookies. (Default: STRICT)")
	private CookieSameSite cookieSameSitePolicy = DEFAULT_COOKIE_SAME_SITE;

	public String getApiKey() {
		return apiKey;
	}

	public SecurityOptions setApiKey(String apiKey) {
		this.apiKey = apiKey;

		return this;
	}

	@Nonnull
	public Set<String> getAllowedIps() {
		if (allowedIps == null) {
			allowedIps = Collections.emptySet();
		}

		return allowedIps;
	}

	public SecurityOptions setAllowedIps(Set<String> allowedIps) {
		this.allowedIps = allowedIps;

		return this;
	}

	@Nonnull
	public Map<String, String> getAdditionalResponseHeaders() {
		if (additionalResponseHeaders == null) {
			additionalResponseHeaders = Collections.emptyMap();
		}

		return additionalResponseHeaders;
	}

	public SecurityOptions setAdditionalResponseHeaders(Map<String, String> additionalResponseHeaders) {
		this.additionalResponseHeaders = additionalResponseHeaders;

		return this;
	}

	public boolean isUseHostCookiePrefix() {
		return useHostCookiePrefix;
	}

	public SecurityOptions setUseHostCookiePrefix(boolean useHostCookiePrefix) {
		this.useHostCookiePrefix = useHostCookiePrefix;

		return this;
	}

	public boolean isEncryptCookies() {
		return encryptCookies;
	}

	public SecurityOptions setEncryptCookies(boolean encryptCookies) {
		this.encryptCookies = encryptCookies;

		return this;
	}

	public EncryptionMode getEncryptionMode() {
		if (encryptionMode == null) {
			encryptionMode = DEFAULT_ENCRYPTION_MODE;
		}

		return encryptionMode;
	}

	public SecurityOptions setEncryptionMode(EncryptionMode encryptionMode) {
		this.encryptionMode = encryptionMode;

		return this;
	}

	public String getEncryptionPassword() {
		return encryptionPassword;
	}

	public SecurityOptions setEncryptionPassword(String encryptionPassword) {
		this.encryptionPassword = encryptionPassword;

		return this;
	}


	public CookieSameSite getCookieSameSitePolicy() {
		if (cookieSameSitePolicy == null) {
			cookieSameSitePolicy = DEFAULT_COOKIE_SAME_SITE;
		}

		return cookieSameSitePolicy;
	}

	public void setCookieSameSitePolicy(CookieSameSite cookieSameSitePolicy) {
		this.cookieSameSitePolicy = cookieSameSitePolicy;
	}

	@Override
	public void validate() {
		if (getEncryptionMode() == EncryptionMode.PASSWORD_ONLY_KEY && StringUtils.isEmpty(encryptionPassword)) {
			throw new InvalidConfigurationException(
				"authentication.encryptionPassword",
				"Password cannot be empty when encryption mode is set to PASSWORD_ONLY_KEY");
		}

		if (getEncryptionMode() == EncryptionMode.DISABLED && encryptCookies) {
			throw new InvalidConfigurationException(
				"authentication.encryptCookies",
				"Encryption of authentication cookies not possible when encryption mode is DISABLED");
		}
	}
}
