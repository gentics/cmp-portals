package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.api.config.OverridableOptions;
import com.gentics.mesh.portal.config.PortalConfig;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.time.Duration;

/**
 * Configuration options for static file handler
 */
public class StaticHandlerOptions implements OverridableOptions {
	public final static String DEFAULT_PATH = "data/static";

	@JsonProperty(defaultValue = DEFAULT_PATH)
	@JsonPropertyDescription("Path to static files.")
	@EnvironmentVariable(name = PortalConfig.STATIC_PATH_ENV, description = "Override the server static path.")
	private String path = DEFAULT_PATH;

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Flag to turn on caching of static files.")
	@EnvironmentVariable(name = PortalConfig.STATIC_CACHING_ENV, description = "Override caching of static files.")
	private boolean caching = false;

	@JsonProperty(defaultValue = "86400s (one day)")
	@JsonPropertyDescription("Maximum age in seconds for caching of static files.")
	@EnvironmentVariable(name = PortalConfig.STATIC_MAX_AGE_ENV, description = "Override max age (in seconds) for static files.")
	private long maxAge = Duration.ofDays(1).getSeconds();

	/**
	 * Path containing static files
	 * @return path
	 */
	@Nonnull
	public String getPath() {
		if (StringUtils.isBlank(path)) {
			path = DEFAULT_PATH;
		}

		return path;
	}

	/**
	 * Set path
	 * @param path path
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * Flag for enabling cache
	 * @return flag value
	 */
	public boolean isCaching() {
		return caching;
	}

	/**
	 * Enable/disable caching
	 * @param caching flag
	 */
	public void setCaching(boolean caching) {
		this.caching = caching;
	}

	/**
	 * Max age in seconds
	 * @return max age
	 */
	public long getMaxAge() {
		return maxAge;
	}

	/**
	 * Set max age in seconds
	 * @param maxAge max age
	 */
	public void setMaxAge(long maxAge) {
		this.maxAge = maxAge;
	}
}
