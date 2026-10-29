package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.OverridableOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SingleSignOutOptions implements OverridableOptions {

	public static final String DEFAULT_SINGLE_SIGN_OUT_PATH = "/auth/logoutAll";

	@JsonProperty
	@JsonPropertyDescription("List of all URLs of external portals")
	private List<String> externalPortals = new ArrayList<>();

	@JsonProperty(defaultValue = DEFAULT_SINGLE_SIGN_OUT_PATH)
	@JsonPropertyDescription("Endpoint for starting single sign out.")
	private String singleSignOutPath = DEFAULT_SINGLE_SIGN_OUT_PATH;

	@JsonIgnore
	public boolean isDefault() {
		return DEFAULT_SINGLE_SIGN_OUT_PATH.equals(singleSignOutPath)
				&& externalPortals != null
				&& externalPortals.isEmpty();

	}

	public List<String> getExternalPortals() {
		if (externalPortals == null) {
			externalPortals = new ArrayList<>();
		}

		return externalPortals;
	}

	public void setExternalPortals(List<String> externalPortals) {
		this.externalPortals = externalPortals;
	}

	public String getSingleSignOutPath() {
		return singleSignOutPath;
	}

	public void setSingleSignOutPath(String singleSignOutPath) {
		this.singleSignOutPath = singleSignOutPath;
	}

}

