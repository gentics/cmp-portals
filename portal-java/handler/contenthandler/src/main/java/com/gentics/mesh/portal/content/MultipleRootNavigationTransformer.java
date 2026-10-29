package com.gentics.mesh.portal.content;

import com.gentics.mesh.portal.config.PortalConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default navigation transformer for navigations which can have multiple roots.
 *
 * <p>
 *     The {@link NavigationTransformerBase#processNavigation(JsonObject)}
 *     method is called without altering the navigation tree first.
 * </p>
 */
@Singleton
public class MultipleRootNavigationTransformer extends NavigationTransformerBase {

	/**
	 * Default constructor.
	 *
	 * @param config The portal configuration
	 */
	@Inject
	public MultipleRootNavigationTransformer(PortalConfig config) {
		super(config);
	}

	/**
	 * Calls {@link NavigationTransformerBase#processNavigation(JsonObject) processNavigation()}
	 * to remove hidden elements, and sort the rest.
	 *
	 * @see NavigationTransformerBase#processNavigation(JsonObject)
	 * @param queryResult The result of the <code>loadNavigation</code> GraphQL query
	 * @return A <code>JsonArray</code> containing the processed navigation
	 */
	@Override
	public JsonArray transform(JsonObject queryResult, RoutingContext rc) {
		return processNavigation(queryResult.getJsonObject("rootNode"));
	}
}
