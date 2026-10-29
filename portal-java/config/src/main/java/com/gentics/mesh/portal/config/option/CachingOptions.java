package com.gentics.mesh.portal.config.option;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

public class CachingOptions implements Config {

	public static final String CACHING_ENABLED_ENV = "CACHING_ENABLED";
	public static final String CACHING_EXPIRE_TIME = "CACHING_EXPIRE_TIME";
	public static final String CACHING_MAXIMUM_SIZE = "CACHING_MAXIMUM_SIZE";
	public static final String CACHING_CONFIG_FILE_ENV = "CACHING_CONFIG_FILE";
	public static final String CACHING_DISK_CACHE_REGION_NAME_ENV = "CACHING_DISK_CACHE_REGION_NAME";
	public static final String CACHING_BINARY_MAX_AGE = "CACHING_BINARY_MAX_AGE";

	public static final int DEFAULT_EXPIRE_TIME = 60;
	public static final int DEFAULT_MAXIMUM_SIZE = 256;
	public static final String DEFAULT_CACHE_CONFIG = "config/cache.ccf";
	public static final String DEFAULT_DISK_CACHE_REGION_NAME = "content";
	public static final int DEFAULT_BINARY_MAX_AGE = 60 * 60 * 24;

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Enable or disable caching.")
	@EnvironmentVariable(name = CACHING_ENABLED_ENV, description = "Override the caching enabled flag.")
	private boolean enabled = true;

	@JsonProperty(defaultValue = "" + DEFAULT_MAXIMUM_SIZE)
	@JsonPropertyDescription("The maximum number of elements stored in a cache.")
	@EnvironmentVariable(name = CACHING_MAXIMUM_SIZE, description = "Override the maximum cache size.")
	private int maximumSize = DEFAULT_MAXIMUM_SIZE;

	@JsonProperty(defaultValue = "" + DEFAULT_EXPIRE_TIME)
	@JsonPropertyDescription("The time after which elements in the cache expire when they are not accessed.")
	@EnvironmentVariable(name = CACHING_EXPIRE_TIME, description = "Override the expire time value.")
	private int expireTime = DEFAULT_EXPIRE_TIME;

	@JsonProperty
	@JsonPropertyDescription("Custom values from the routing context to be added to content cache keys.")
	private List<String> customParameters = new ArrayList<>();

	@JsonProperty(defaultValue = DEFAULT_CACHE_CONFIG)
	@JsonPropertyDescription("Path of the JCS configuration file.")
	@EnvironmentVariable(name = CACHING_CONFIG_FILE_ENV, description = "Override the path to the JCS configuration file.")
	private String diskCacheConfig = DEFAULT_CACHE_CONFIG;

	@JsonProperty(defaultValue = DEFAULT_DISK_CACHE_REGION_NAME )
	@JsonPropertyDescription("Name of the disk cache region to be used for the caching handler. The in-memory cache solution will be used when no region name has been specified.")
	@EnvironmentVariable(name = CACHING_DISK_CACHE_REGION_NAME_ENV, description = "Override the configured disk cache region name." )
	private String diskCacheRegionName = DEFAULT_DISK_CACHE_REGION_NAME;

	@JsonProperty(defaultValue = DEFAULT_BINARY_MAX_AGE + " seconds (1 day)")
	@JsonPropertyDescription("The max-age cache-control setting for binaries loaded from Mesh.")
	@EnvironmentVariable(name = CACHING_BINARY_MAX_AGE, description = "Override the max-age cache-control setting for Mesh binaries")
	private int binaryMaxAge = DEFAULT_BINARY_MAX_AGE;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getMaximumSize() {
		return maximumSize;
	}

	public void setMaximumSize(int maximumSize) {
		this.maximumSize = maximumSize;
	}

	public int getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(int expireTime) {
		this.expireTime = expireTime;
	}

	public List<String> getCustomParameters() {
		return customParameters;
	}

	public void setCustomParameters(List<String> customParameters) {
		this.customParameters = customParameters;
	}

	public String getDiskCacheConfig() {
		return diskCacheConfig;
	}

	public void setDiskCacheConfig(String diskCacheConfig) {
		this.diskCacheConfig = diskCacheConfig;
	}

	public String getDiskCacheRegionName() {
		return diskCacheRegionName;
	}

	public CachingOptions setDiskCacheRegionName(String diskCacheRegionName) {
		this.diskCacheRegionName = diskCacheRegionName;
		return this;
	}

	public int getBinaryMaxAge() {
		return binaryMaxAge;
	}

	public CachingOptions setBinaryMaxAge(int binaryMaxAge) {
		this.binaryMaxAge = binaryMaxAge;

		return this;
	}
}
