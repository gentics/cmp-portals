package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

/**
 * Mailserver configuration options.
 *
 * <p>
 *     The mail configuration is not used by the portal at the moment, so all environment variable annotations
 *     have been disabled.
 * </p>
 */
public class MailOptions implements OverridableOptions {

	public static final String MAIL_PORT_ENV = "MAIL_PORT";
	public static final String MAIL_HOSTNAME_ENV = "MAIL_HOSTNAME";
	public static final String MAIL_AUTH_METHODS_ENV = "MAIL_AUTH_METHODS";
	public static final String MAIL_AUTH_USER_ENV = "MAIL_AUTH_USERNAME";
	public static final String MAIL_AUTH_PASS_ENV = "MAIL_AUTH_PASSWORD";
	public static final String MAIL_SSL_ENV = "MAIL_SSL";
	public static final String MAIL_TRUST_ALL_ENV = "MAIL_TRUST_ALL";
	public static final String MAIL_OWN_HOSTNAME_ENV = "MAIL_OWN_HOSTNAME";
	public static final String MAIL_MAX_POOL_SIZE_ENV = "MAIL_MAX_POOL_SIZE";
	public static final String MAIL_KEEP_ALIVE_ENV = "MAIL_KEEP_ALIVE";

	public static final int DEFAULT_PORT = 25;
	public static final String DEFAULT_PORT_STRING = "25";
	public static final String DEFAULT_HOST = "localhost";
	public static final int DEFAULT_MAX_POOL_SIZE = 10;
	public static final boolean DEFAULT_SSL = false;
	public static final boolean DEFAULT_TRUST_ALL = false;
	public static final boolean DEFAULT_ALLOW_RCPT_ERRORS = false;
	public static final boolean DEFAULT_KEEP_ALIVE = true;
	public static final boolean DEFAULT_DISABLE_ESMTP = false;

	@JsonProperty(defaultValue = DEFAULT_HOST)
	@JsonPropertyDescription("Mail server host.")
	// @EnvironmentVariable(name = MAIL_HOSTNAME_ENV, description = "Override the mail server host.")
	private String hostname = DEFAULT_HOST;

	@JsonProperty(defaultValue = DEFAULT_PORT_STRING)
	@JsonPropertyDescription("Mail server port.")
	// @EnvironmentVariable(name = MAIL_PORT_ENV, description = "Override the mail server port.")
	private int port = DEFAULT_PORT;

	@JsonProperty
	@JsonPropertyDescription("Mail server auth methods.")
	// @EnvironmentVariable(name = MAIL_AUTH_METHODS_ENV, description = "Override the mail server auth methods.")
	private String authMethods;

	@JsonProperty
	@JsonPropertyDescription("Mail server username.")
	// @EnvironmentVariable(name = MAIL_AUTH_USER_ENV, description = "Override the mail server username.")
	private String username;

	@JsonProperty
	@JsonPropertyDescription("Mail server password.")
	// @EnvironmentVariable(name = MAIL_AUTH_PASS_ENV, description = "Override the mail server password.")
	private String password;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Mail server SSL flag.")
	// @EnvironmentVariable(name = MAIL_SSL_ENV, description = "Override the mail server ssl flag.")
	private boolean ssl = DEFAULT_SSL;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Mail server trust all flag.")
	// @EnvironmentVariable(name = MAIL_TRUST_ALL_ENV, description = "Override the mail server trust all flag.")
	private boolean trustAll = DEFAULT_TRUST_ALL;

	@JsonProperty
	@JsonPropertyDescription("Mail server own hostname setting.")
	// @EnvironmentVariable(name = MAIL_OWN_HOSTNAME_ENV, description = "Override the mail server own hostname setting.")
	private String ownHostname;

	@JsonProperty(defaultValue = "10")
	@JsonPropertyDescription("Mail server maximum connection pool size.")
	// @EnvironmentVariable(name = MAIL_MAX_POOL_SIZE_ENV, description = "Override the mail server connection max pool size.")
	private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Mail server keep alive flag.")
	// @EnvironmentVariable(name = MAIL_KEEP_ALIVE_ENV, description = "Override the mail server keep alive flag.")
	private boolean keepAlive = DEFAULT_KEEP_ALIVE;

	public MailOptions() {
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getAuthMethods() {
		return authMethods;
	}

	public void setAuthMethods(String authMethods) {
		this.authMethods = authMethods;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public boolean isTrustAll() {
		return trustAll;
	}

	public void setTrustAll(boolean trustAll) {
		this.trustAll = trustAll;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	public int getMaxPoolSize() {
		return maxPoolSize;
	}

	public void setMaxPoolSize(int maxPoolSize) {
		this.maxPoolSize = maxPoolSize;
	}

	public String getOwnHostname() {
		return ownHostname;
	}

	public void setOwnHostname(String ownHostname) {
		this.ownHostname = ownHostname;
	}
}
