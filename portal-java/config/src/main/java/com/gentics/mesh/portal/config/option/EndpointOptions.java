package com.gentics.mesh.portal.config.option;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

public class EndpointOptions implements OverridableOptions {

	@JsonProperty
	@JsonPropertyDescription("The path to protect. The path may end with an * to also catch paths which start with this value.")
	private String path;

	@JsonProperty
	@JsonPropertyDescription("List of required role names.")
	private List<String> permissions = Collections.emptyList();

	@JsonProperty(value = "mode", defaultValue = "ANY")
	@JsonPropertyDescription("Mode for checking permissions. ALL means that user needs to have all roles, ANY means that user needs to have at least one role.")
	private PermissionMode permissionMode = PermissionMode.ANY;

	public String getPath() {
		return path;
	}

	public EndpointOptions setPath(String path) {
		this.path = path;

		return this;
	}

	public List<String> getPermissions() {
		return permissions;
	}

	public EndpointOptions setPermissions(List<String> permissions) {
		this.permissions = permissions;

		return this;
	}

	@JsonProperty("mode")
	public PermissionMode getPermissionMode() {
		return permissionMode;
	}

	@JsonProperty("mode")
	public EndpointOptions setPermissionMode(PermissionMode permissionMode) {
		this.permissionMode = permissionMode;

		return this;
	}
}
