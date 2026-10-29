package com.gentics.mesh.portal.openapi;

import org.apache.commons.lang3.StringUtils;

/**
 * Supported OpenAPI versions.
 */
public enum OpenApiVersion {

	/** Version 3.0 */
	V30,
	/** Version 3.1 */
	V31;

	/**
	 * Safe parse a string value into an OpenApi version.
	 *
	 * @param version The version as a string.
	 * @param defaultValue The default value to return when {@code version} does not match a valid version.
	 * @return The parsed version.
	 */
	public static OpenApiVersion parse(String version, OpenApiVersion defaultValue) {
		if (StringUtils.isNotBlank(version)) {
			switch (version.toLowerCase()) {
				case "v30":
				case "3.0":
					return V30;
				case "v31":
				case "3.1":
					return V31;
			}
		}
		return defaultValue;
	}
}
