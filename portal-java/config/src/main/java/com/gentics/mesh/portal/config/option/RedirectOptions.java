package com.gentics.mesh.portal.config.option;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

import javax.annotation.Nonnull;

public class RedirectOptions implements OverridableOptions {

	@JsonProperty
	@JsonPropertyDescription("Map of status pages. Keys are HTTP response status codes, and values are the webroot paths of the error respective pages. Language fallback will be done automatically.")
	private Map<String, String> statusPages = new HashMap<>();

	@JsonProperty
	@JsonPropertyDescription("Optional custom redirects.")
	private CustomRedirectOptions custom;

	@Nonnull
	public Map<String, String> getStatusPages() {
		if (statusPages == null) {
			statusPages = new HashMap<>();
		}

		return statusPages;
	}

	public void setStatusPages(Map<String, String> statusPages) {
		this.statusPages = statusPages;
	}

	public CustomRedirectOptions getCustom() {
		return custom;
	}

	public void setCustom(CustomRedirectOptions custom) {
		this.custom = custom;
	}

	public void validate() {
		if (custom != null) {
			custom.validate();
		}
	}
}
