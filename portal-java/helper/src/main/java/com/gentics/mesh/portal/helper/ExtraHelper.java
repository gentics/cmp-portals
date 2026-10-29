package com.gentics.mesh.portal.helper;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.RenderContextUtils;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.helper.EachHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Extra helper functions.
 *
 * <p>
 *     <strong>IMPORTANT:</strong> The helpers in this class are very likely to change or be removed in
 *     future versions of Gentics Portal | java.
 * </p>
 *
 * <p>
 *     These helpers can be used, when the overhauled versions from the classes listed below are
 *     not adequate. Adapting templates to use the overhauled helpers from the listed classes is
 *     highly recommended.
 * </p>
 *
 * <p>
 *     Currently the this class provides helpers removed from
 *     <ul>
 *         <li>{@link NavigationHelper}</li>
 *         <li><code>BreadcrumbsHelper</code> (merged with <code>NavigationHelper</code>)</code></li>
 *         <li><code>OverviewHelper</code></li>
 *     </ul>
 * </p>
 */
@Singleton
public class ExtraHelper {

	private PortalConfig portalConfig;
	private final boolean isProjectPerNode;

	@Inject
	public ExtraHelper(PortalConfig portalConfig) {
		String schemaPrefix = portalConfig.getMeshSchemaPrefix();

		this.portalConfig = portalConfig;
		this.isProjectPerNode = StringUtils.isBlank(schemaPrefix) || StringUtils.equals(schemaPrefix, portalConfig.getMeshProject());
	}

	Predicate<JsonObject> unhiddenPredicate = new Predicate<JsonObject>() {
		@Override
		public boolean test(JsonObject element) {
			JsonObject fields = element.getJsonObject("fields");
			if (fields == null || fields.isEmpty()) {
				return false;
			}
			if (Boolean.TRUE != fields.getBoolean("navhidden", false)
				&& element.getJsonObject("children") != null
				&& element.getJsonObject("children").getJsonArray("elements") != null
				&& !element.getJsonObject("children").getJsonArray("elements").isEmpty()) {
				return true;
			}
			return false;
		}
	};

	Predicate<JsonObject> startpagePredicate = new Predicate<JsonObject>() {
		@Override
		public boolean test(JsonObject element) {
			return hasStartpage(element);
		}
	};

	Predicate<JsonObject> startpageOrSubelementsPredicate = new Predicate<JsonObject>() {
		@Override
		public boolean test(JsonObject element) {
			if (startpagePredicate.test(element) || unhiddenPredicate.test(element)) {
				return true;
			}
			return false;
		}
	};

	protected boolean isHidden(JsonObject element) {
		JsonObject fields = JsonUtils.getValue(element, "fields", new JsonObject());
		if (fields.isEmpty()) {
			return false;
		}
		if (Boolean.TRUE == fields.getBoolean("navhidden", false)) {
			return true;
		}
		return false;
	}

	public Object isNotHidden(JsonObject element, Options options) throws IOException {
		if (!isHidden(element)) {
			return options.fn();
		}
		return options.inverse();
	}

	public boolean _hasUnhiddenFolderChildren(JsonObject element) {
		if (element.getJsonObject("children") != null
			&& element.getJsonObject("children").getJsonArray("elements") != null
			&& !element.getJsonObject("children").getJsonArray("elements").isEmpty()) {

			for (Object o : element.getJsonObject("children").getJsonArray("elements")) {
				if (o instanceof JsonObject) {
					JsonObject child = (JsonObject) o;
					if (_isFolder(child) && !isHidden(child) && _folderHasDirectStartpage(child)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public Object hasUnhiddenFolderChildren(JsonObject element, Options options) throws IOException {
		return _hasUnhiddenFolderChildren(element) ? options.fn() : options.inverse();
	}

	public Object folderHasDirectStartpage(JsonObject element, Options options) throws IOException {
		if (_folderHasDirectStartpage(element)) {
			return options.fn();
		}
		return options.inverse();
	}


	public boolean _folderHasDirectStartpage(JsonObject element) {
		if (element.getJsonObject("fields") != null && !element.getJsonObject("fields").isEmpty()
			&& element.getJsonObject("fields").getJsonObject("startpage") != null) {
			return true;
		}
		return false;
	}


	/**
	 * returns true if element contains startpage somewhere in subtree
	 *
	 * @param element
	 * @return
	 */
	protected boolean hasStartpage(JsonObject element) {
		if (element.getJsonObject("fields") != null && !element.getJsonObject("fields").isEmpty()
			&& element.getJsonObject("fields").getJsonObject("startpage") != null) {
			return true;
		}
		if (element.getJsonObject("children") != null) {
			JsonArray subElements = element.getJsonObject("children").getJsonArray("elements");
			if (subElements != null && !subElements.isEmpty()) {
				for (Object subElement : subElements) {
					if (hasStartpage((JsonObject) subElement)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	protected boolean _isInActivePath(JsonObject element, JsonObject node) {
		if (elementInFolder(element, node)) {
			return true;
		}
		if (element.getJsonObject("children") != null && element.getJsonObject("children").getJsonArray("elements") != null) {
			JsonArray elements = element.getJsonObject("children").getJsonArray("elements");
			for (int i = 0; i < elements.size(); i++) {
				JsonObject item = elements.getJsonObject(i);
				if (elementInBreadcrumbs(item, node) || elementInFolder(item, node)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks if current object is in breadcrumbs. Returns true if element is in breadcrumbs of the folder or is in folder. Otherwise returns false;
	 *
	 * @param element
	 *            current object; e.g. data.project.rootNode.children.element[i], this.children.element[i]
	 * @param node
	 *            element containing breadcrumbs; e.g. data.folder
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object isInActivePath(JsonObject element, JsonObject node, Options options) throws IOException {
		return _isInActivePath(element, node) ? options.fn() : options.inverse();
	}

	/**
	 * returns true if element is from schema wn_folder
	 *
	 * @param element
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object isFolder(JsonObject element, Options options) throws IOException {
		if (_isFolder(element)) {
			return options.fn();
		}
		return options.inverse();
	}

	protected boolean _isFolder(JsonObject element) {
		if (element.getJsonObject("schema").getString("name").equals(portalConfig.getFolderSchema())) {
			return true;
		}
		return false;
	}

	public boolean elementInBreadcrumbs(JsonObject element, JsonObject node) {
		JsonArray breadcrumbs = node.getJsonArray("breadcrumb");
		String elementUuid = element.getString("uuid");
		for (int i = 0; i < breadcrumbs.size(); i++) {
			JsonObject object = breadcrumbs.getJsonObject(i);
			String breadcrumbUuid = object.getString("uuid");
			if (elementUuid.equals(breadcrumbUuid)) {
				return true;
			}
		}
		return false;
	}


	public boolean elementInFolder(JsonObject element, JsonObject node) {
		return element.getString("uuid").equals(node.getString("uuid"));
	}

	/**
	 * Returns true if "children" has some unhidden elements. Otherwise returns false
	 *
	 * @param children
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object hasUnhiddenElements(JsonObject children, Options options) throws IOException {
		if (children != null && children.getJsonArray("elements") != null) {
			JsonArray elements = children.getJsonArray("elements");
			JsonObject filtered = elements.stream().map(JsonObject.class::cast)
				.filter(unhiddenPredicate)
				.findFirst()
				.orElse(null);

			if (filtered != null) {
				return options.fn();
			}
		}
		return options.inverse();
	}

	/**
	 * If element has object.startpage configured, returns url of that startpage. Otherwise returns #!
	 *
	 * @param element
	 * @return
	 */
	public Object getUrl(JsonObject element) {
		JsonObject startpage = null;
		if (element.containsKey("fields")) {
			startpage = element.getJsonObject("fields").getJsonObject("startpage");
		} else {
			startpage = element.getJsonObject("node").getJsonObject("fields").getJsonObject("startpage");
		}
		if (startpage != null) {
			return startpage.getString("path");
		}
		return "#!";
	}

	/**
	 * Returns filtered sublist containing only unhidden elements that has startpage somewhere in subtree
	 *
	 * @param elements
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object eachWithStartpage(JsonArray elements, Options options) throws IOException {
		final Helper<Object> delegate = new EachHelper();
		return delegate.apply(filter(elements), options);
	}

	public Object eachWithStartpageOrSubElements(JsonArray elements, Options options) throws IOException {
		final Helper<Object> delegate = new EachHelper();
		return delegate.apply(filterStartpageOrSubelements(elements), options);
	}

	private Iterable<JsonObject> filter(JsonArray items) {
		return items.stream().map(JsonObject.class::cast).filter(unhiddenPredicate)
			.filter(startpagePredicate)
			.collect(Collectors.toList());
	}

	private Iterable<JsonObject> filterStartpageOrSubelements(JsonArray items) {
		return items.stream().map(JsonObject.class::cast).filter(startpageOrSubelementsPredicate)
			.collect(Collectors.toList());
	}


	/**
	 * Iterate over each breadcrumb entry.
	 *
	 * The breadcrumbs returned by Mesh contain the project root node and a node for the current page. Those will be skipped by this helper.
	 *
	 * @param jsonArray The breadcrumbs for the current page
	 * @param options The helper options
	 * @return The result of the {@link EachHelper} applied to the <code>jsonArray</code>, but the first and the last element will be skipped
	 * @throws IOException
	 */
	public Object eachBreadcrumb(JsonArray jsonArray, Options options) throws IOException {
		if (jsonArray == null) {
			return "";
		}

		JsonArray copy = jsonArray.copy();

		// When project per node is active, the root folder is not in the breadcrumb.
		if (isProjectPerNode) {
			copy.remove(0);
		}

		// During a preview the last entry of the breadcrumb (the page itself) is missing.
		if (RenderContextUtils.isPreview(options.context)) {
			copy.remove(copy.size() - 1);
		}

		if (copy.isEmpty()) {
			return "";
		}

		return new EachHelper().apply(copy, options);
	}

	/**
	 * Returns true if breadcrumb object has set startpage. Otherwise returns false
	 *
	 * @param breadcrumb
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object hasDirectStartpage(JsonObject breadcrumb, Options options) throws IOException {
		JsonObject startPage = JsonUtils.getValue(breadcrumb, "node.fields.startpage", null);

		if (startPage != null) {
			return options.fn();
		}

		String startPageUrl = JsonUtils.getValue(breadcrumb, "node.fields.startpageurl", null);

		if (startPageUrl != null) {
			return options.fn();
		}

		return options.inverse();
	}

	/**
	 * Returns true if current page is startpage of parent folder. Otherwise returns false
	 *
	 * @param data
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object isCurrentPageStartpage(JsonObject data, Options options) throws IOException {
		JsonArray breadcrumbs = JsonUtils.getValue(data, "node.breadcrumb", new JsonArray());

		// First entry is the root node of the project, last entry is the node of the current page itself.
		int lastFolderIdx = breadcrumbs.size() - 2;

		if (lastFolderIdx < 1) {
			return options.inverse();
		}

		String currentPage = JsonUtils.getValue(data, "node.uuid", null);
		String startPage = JsonUtils.getValue(breadcrumbs.getJsonObject(lastFolderIdx), "node.fields.startpage.uuid", null);

		if (currentPage == null || !currentPage.equals(startPage)) {
			return options.inverse();
		}

		return options.fn();
	}

	public Object removeLinks(String overview) throws IOException {
		if ("<node overview>".equals(overview)) {
			return overview;
		}
		Document doc = Jsoup.parse(overview, "", Parser.xmlParser());
		Elements elements = doc.getElementsByAttribute("data-removelinks");
		if (elements != null) {
			for (Element teaser : elements) {
				teaser.select("a[href]").remove();
			}
		}
		return doc.toString();
	}

	public Object increment(String i) {
		return Integer.parseInt(i) + 1 + "";
	}
}
