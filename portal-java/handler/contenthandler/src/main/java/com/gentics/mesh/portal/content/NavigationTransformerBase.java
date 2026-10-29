package com.gentics.mesh.portal.content;

import com.gentics.mesh.portal.api.content.NavigationTransformer;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.NavigationOptions;
import com.gentics.mesh.portal.util.JsonUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.stream.Collector;

/**
 * Base for default {@link NavigationTransformer} implementations.
 *
 * <p>
 *     This base class provides the {@link #processNavigation(JsonObject, int)}
 *     method for subclasses which will filter elements which have
 *     {@code navhidden} set, and sorts them by {@code navsortorder}.
 * </p>
 *
 * <p>
 *     The concrete names of the two fields can be overridden in the portal
 *     configuration ({@code navigation.hiddenField} and
 *     {@code navigation.sortOrderField} respectively).
 * </p>
 *
 * <p>
 *     Additionally each navigation entry will get a {@code level} field which
 *     indicates the depth in the navigation tree that this entry occurred
 *     (starting with 0).
 * </p>
 */
public abstract class NavigationTransformerBase implements NavigationTransformer {

	private static final String LEVEL_FIELD = "level";

	private final Logger log = LoggerFactory.getLogger(NavigationTransformerBase.class);

	/** The name of the field containing the sort order. */
	private final String sortOrderFieldname;
	/** The path to the field to hide elements. */
	private final String hiddenField;

	/**
	 * Default constructor.
	 *
	 * @param config The portal configuration
	 */
	public NavigationTransformerBase(PortalConfig config) {
		NavigationOptions options = config.getNavigationOptions();

		this.sortOrderFieldname = options.getSortOrderField();
		this.hiddenField = "fields." + options.getHiddenField();
	}

	/**
	 * Recursively remove hidden child elements, and sort the rest.
	 *
	 * <p>
	 *     The path to the children of a node will be collapsed to <code>children</code> (from
	 *     <code>children.elements</code>), and nodes which neither have a start page themselves nor one of their
	 *     successors will be removed as well.
	 * </p>
	 *
	 * <p>
	 *     Each element which is not removed will have its <code>level</code> field set to their respective level
	 *     in the navigation tree.
	 * </p>
	 *
	 * @param rootNode The root node of the navigation tree
	 * @return A {@code JsonArray} containing the processed navigation
	 */
	protected JsonArray processNavigation(JsonObject rootNode) {
		processNavigation(rootNode, 0);

		JsonArray navigation = rootNode.getJsonArray("children", new JsonArray());

		if (log.isTraceEnabled()) {
			log.trace("Processed navigation:\n{}", navigation.encodePrettily());
		}

		return navigation;
	}

	/**
	 * Recursively remove hidden child elements, and sort the rest.
	 *
	 * <p>
	 *     The path to the children of a node will be collapsed to <code>children</code> (from
	 *     <code>children.elements</code>), and nodes which neither have a start page themselves nor one of their
	 *     successors will be removed as well.
	 * </p>
	 *
	 * <p>
	 *     Each element which is not removed will have its <code>level</code> field set to their respective level
	 *     in the navigation tree.
	 * </p>
	 *
	 * @param node The current node to process
	 * @param level The current level in the navigation tree (starting with 0 for the project root node)
	 * @return <code>true</code> when the given node itself or one of its successors has a start page
	 */
	private boolean processNavigation(JsonObject node, int level) {
		if (node == null) {
			log.debug("No navigation data available");

			return false;
		}

		JsonArray children = JsonUtils.getValue(node, "children.elements", new JsonArray());
		// When we are at level 0, we are processing project.rootNode which has
		// no startpage but must still be considered.
		boolean foundStartpage = level == 0
				|| !JsonUtils.getValue(node, "fields.startpage", new JsonObject()).isEmpty()
				|| StringUtils.isNotBlank(JsonUtils.getValue(node, "fields.startpageurl", null));

		node.put(LEVEL_FIELD, level);

		if (children.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug(
					"Element {} has no children (start page: {})",
					node.getString("path"),
					foundStartpage);
			}

			node.put("children", children);

			return foundStartpage;
		}

		children = children.stream()
			.map(o -> (JsonObject) o)
			.filter(o -> !JsonUtils.getValue(o, hiddenField, Boolean.FALSE))
			.sorted(this::navigationComparator)
			.collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::addAll));

		Iterator<Object> it = children.iterator();

		while (it.hasNext()) {
			JsonObject child = (JsonObject) it.next();

			if (processNavigation(child, level + 1)) {
				foundStartpage = true;
			} else {
				if (log.isDebugEnabled()) {
					log.debug(
						"Removing {} from navigation because it has no direct or indirect start page",
						child.getString("path"));
				}

				it.remove();
			}
		}

		node.put("children", children);

		return foundStartpage;
	}

	/**
	 * Compares two nodes by their {@link #sortOrderFieldname} field.
	 *
	 * @see #getSortOrder(JsonObject)
	 * @param a The first item to compare
	 * @param b The second item to compare
	 * @return A value less than 0, 0 or a value greater than 0 when the <code>sortOrderField</code> of
	 * 		<code>a</code> is less than, equal, or greater than the <code>sortOrderField</code> of <code>b</code>
	 * 		respectively.
	 */
	private int navigationComparator(JsonObject a, JsonObject b) {
		return getSortOrder(a).compareTo(getSortOrder(b));
	}

	/**
	 * Tries to retrieve the integer value for the {@link #sortOrderFieldname sort order field}.
	 *
	 * @param o The object to retrieve the sort order field from
	 * @return The integer value of the sort order field if it is present, and 0 when the passed object
	 * 		is <code>null</code> or does not have the field set
	 */
	private Integer getSortOrder(JsonObject o) {
		if (o == null) {
			return 0;
		}

		Integer ret = o.getJsonObject("fields", new JsonObject()).getInteger(sortOrderFieldname);

		return ret == null ? 0 : ret;
	}
}
