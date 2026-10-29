package com.gentics.mesh.portal.api;

import java.util.Properties;

/**
 * Model class for build information.
 *
 * <p>
 *     Currently this class provides information about the Gentics Portal | java
 *     version and the build timestamp.
 * </p>
 */
public class BuildInfo {

	private final String version;
	private final String buildTimestamp;

	public BuildInfo(String version, String buildTimestamp) {
		this.version = version;
		this.buildTimestamp = buildTimestamp;
	}

	/**
	 * Create an instance by reading version and build timestamp from the given
	 * properties.
	 *
	 * <p>
	 *     The version must be in the property {@code gpj.version}, and the
	 *     build timestamp in {@code gpj.buildTimestamp}.
	 * </p>
	 *
	 * @param buildProperties The properties containing information about
	 * 		version and build timestamp
	 */
	public BuildInfo(Properties buildProperties) {
		this(buildProperties.getProperty("gpj.version"), buildProperties.getProperty("gpj.buildTimestamp"));
	}

	/**
	 * Get the Gentics Portal | java version.
	 *
	 * @return The Gentics Portal | java version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Get the build timestamp.
	 *
	 * @return The build timestamp
	 */
	public String getBuildTimestamp() {
		return buildTimestamp;
	}

	@Override
	public String toString() {
		return getVersion() + " " + getBuildTimestamp();
	}
}
