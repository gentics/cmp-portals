package com.gentics.mesh.portal.config.option;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

import io.vertx.core.json.JsonObject;

public class DataProviderOptions implements OverridableOptions {

	@JsonProperty(defaultValue = "60")
	@JsonPropertyDescription("Refresh interval in seconds.")
	private int refreshTime = 60;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Flag to prevent startup of the portal in case of errors during loading the configured data.")
	private boolean preventStartupOnError = false;

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Flag to retry loading data in case of errors.")
	private boolean retryOnError = true;

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Whether to provide an endpoint for OpenAPI specification of the routes, currently available in this Portal.")
	private boolean serveOpenApi = true;

	@JsonProperty
	@JsonPropertyDescription("Map of data keys → webroot paths of Mesh content to provide")
	private Map<String, List<String>> meshData = new HashMap<>();

	@JsonProperty
	@JsonPropertyDescription("Map of data keys → web data to provide")
	private Map<String, WebDataOptions> webData = new HashMap<>();

	public int getRefreshTime() {
		return refreshTime;
	}

	public void setRefreshTime(int refreshTime) {
		this.refreshTime = refreshTime;
	}

	public boolean isPreventStartupOnError() {
		return preventStartupOnError;
	}

	public void setPreventStartupOnError(boolean preventStartupOnError) {
		this.preventStartupOnError = preventStartupOnError;
	}

	public boolean isRetryOnError() {
		return retryOnError;
	}

	public void setRetryOnError(boolean retryOnError) {
		this.retryOnError = retryOnError;
	}

	public Map<String, List<String>> getMeshData() {
		return meshData;
	}

	public void setMeshData(Map<String, List<String>> meshData) {
		this.meshData = meshData;
	}

	public Map<String, WebDataOptions> getWebData() {
		return webData;
	}

	public void setWebData(Map<String, WebDataOptions> webData) {
		this.webData = webData;
	}

	public boolean isServeOpenApi() {
		return serveOpenApi;
	}

	public void setServeOpenApi(boolean serveOpenApi) {
		this.serveOpenApi = serveOpenApi;
	}

	/**
	 * Calls {@link WebDataOptions#validate validate()} on all <code>WebDataOptions</code> when
	 * {@link #preventStartupOnError} is <code>true</code>.
	 *
	 * @return This object to enable fluid calls
	 */
	public DataProviderOptions validate() {
		if (preventStartupOnError) {
			for (WebDataOptions entry : webData.values()) {
				entry.validate();
			}
		}

		return this;
	}

	public boolean noDataSources() {
		return (meshData == null || meshData.isEmpty()) && (webData == null || webData.isEmpty());
	}

	@Override
	public String toString() {
		return JsonObject.mapFrom(this).encodePrettily();
	}
}
