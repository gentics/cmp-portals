package com.gentics.mesh.portal;

import java.util.Map;

import io.vertx.core.json.JsonObject;

/**
 * A post processor for data loaded by the {@link DataProviderVerticle}.
 */
public interface DataProviderPostProcessor {

	/**
	 * Process the received <code>data</code> and convert it to
	 * a <code>JsonObject</code>.
	 *
	 * @param data The loaded data
	 * @return The possibly modified loaded data converted to a <code>JsonObject</code>
	 */
	JsonObject process(Object data);

	/**
	 * Initialise this post processor with the given options.
	 *
	 * This method will be called automatically, when the post processor
	 * is created.
	 *
	 * @param options The specific options for this post processor
	 */
	void init(Map<String, String> options);
}
