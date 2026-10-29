package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class WebDataOptions {

	public enum WebDataFormat {
		JSON,
		RAW
	};

	@JsonProperty(required = true)
	@JsonPropertyDescription("URL of the web page to load.")
	private String url;

	@JsonProperty(defaultValue = "RAW")
	@JsonPropertyDescription("Format of the loaded web page. RAW means the loaded data is interpreted as text. When the format is JSON, the loaded data will be parsed into a Vert.x JsonObject.")
	private WebDataFormat format = WebDataFormat.RAW;

	@JsonProperty(defaultValue = "null")
	@JsonPropertyDescription("Fully qualified class name of an optional post processor implementation, which must implement the interface DataProviderPostProcessor.")
	private String postProcessor;

	@JsonProperty
	@JsonPropertyDescription("Options for the post processor.")
	private Map<String, String> options = Collections.emptyMap();

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public WebDataFormat getFormat() {
		return format;
	}

	public void setFormat(WebDataFormat format) {
		this.format = format;
	}

	public String getPostProcessor() {
		return postProcessor;
	}

	public void setPostProcessor(String postProcessor) {
		this.postProcessor = postProcessor;
	}

	/**
	 * Makes sure an {@link #url} is set.
	 *
	 * @return This object for fluid calls
	 */
	public WebDataOptions validate() {
		Objects.requireNonNull(url, "WebData entries must specify a URL");

		return this;
	}

	@Override
	public String toString() {
		return JsonObject.mapFrom(this).encodePrettily();
	}


	public Map<String, String> getOptions() {
		return options;
	}

	public void setOptions(Map<String, String> options) {
		this.options = options;
	}
}
