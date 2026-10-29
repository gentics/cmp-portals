package com.gentics.mesh.portal.content.preview;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CompatibilityOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Map fields from a {@link com.gentics.mesh.core.rest.node.NodeResponse} to
 * field names from the respective GraphQL response.
 *
 * <p>
 *     This mapping can be disabled in the
 *     {@link CompatibilityOptions#isMapNodeResponse() compatibility options}.
 * </p>
 *
 * <p>
 *     Currently this mapper will just rename the following fields on the top
 *     level of the response:
 *     <ul>
 *         <li>{@code parentNode} -> {@code parent}</li>
 *     </ul>
 * </p>
 */
@Singleton
public class PreviewDataMapper {

	private static final Logger log = LoggerFactory.getLogger(PreviewDataMapper.class);

	/** The field name mappings to perform. */
	private static final Map<String, String> FIELD_MAPPINGS = Stream
		.of(
			Pair.of("parentNode", "parent")
		)
		.collect(Collectors.toMap(Pair::getKey, Pair::getValue));

	private final boolean enabled;

	@Inject
	public PreviewDataMapper(PortalConfig config) {
		this.enabled = config.getCompatibilityOptions().isMapNodeResponse();
	}

	/**
	 * Rename all fields on the top level according to the
	 * {@link #FIELD_MAPPINGS}.
	 *
	 * <p>
	 *     When {@link #enabled} is {@code false} this method will do nothing,
	 *     and return the unmodified JSON object.
	 * </p>
	 *
	 * @param nodeResponse The serialized {@link com.gentics.mesh.core.rest.node.NodeResponse}
	 * @return The {@code nodeResponse} with renamed fields
	 */
	public JsonObject mapNodeResponse(JsonObject nodeResponse) {
		if (enabled) {
			if (log.isDebugEnabled()) {
				log.debug("Mapping {} NodeResponse fields to GraphQL fields", FIELD_MAPPINGS.size());
			}

			FIELD_MAPPINGS.forEach((srcField, dstField) -> {
				Object val = nodeResponse.remove(srcField);

				if (val == null) {
					log.warn("Could not map \"{}\" to \"{}\": source field not found", srcField, dstField);
				} else {
					nodeResponse.put(dstField, val);

					if (log.isTraceEnabled()) {
						log.trace(
							"Mapped {} value \"{}\" to \"{}\"",
							val.getClass().getSimpleName(),
							srcField,
							dstField);
					}
				}
			});
		} else {
			log.trace("Mapping NodeResponse fields to GraphQL fields is disabled");
		}

		return nodeResponse;
	}
}
