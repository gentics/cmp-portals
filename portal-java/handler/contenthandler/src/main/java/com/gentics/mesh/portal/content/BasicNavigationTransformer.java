package com.gentics.mesh.portal.content;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.JsonUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default navigation transformer for navigations with only one root node.
 *
 * <p>
 *     Tries to determine whether <em>Project Per Node</em> is active for the
 *     served project by comparing the schema prefix and the project name. When
 *     project per node seems to be <em>inactive</em> the root node of the
 *     navigation is skipped (because it would be empty, and its only child
 *     would be the actual root of the navigation tree).
 * </p>
 */
@Singleton
public class BasicNavigationTransformer extends NavigationTransformerBase {

	/** When "Project per Node" is NOT active for the served project, the actual root node is one level deeper. */
	private final boolean skipRootNode;

	/**
	 * Default constructor.
	 *
	 * @param config The portal configuration
	 */
	@Inject
	public BasicNavigationTransformer(PortalConfig config) {
		super(config);

		String schemaPrefix = config.getMeshSchemaPrefix();

		this.skipRootNode = StringUtils.isBlank(schemaPrefix) || StringUtils.equals(schemaPrefix, config.getMeshProject());
	}

	/**
	 * Calls {@link NavigationTransformerBase#processNavigation(JsonObject) processNavigation()}
	 * to remove hidden elements, and sort the rest.
	 *
	 * @see NavigationTransformerBase#processNavigation(JsonObject)
	 * @param queryResult The result of the {@code loadNavigation} GraphQL query
	 * @return A {@code JsonArray} containing the processed navigation
	 */
	@Override
	public JsonArray transform(JsonObject queryResult, RoutingContext rc) {
		JsonObject rootNode = queryResult.getJsonObject("rootNode");

		if (skipRootNode) {
			JsonArray children = JsonUtils.getValue(rootNode, "children.elements", new JsonArray());

			rootNode = children.size() > 0 ? children.getJsonObject(0) : new JsonObject();
		}

		return processNavigation(rootNode);
	}
}
