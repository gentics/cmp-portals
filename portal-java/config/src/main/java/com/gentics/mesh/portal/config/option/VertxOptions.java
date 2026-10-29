package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

public class VertxOptions implements Config {

	public static final String PORTAL_WORKER_POOL_SIZE_ENV = "PORTAL_WORKER_POOL_SIZE";
	public static final String PORTAL_EVENT_POOL_SIZE_ENV = "PORTAL_EVENT_POOL_SIZE";
	public static final String PORTAL_DEVELOPMENT_MODE_ENV = "PORTAL_DEVELOPMENT_MODE";

	@JsonProperty(defaultValue = "" + io.vertx.core.VertxOptions.DEFAULT_WORKER_POOL_SIZE)
	@JsonPropertyDescription("The thread pool size for worker verticles")
	@EnvironmentVariable(name = PORTAL_WORKER_POOL_SIZE_ENV, description = "Override the Vert.x worker pool size")
	private int workerPoolSize = io.vertx.core.VertxOptions.DEFAULT_WORKER_POOL_SIZE;

	@JsonProperty(defaultValue = "Number of CPUs times two")
	@JsonPropertyDescription("The thread pool size for standard verticles")
	@EnvironmentVariable(name = PORTAL_EVENT_POOL_SIZE_ENV, description = "Override the Vert.x event loop pool size")
	private int eventPoolSize = io.vertx.core.VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;

	// The @JsonProperty annotation is omitted on purpose, because this setting should not show up in the documentation.
	// Enabling development mode will increase the blocked thread check interval to facilitate debugging
	private boolean developmentMode = false;

	public int getWorkerPoolSize() {
		return workerPoolSize;
	}

	public void setWorkerPoolSize(int workerPoolSize) {
		this.workerPoolSize = workerPoolSize;
	}

	public int getEventPoolSize() {
		return eventPoolSize;
	}

	public void setEventPoolSize(int eventPoolSize) {
		this.eventPoolSize = eventPoolSize;
	}

	public boolean isDevelopmentMode() {
		return developmentMode;
	}

	public void setDevelopmentMode(boolean developmentMode) {
		this.developmentMode = developmentMode;
	}
}
