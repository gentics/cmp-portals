package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

import io.vertx.core.json.JsonObject;

/**
 * Proxy configuration.
 *
 * <p>
 *     The proxy configuration is not used by the portal at the moment, so all environment variable annotations
 *     have been disabled.
 * </p>
 */
public class ProxyOptions implements OverridableOptions {

	public static final String PROXY_HOST_ENV = "PROXY_HOST";

	public static final String PROXY_PORT_ENV = "PROXY_PORT";

	public static final String PROXY_USER_ENV = "PROXY_USER";

	public static final String PROXY_PASSWORD_ENV = "PROXY_PASSWORD";

	public static final String PROXY_ACTIVE_ENV = "PROXY_ACTIVE";

	@JsonProperty
	@JsonPropertyDescription("Proxy host.")
	// @EnvironmentVariable(name = PROXY_HOST_ENV, description = "Override the proxy host.")
	private String host;

	@JsonProperty
	@JsonPropertyDescription("Proxy port.")
	// @EnvironmentVariable(name = PROXY_PORT_ENV, description = "Override the proxy port.")
	private String port;

	@JsonProperty
	@JsonPropertyDescription("Proxy username.")
	// @EnvironmentVariable(name = PROXY_USER_ENV, description = "Override the proxy username.")
	private String user;

	@JsonProperty
	@JsonPropertyDescription("Proxy password.")
	// @EnvironmentVariable(name = PROXY_PASSWORD_ENV, description = "Override the proxy password.")
	private String password;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Flag to activate proxy.")
	// @EnvironmentVariable(name = PROXY_ACTIVE_ENV, description = "Override the proxy active flag.")
	private boolean active = false;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	@Override
	public String toString() {
		return JsonObject.mapFrom(this).encodePrettily();
	}

}
