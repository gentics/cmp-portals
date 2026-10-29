package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.api.config.OverridableOptions;
import com.gentics.mesh.portal.config.MeshAuthenticationMode;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.rest.client.ProtocolVersion;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MeshOptions implements OverridableOptions {

	public static final String MESH_HOST_ENV = "MESH_HOST";
	public static final String MESH_PORT_ENV = "MESH_PORT";
	public static final String MESH_PROTOCOL_VERSION_ENV = "MESH_PROTOCOL_VERSION";
	public static final String MESH_MONITORING_PORT_ENV = "MESH_MONITORING_PORT";
	public static final String MESH_SSL_ENV = "MESH_SSL";
	public static final String MESH_API_KEY_ENV = "MESH_API_KEY";
	public static final String MESH_NETWORK_TIMEOUT_ENV = "MESH_NETWORK_TIMEOUT";
	public static final String MESH_MAX_REQUESTS_ENV = "MESH_MAX_REQUESTS";
	public static final String MESH_MAX_REQUESTS_PER_HOST_ENV = "MESH_MAX_REQUESTS_PER_HOST";
	public static final String MESH_WRITE_QUEUE_MAX_SIZE_ENV = "MESH_WRITE_QUEUE_MAX_SIZE";
	public static final String MESH_AUTHENTICATION_MODE_ENV = "MESH_AUTHENTICATION_MODE";

	private static final int DEFAULT_MESH_MONITORING_PORT = 8081;
	private static final List<String> DEFAULT_PROXY_HEADER_WHITELIST = Arrays.asList(
		"Accept",
		"Accept-Encoding",
		"Authorization",
		"Content-Encoding",
		"Content-Length",
		"Content-Type",
		"Cookie",
		"Referer");

	private static final Set<String> DEFAULT_COOKIE_FORWARD_WHITELIST = new HashSet<>(Arrays.asList(
		"forms-data",
		"forms-voted"));

	private static final int DEFAULT_MAX_REQUESTS = 64;
	private static final int DEFAULT_MAX_REQUESTS_PER_HOST = 64;
	private static final int DEFAULT_WRITE_QUEUE_MAX_SIZE = 8192;
	private static MeshAuthenticationMode DEFAULT_AUTHENTICATION_MODE = MeshAuthenticationMode.BOTH;

	@JsonProperty(defaultValue = "localhost")
	@JsonPropertyDescription("Mesh host.")
	@EnvironmentVariable(name = MESH_HOST_ENV, description = "Override the configured mesh host.")
	private String host = "localhost";

	@JsonProperty(defaultValue = "8080")
	@JsonPropertyDescription("Mesh port.")
	@EnvironmentVariable(name = MESH_PORT_ENV, description = "Override the configured mesh port.")
	private int port = 8080;

	@JsonProperty
	@JsonPropertyDescription("Mesh HTTP protocol version.")
	@EnvironmentVariable(name = MESH_PROTOCOL_VERSION_ENV, description = "Override the configured mesh HTTP protocol version.")
	private ProtocolVersion protocolVersion = ProtocolVersion.DEFAULT;

	@JsonProperty(defaultValue = "" + DEFAULT_MESH_MONITORING_PORT)
	@JsonPropertyDescription("The port of the monitoring verticle.")
	@EnvironmentVariable(name = MESH_MONITORING_PORT_ENV, description = "Override the Mesh monitoring port")
	private int monitoringPort = DEFAULT_MESH_MONITORING_PORT;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Mesh SSL flag.")
	@EnvironmentVariable(name = MESH_SSL_ENV, description = "Override the configured mesh ssl flag.")
	private boolean ssl = false;

	@JsonProperty(required = true)
	@JsonPropertyDescription("API key for accessing Mesh as admin user.")
	@EnvironmentVariable(name = MESH_API_KEY_ENV, description = "Override the configured mesh API key.")
	private String apiKey;

	@JsonProperty("eventbus")
	@JsonPropertyDescription("Eventbus Configuration")
	private EventbusOptions eventbusOptions = new EventbusOptions();

	@JsonProperty(defaultValue = "(none)")
	@JsonPropertyDescription("List of Mesh plugins which must be present for this portal")
	private List<String> requiredPlugins = new ArrayList<>();

	@JsonPropertyDescription("This option is deprecated and should not be used. Use `networkTimeout` instead.")
	@EnvironmentVariable(name = MESH_NETWORK_TIMEOUT_ENV, description = "This option is deprecated and should not be used. Use `GPJ_NETWORK_TIMEOUT` instead.")
	@Deprecated
	private int networkTimeout = PortalConfig.DEFAULT_NETWORK_TIMEOUT;

	@JsonProperty(defaultValue = "Accept, Authorization, Content-Encoding, Content-Length, Content-Type, Cookie")
	@JsonPropertyDescription("Whitelist for HTTP headers which may be forwarded to Mesh plugins. Note that the default values are always allowed and cannot be removed.")
	private Set<String> proxyHeaderWhitelist = new HashSet<>(DEFAULT_PROXY_HEADER_WHITELIST);

	@JsonProperty(defaultValue = "" + DEFAULT_MAX_REQUESTS)
	@JsonPropertyDescription("Set the maximum number of requests to execute concurrently. Above this requests queue in memory, waiting for the running calls to complete.")
	@EnvironmentVariable(name = MESH_MAX_REQUESTS_ENV, description = "Override the maximum number of requests to Mesh to execute concurrently")
	private int maxRequests = DEFAULT_MAX_REQUESTS;

	@JsonProperty(defaultValue = "" + DEFAULT_MAX_REQUESTS_PER_HOST)
	@JsonPropertyDescription("Set the maximum number of requests for each host to execute concurrently. Above this requests queue in memory, waiting for the running calls to complete.")
	@EnvironmentVariable(name = MESH_MAX_REQUESTS_PER_HOST_ENV, description = "Override the maximum number of requests for each host to Mesh to execute concurrently")
	private int maxRequestsPerHost = DEFAULT_MAX_REQUESTS_PER_HOST;

	@JsonProperty(defaultValue = "" + DEFAULT_WRITE_QUEUE_MAX_SIZE)
	@JsonPropertyDescription("Set the maximum number of bytes stored in the write queue when forwarding incoming requests to Mesh using the okHttp Client.")
	@EnvironmentVariable(name = MESH_WRITE_QUEUE_MAX_SIZE_ENV, description = "Override the maximum number of bytes stored in the write queue when forwarding incoming requests to Mesh using the okHttp Client.")
	private int writeQueueMaxSize = DEFAULT_WRITE_QUEUE_MAX_SIZE;

	@JsonProperty
	@JsonPropertyDescription("Names of cookies which can be forwarded to Mesh by plugin proxy requests (default: forms-data, forms-voted).")
	private Set<String> cookieForwardWhitelist = DEFAULT_COOKIE_FORWARD_WHITELIST;

	@JsonProperty
	@JsonPropertyDescription("Whether to send the API token via authentication header, cookie, or both (default: both)")
	@EnvironmentVariable(name = MESH_AUTHENTICATION_MODE_ENV, description = "Override whether to send the API token via authentication header, cookie, or both.")
	private MeshAuthenticationMode authenticationMode = DEFAULT_AUTHENTICATION_MODE;

	/**
	 * Whether the current Mesh version supports webroot field requests.
	 *
	 * <p>
	 *     Note that this setting is <em>not</em> intended to be changed in the
	 *     <em>server.yml</em> configuration file, and will be overwritten
	 *     during portal startup.
	 * </p>
	 */
	private boolean webrootFieldAvailable = false;

	public String getHost() {
		return host;
	}

	public MeshOptions setHost(String host) {
		this.host = host;

		return this;
	}

	public int getPort() {
		return port;
	}

	public MeshOptions setPort(int port) {
		this.port = port;

		return this;
	}

	public int getMonitoringPort() {
		return monitoringPort;
	}

	public MeshOptions setMonitoringPort(int monitoringPort) {
		this.monitoringPort = monitoringPort;

		return this;
	}

	public boolean isSsl() {
		return ssl;
	}

	public MeshOptions setSsl(boolean flag) {
		this.ssl = flag;

		return this;
	}

	public String getApiKey() {
		return apiKey;
	}

	public MeshOptions setApiKey(String apiKey) {
		this.apiKey = apiKey;

		return this;
	}

	@Nonnull
	public EventbusOptions getEventbusOptions() {
		if (eventbusOptions == null) {
			eventbusOptions = new EventbusOptions();
		}

		return eventbusOptions;
	}

	public MeshOptions setEventbusOptions(EventbusOptions eventbusOptions) {
		this.eventbusOptions = eventbusOptions;

		return this;
	}

	@Nonnull
	public List<String> getRequiredPlugins() {
		if (requiredPlugins == null) {
			requiredPlugins = new ArrayList<>();
		}

		return requiredPlugins;
	}

	public MeshOptions setRequiredPlugins(List<String> requiredPlugins) {
		this.requiredPlugins = requiredPlugins;

		return this;
	}

	/**
	 * Get the timeout for network requests (in- and outbound) in seconds.
	 *
	 * @deprecated Use {@link PortalConfig#getNetworkTimeout()} instead.
	 *
	 * @return The network timeout in seconds
	 */
	public int getNetworkTimeout() {
		return networkTimeout;
	}

	public MeshOptions setNetworkTimeout(int networkTimeout) {
		this.networkTimeout = networkTimeout;

		return this;
	}

	public Set<String> getProxyHeaderWhitelist() {
		if (proxyHeaderWhitelist == null) {
			proxyHeaderWhitelist = new HashSet<>(DEFAULT_PROXY_HEADER_WHITELIST);
		}

		return proxyHeaderWhitelist;
	}

	public MeshOptions setProxyHeaderWhitelist(Set<String> proxyHeaderWhitelist) {
		this.proxyHeaderWhitelist = proxyHeaderWhitelist;

		// The getter will check for null and reinitialize the list if necessary.
		getProxyHeaderWhitelist().addAll(DEFAULT_PROXY_HEADER_WHITELIST);

		return this;
	}

	public int getMaxRequests() {
		return maxRequests;
	}

	public MeshOptions setMaxRequests(int maxRequests) {
		this.maxRequests = maxRequests;

		return this;
	}

	public int getMaxRequestsPerHost() {
		return maxRequestsPerHost;
	}

	public MeshOptions setMaxRequestsPerHost(int maxRequestsPerHost) {
		this.maxRequestsPerHost = maxRequestsPerHost;

		return this;
	}

	public int getWriteQueueMaxSize() {
		return writeQueueMaxSize;
	}

	public MeshOptions setWriteQueueMaxSize(int writeQueueMaxSize) {
		this.writeQueueMaxSize = writeQueueMaxSize;

		return this;
	}

	@Nonnull
	public Set<String> getCookieForwardWhitelist() {
		if (cookieForwardWhitelist == null) {
			cookieForwardWhitelist = DEFAULT_COOKIE_FORWARD_WHITELIST;
		}

		return cookieForwardWhitelist;
	}

	public MeshOptions setCookieForwardWhitelist(Set<String> cookieForwardWhitelist) {
		this.cookieForwardWhitelist = cookieForwardWhitelist;

		return this;
	}

	@Nonnull
	public MeshAuthenticationMode getAuthenticationMode() {
		if (authenticationMode == null) {
			authenticationMode = DEFAULT_AUTHENTICATION_MODE;
		}

		return authenticationMode;
	}

	public MeshOptions setAuthenticationMode(MeshAuthenticationMode authenticationMode) {
		this.authenticationMode = authenticationMode;
		return this;
	}

	public boolean isWebrootFieldAvailable() {
		return webrootFieldAvailable;
	}

	public MeshOptions setWebrootFieldAvailable(boolean webrootFieldAvailable) {
		this.webrootFieldAvailable = webrootFieldAvailable;

		return this;
	}

	public ProtocolVersion getProtocolVersion() {
		return protocolVersion;
	}

	public MeshOptions setProtocolVersion(ProtocolVersion protocolVersion) {
		this.protocolVersion = protocolVersion;
		return this;
	}

	public MeshOptions validate() {
		Objects.requireNonNull(apiKey, "No Mesh API key was configured in the server options");
		Objects.requireNonNull(eventbusOptions, "No Mesh eventbus options configured");
		return this;
	}
}
