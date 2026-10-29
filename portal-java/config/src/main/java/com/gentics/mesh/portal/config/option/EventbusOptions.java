package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.api.config.OverridableOptions;

public class EventbusOptions implements OverridableOptions {
	public static final String MESH_EVENTBUS_PING_INTERVAL_ENV = "MESH_EVENTBUS_PING_INTERVAL";

	public static final String MESH_EVENTBUS_RETRY_TIMEOUT_ENV = "MESH_EVENTBUS_RETRY_TIMEOUT";

	public static final int DEFAULT_PING_INTERVAL = 100;

	public static final int DEFAULT_RETRY_TIMEOUT = 5000;

	@JsonProperty(defaultValue = "100")
	@JsonPropertyDescription("Eventbus ping interval in ms.")
	@EnvironmentVariable(name = MESH_EVENTBUS_PING_INTERVAL_ENV, description = "Override the eventbus ping interval.")
	private int pingInterval = DEFAULT_PING_INTERVAL;

	@JsonProperty(defaultValue = "5000")
	@JsonPropertyDescription("Eventbus retry timeout in ms.")
	@EnvironmentVariable(name = MESH_EVENTBUS_RETRY_TIMEOUT_ENV, description = "Override the eventbus connection retry interval.")
	private int retryTimeout = DEFAULT_RETRY_TIMEOUT;

	public int getPingInterval() {
		return pingInterval;
	}

	public void setPingInterval(int pingInterval) {
		this.pingInterval = pingInterval;
	}

	public int getRetryTimeout() {
		return retryTimeout;
	}

	public void setRetryTimeout(int retryTimeout) {
		this.retryTimeout = retryTimeout;
	}
}
