package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import org.apache.commons.lang3.Strings;

public class FormsOptions implements Config {
	
	private static final String ENV_FORMGEN_STATIC_FILE_DIRECTORY = "GPJ_FORMGEN_STATIC_FILE_DIRECTORY";
	private static final String ENV_FORMGEN_STATIC_SERVE_PATH = "GPJ_FORMGEN_STATIC_SERVE_PATH";
	
	@JsonProperty(defaultValue = "data/static/cpa/formgen")
	@JsonPropertyDescription("Internal path to static (delivered by Portal) formgen files (css/js/fonts)")
	@EnvironmentVariable(name = ENV_FORMGEN_STATIC_FILE_DIRECTORY, description = "Internal path to static (delivered by Portal) formgen files (css/js/fonts)")
	private String formgenStaticFileDirectory = "data/static/cpa/formgen";
	
	
	@JsonProperty(defaultValue = "/formgen/")
	@JsonPropertyDescription("Path of static formgen files reachable by browser")
	@EnvironmentVariable(name = ENV_FORMGEN_STATIC_SERVE_PATH, description = "Path of static formgen files reachable by browser")
	private String formgenStaticServePath= "/formgen/";


	public String getFormgenStaticFileDirectory() {
		return formgenStaticFileDirectory;
	}

	public FormsOptions setFormgenStaticFileDirectory(String formgenStaticFileDirectory) {
		formgenStaticFileDirectory = Strings.CI.removeStart(formgenStaticFileDirectory, "/");
		this.formgenStaticFileDirectory = Strings.CI.removeEnd(formgenStaticFileDirectory, "/");

		return this;
	}


	public String getFormgenStaticServePath() {
		return formgenStaticServePath;
	}


	public FormsOptions setFormgenStaticServePath(String formgenStaticServePath) {
		formgenStaticServePath = Strings.CI.prependIfMissing(formgenStaticServePath, "/");
		this.formgenStaticServePath = Strings.CI.appendIfMissing(formgenStaticServePath, "/");

		return this;
	}	
	
}
