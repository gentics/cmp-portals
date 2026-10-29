package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.config.StartupCheckMode;

import javax.annotation.Nonnull;

public class MonitoringOptions implements Config {

	public static final int DEFAULT_MAX_TIMEOUTS = 10;

	private static final String ENABLED_ENV = "MONITORING_ENABLED";
	private static final String ENABLED_JMX_ENV = "MONITORING_JMX_ENABLED";
	private static final String HTTP_HOST_ENV = "MONITORING_HTTP_HOST";
	private static final String HTTP_PORT_ENV = "MONITORING_HTTP_PORT";
	private static final String IGNORE_MESH_READY_ENV = "MONITORING_CHECK_MESH_READY";
	private static final String CONSERVATIVE_PORTAL_STATE_ENV = "MONITORING_CONSERVATIVE_PORTAL_STATE";
	private static final String MAX_TIMEOUTS_ENV = "MONITORING_MAX_TIMEOUTS";
	private static final String STARTUP_CHECKS_ENV = "GPJ_MONITORING_STARTUP_CHECKS";
	private static final String LIVE_PATH_ENV = "GPJ_LIVE_PATH";
	private static final String LIVE_UPDATE_INTERVAL_ENV = "GPJ_LIVE_UPDATE_INTERVAL";

	private static final StartupCheckMode DEFAULT_STARTUP_CHECK_MODE = StartupCheckMode.ABORT;
	private static final String DEFAULT_LIVE_PATH = "gpj.live";
	private static final int DEFAULT_LIVE_UPDATE_INTERVAL = 10;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether the monitoring server should be enabled.")
	@EnvironmentVariable(name = ENABLED_ENV, description = "Override whether monitoring should be enabled.")
	private boolean enabled = false;

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Whether to ignore the Mesh ready status for the portal ready check.")
	@EnvironmentVariable(name = IGNORE_MESH_READY_ENV, description = "Override the ignore Mesh ready flag.")
	private boolean ignoreMeshReady = true;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether JMX should be enabled")
	@EnvironmentVariable(name = ENABLED_JMX_ENV, description = "Override whether JMX should be enabled.")
	private boolean jmxEnabled = false;

	@JsonProperty(defaultValue = "3001")
	@JsonPropertyDescription("The port which is used for the monitoring server.")
	@EnvironmentVariable(name = HTTP_PORT_ENV, description = "Override the monitoring server port.")
	private int port = 3001;

	@JsonProperty(defaultValue = "127.0.0.1")
	@JsonPropertyDescription("The host which is used for the monitoring server.")
	@EnvironmentVariable(name = HTTP_HOST_ENV, description = "Override the monitoring server host.")
	private String host = "127.0.0.1";

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether the portal state should change to NETWORK_ERROR as soon as a GraphQL request to Mesh fails with a network error.")
	@EnvironmentVariable(name = CONSERVATIVE_PORTAL_STATE_ENV, description = "Override activation of conservative portal state")
	private boolean conservativePortalState = false;

	@JsonProperty(defaultValue = "" + DEFAULT_MAX_TIMEOUTS)
	@JsonPropertyDescription("Maximum number of timeouts of the MeshRestClient before conservativePortalState will cause a state change to NETWORK_ERROR.")
	@EnvironmentVariable(name = MAX_TIMEOUTS_ENV, description = "Override the maximum number of MeshRestClient timeouts before a state change to NETWORK_ERROR")
	private int maxTimeouts = DEFAULT_MAX_TIMEOUTS;

	@JsonProperty(defaultValue = "ABORT")
	@JsonPropertyDescription("How to handle failing start-up checks.")
	@EnvironmentVariable(name = STARTUP_CHECKS_ENV, description = "Override how to handle failing start-up checks.")
	private StartupCheckMode startupCheckMode = DEFAULT_STARTUP_CHECK_MODE;

	@JsonProperty(defaultValue = DEFAULT_LIVE_PATH)
	@JsonPropertyDescription("Path to the file which tracks liveness of the portal")
	@EnvironmentVariable(name = LIVE_PATH_ENV, description = "Override path to the file which tracks liveness of the portal")
	private String livePath = DEFAULT_LIVE_PATH;

	@JsonProperty(defaultValue = "" + DEFAULT_LIVE_UPDATE_INTERVAL)
	@JsonPropertyDescription("Set the interval in seconds for updating the modified time of the live file")
	@EnvironmentVariable(name = LIVE_UPDATE_INTERVAL_ENV, description = "Override the interval in seconds for updating the modified time of the live file")
	private int liveUpdateInterval = DEFAULT_LIVE_UPDATE_INTERVAL;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isIgnoreMeshReady() {
		return ignoreMeshReady;
	}

	public MonitoringOptions setIgnoreMeshReady(boolean ignoreMeshReady) {
		this.ignoreMeshReady = ignoreMeshReady;

		return this;
	}

	public boolean isJmxEnabled() {
		return jmxEnabled;
	}

	public void setJmxEnabled(boolean jmxEnabled) {
		this.jmxEnabled = jmxEnabled;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isConservativePortalState() {
		return conservativePortalState;
	}

	public MonitoringOptions setConservativePortalState(boolean conservativePortalState) {
		this.conservativePortalState = conservativePortalState;

		return this;
	}

	public int getMaxTimeouts() {
		return maxTimeouts;
	}

	public MonitoringOptions setMaxTimeouts(int maxTimeouts) {
		this.maxTimeouts = maxTimeouts;

		return this;
	}

	@Nonnull
	public StartupCheckMode getStartupCheckMode() {
		if (startupCheckMode == null) {
			startupCheckMode = DEFAULT_STARTUP_CHECK_MODE;
		}

		return startupCheckMode;
	}

	public MonitoringOptions setStartupCheckMode(StartupCheckMode startUpCheckMode) {
		this.startupCheckMode = startUpCheckMode;

		return this;
	}

	@Nonnull
	public String getLivePath() {
		if (livePath == null) {
			livePath = "";
		}

		return livePath;
	}

	public MonitoringOptions setLivePath(String livePath) {
		this.livePath = livePath;

		return this;
	}

	public int getLiveUpdateInterval() {
		return liveUpdateInterval;
	}

	public MonitoringOptions setLiveUpdateInterval(int liveUpdateInterval) {
		this.liveUpdateInterval = liveUpdateInterval;

		return this;
	}
}
