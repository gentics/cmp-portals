package com.gentics.mesh.portal.content;

import java.util.Map;

import io.vertx.core.json.JsonObject;

/**
 * A RedirectHelper is used to convert JSON data loaded from Mesh into a
 * map of redirects.
 * 
 * Configuration options are passed to its {@link #init(Map)} method.
 */
public interface RedirectHelper {
	/**
	 * Initialise the helper with the specified configuration.
	 *
	 * @param config The configuration for this helper.
	 */
	void init(Map<String, String> config);
	
	/**
	 * Convert the JSON data from Mesh to a redirect mapping.
	 *
	 * @param data The loaded data from Mesh
	 * @return A mapping of URLs to their redirect destinations
	 */
	Map<String, String> convertData(JsonObject data);
}
