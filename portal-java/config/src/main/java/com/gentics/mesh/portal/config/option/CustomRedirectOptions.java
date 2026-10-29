package com.gentics.mesh.portal.config.option;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

public class CustomRedirectOptions implements OverridableOptions {

	@JsonProperty(value = "permanentRedirects", defaultValue = "false")
	@JsonPropertyDescription("Flag to use \"301 Moved Permanently\" responses instead of \"303 See Other\".")
	private boolean permanent = false;

	@JsonProperty(required = true)
	@JsonPropertyDescription("Source for custom redirects. Use prefix 'dataProvider:' to use data from the data provider.")
	private String source;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Flag to reload the redirects source for every request.")
	private boolean reload = false;

	@JsonProperty(required = true)
	@JsonPropertyDescription("Fully qualified class name of helper implementation, which must implement RedirectHelper.")
	private String helperClass;

	@JsonProperty
	@JsonPropertyDescription("Configuration options for helper class.")
	private Map<String, String> helperConfig = new HashMap<>();

	public boolean isPermanent() {
		return permanent;
	}

	public void setPermanent(boolean permanent) {
		this.permanent = permanent;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public boolean isReload() {
		return reload;
	}

	public void setReload(boolean reload) {
		this.reload = reload;
	}

	public String getHelperClass() {
		return helperClass;
	}

	public void setHelperClass(String helperClass) {
		this.helperClass = helperClass;
	}

	public Map<String, String> getHelperConfig() {
		return helperConfig;
	}

	public void setHelperConfig(Map<String, String> helperConfig) {
		this.helperConfig = helperConfig;
	}

	public void validate() {
		Objects.requireNonNull(source, "A source must be specified when custom redirects are used");
		Objects.requireNonNull(helperClass, "The helperClass must be specified when custom redirects are used");
	}
}
