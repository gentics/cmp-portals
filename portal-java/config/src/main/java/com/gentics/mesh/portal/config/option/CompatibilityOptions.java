package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

public class CompatibilityOptions implements Config {

	public static final String COMPATIBILITY_PREVIEW = "COMPATIBILITY_PREVIEW";
	public static final String COMPATIBILITY_NODE_RESPONSE_MAPPER = "COMPATIBILITY_NODE_RESPONSE_MAPPER";

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether to use preview modes for GCMS versions older than 5.34.x.")
	@EnvironmentVariable(name = COMPATIBILITY_PREVIEW, description = "Override the compatibility preview flag.")
	private boolean preview = false;

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Whether the NodeResponse to GraphQL response mapper should be applied for previews.")
	@EnvironmentVariable(name = COMPATIBILITY_NODE_RESPONSE_MAPPER, description = "Override the compatibility map node response flag.")
	private boolean mapNodeResponse = true;

	public boolean isPreview() {
		return preview;
	}

	public CompatibilityOptions setPreview(boolean preview) {
		this.preview = preview;

		return this;
	}

	public boolean isMapNodeResponse() {
		return mapNodeResponse;
	}

	public CompatibilityOptions setMapNodeResponse(boolean mapNodeResponse) {
		this.mapNodeResponse = mapNodeResponse;

		return this;
	}
}
