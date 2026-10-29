package com.gentics.mesh.portal.helper;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.NavigationOptions;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.RenderContextUtils;
import com.gentics.mesh.portal.utils.HelperUtils;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.helper.EachHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helpers for rendering navigation and breadcrumbs.
 */
@Singleton
public class NavigationHelper {

	private static final String FIELD_ANCESTORS = "ancestors";

	/** The name of the default {@link NavigationHelperProfile}. */
	private static final String PROFILE_DEFAULT = "default";
	/** The name of the unwrapped {@link NavigationHelperProfile}. */
	private static final String PROFILE_UNWRAPPED = "unwrapped";

	private static final Logger log = LoggerFactory.getLogger(NavigationHelper.class);

	/** Whether "Project per Node" is active for the project served by the portal. */
	private final boolean isProjectPerNode;
	/** The portals default language. */
	private final String defaultLanguage;
	/** The navigation options for the portal. */
	private final NavigationOptions navOptions;
	/** The default profile for rendering navigation enries. */
	private final NavigationHelperProfile defaultProfile;
	/** The registered navigation proviles. */
	private final Map<String, NavigationHelperProfile> profiles = new HashMap<>();

	@Inject
	public NavigationHelper(PortalConfig config) {
		String schemaPrefix = config.getMeshSchemaPrefix();

		this.isProjectPerNode = !StringUtils.isBlank(schemaPrefix) && !schemaPrefix.equals(config.getMeshProject());
		this.defaultLanguage = config.getDefaultLanguage();
		this.navOptions = config.getNavigationOptions();
		this.defaultProfile = new NavigationHelperProfile().setName(PROFILE_DEFAULT);

		registerProfile(PROFILE_DEFAULT, defaultProfile);
		registerProfile(PROFILE_UNWRAPPED, new NavigationHelperProfile().setName(PROFILE_UNWRAPPED).noWrapping());
	}

	/**
	 * Register the given profile under the specified name.
	 *
	 * @param name The name of the profile
	 * @param profile The profile
	 */
	public void registerProfile(String name, NavigationHelperProfile profile) {
		if (profile == null) {
			log.warn("Refusing to register null options \"{}\"", name);

			return;
		}

		if (StringUtils.isBlank(name)) {
			name = profile.getName();
		} else {
			// The name of the profile is only used in logging output, but
			// should still match with the registered name.
			profile.setName(name);
		}

		if (StringUtils.isBlank(name)) {
			log.warn("Refusing to register profile without a name");
		} else {
			log.info("Registering profile \"{}\"", name);
			profiles.put(name, profile);
		}
	}

	/**
	 * Iterate over each breadcrumb.
	 *
	 * <p>
	 *     When <em>Project per Node</em> is not active for the served project, the first entry (which will be
	 *     empty) is removed.
	 * </p>
	 *
	 * <p>
	 *     All entries with <code>navhidden</code> are removed (this happend prior to removing excess entries
	 *     via the optional <em>maximum entries</em> parameter; see below).
	 * </p>
	 *
	 * <p>
	 *     The following <em>optional</em> parameters can be provided anywhere after <code>node</code>:
	 *     <ul>
	 *         <li>
	 *             <em>Hide root</em> (Boolean, default: <code>true</code>): whether to hide the first entry>
	 *         </li>
	 *         <li>
	 *             <em>Maximum entries</em> (Integer, default: unlimited): the maximum number of entries. When more
	 *             breadcrumbs are present, only the last ones are retained. The other ones will be replaced by a
	 *             single entry with the <code>trimmed</code> field set to <code>true</code>.
	 *         </li>
	 *         <li>
	 *             <em>Profile</em> (String, default: <code>"default"</code>): the name of the
	 *             {@link NavigationHelperProfile} to use. Breadcrumbs only use the
	 *             {@link NavigationHelperProfile#isAutoLanguageFallback() autoLanguageFallback} information from
	 *             the profile.
	 *         </li>
	 *     </ul>
	 * </p>
	 *
	 * <p>
	 *     Each remaining breadcrumb entry will have at least the following properties:
	 *     <ul>
	 *         <li>
	 *             <code>name</code>: the (possible translated) name of the entry.
	 *         </li>
	 *         <li>
	 *             <code>url</code>: the start page URL of the entry (possibly with language fallback), or the URL
	 *             of the entry itself if it is a page.
	 *         </li>
	 *         <li>
	 *             <code>trimmed</code>: flag whether some elements at the beginning where removed. This can only be
	 *             <code>true</code> on the first entry, if at all.
	 *         </li>
	 *         <li>
	 *             <code>current</code>: flat whether this element is the currently displayed page. This can only be
	 *             <code>true</code> on the last entry, if at all.
	 *         </li>
	 *     </ul>
	 * </p>
	 *
	 * @param node The currently rendered node
	 * @param options The Handlebars options
	 * @return The result of an {@link EachHelper} applied to the preprocessed breadcrumbs
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	public Object eachBc(JsonObject node, Options options) throws IOException {
		JsonArray breadcrumbs = getBreadcrumbs(node);

		if (HelperUtils.getBooleanParam(options).orElse(true)) {
			log.trace("Removing root folder from breadcrumb");
			breadcrumbs.remove(0);
		}

		if (isCurrentFolderStartpage(breadcrumbs, RenderContextUtils.getNodeToRender(options.context))) {
			log.trace("Removing folder start page from breadcrumb");

			int lastIdx = breadcrumbs.size() - 1;

			// There are at least two elements in the breadcrumbs array: the
			// last folder in the breadcrumbs and its startpage (the currently
			// displayed page). Otherwise isCurrentFolderStartpage() would not
			// have returned true.
			breadcrumbs.remove(lastIdx--);
			breadcrumbs.getJsonObject(lastIdx)
				.put("current", true)
				.put("currentFolderStartpage", true);
		}

		processAndTrimBreadcrumbs(
			breadcrumbs,
			HelperUtils.getIntegerParam(options).orElse(0),
			RenderContextUtils.getLanguage(options.context).orElse(defaultLanguage),
			HelperUtils.getStringParam(options).orElse("default"));

		return new EachHelper().apply(breadcrumbs, options);
	}

	/**
	 * Iterate over each breadcrumb with minimal preprocessing.
	 *
	 * <p>
	 *     Only the removal of the root node when <em>Project per Node</em> is active will be done.
	 * </p>
	 *
	 * @param options The Handlebars options
	 * @return The result of the {@link EachHelper} applied to the breadcrumb entries
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	public Object eachBcRaw(Options options) throws IOException {
		return new EachHelper().apply(getBreadcrumbs(options), options);
	}

	/**
	 * Iterate over each top level navigation entry.
	 *
	 * @see #delegateEachNav(JsonArray, NavigationHelperProfile, Options)
	 * @param options The Handlebars options
	 * @return The result of applying the {@link EachHelper} to the top level navigation elements.
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	public Object eachNav(Options options) throws IOException {
		NavigationHelperProfile profile =
			profiles.getOrDefault(HelperUtils.getStringParam(options).orElse(PROFILE_UNWRAPPED), defaultProfile);

		return delegateEachNav(RenderContextUtils.getNavigation(options.context), profile, options);
	}

	/**
	 * Iterate over each navigation entry below the specified folder.
	 *
	 * <p>
	 *     Whether the child elements of the specified folders are actually shown, is determine by the
	 *     following optional parameters:
	 *     <ul>
	 *         <li>
	 *             <em>Maximum level</em> (Integer; default: unlimited): the maximum level in the navigation tree
	 *             an element may have to still be visible. When <code>navElement</code> is already at the maximum
	 *             level, its children will not be shown.
	 *         </li>
	 *         <li>
	 *             <em>Force startpage</em> (Boolean; default: false): whether a folder must have a start page for
	 *             its children to be visible. When <code>navElement</code> does not have a start page, its children
	 *             will not be shown.
	 *         </li>
	 *     </ul>
	 * </p>
	 *
	 * @see #delegateEachNav(JsonArray, NavigationHelperProfile, Options)
	 * @param navElement The navigation element which children should be iterated over
	 * @param options The Handlebars options
	 * @return The result of the {@link EachHelper} applied to the children of <code>navElement</code>
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	public Object eachNavFromFolder(JsonObject navElement, Options options) throws IOException {
		int maxLevel = HelperUtils.getIntegerParam(options).orElse(Integer.MAX_VALUE);
		boolean forceStartpage = !HelperUtils.getBooleanParam(options).orElse(false);

		// We are actually checking if we should show the children of this element,
		// so we reduce the maxLevel temporarily to take the higher level of
		// of the children into account.
		if (!checkVisibility(navElement, maxLevel - 1, forceStartpage, "eachNavFromFolder")) {
			return "";
		}

		NavigationHelperProfile profile =
			profiles.getOrDefault(HelperUtils.getStringParam(options).orElse(PROFILE_DEFAULT), defaultProfile);

		return delegateEachNav(navElement.getJsonArray("children", new JsonArray()), profile, options);
	}

	/**
	 * Iterate over each navigation element at the specified level.
	 *
	 * <p>
	 *     This helper will adjust <code>minLevel</code> to the level of the currently rendered page, if that
	 *     level is higher. When the level should really be fixed, use the
	 *     {@link #eachNavFromFixedLevel(int, int, Options) fixed version} of this method.
	 * </p>
	 *
	 * @see #eachNavFromLevel(int, int, JsonArray, Options)
	 * @param minLevel The minimum level to of navigation elements to iterate over
	 * @param maxDepth The maximum depth to recurse down, starting to count from <code>minLevel</code>
	 * @param options The Handlebars options
	 * @return The result of the {@link EachHelper} applied to the navigation elements at level <code>minLevel</code>
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	public Object eachNavFromLevel(int minLevel, int maxDepth, Options options) throws IOException {
		JsonArray breadcrumbs = getBreadcrumbs(options);

		minLevel = Math.max(minLevel, breadcrumbs.size() - 2);

		return eachNavFromLevel(minLevel, maxDepth, breadcrumbs, options);
	}

	/**
	 * Iterate over each navigation element at the specified level.
	 *
	 * @see #eachNavFromLevel(int, int, JsonArray, Options)
	 * @param minLevel The minimum level to of navigation elements to iterate over
	 * @param maxDepth The maximum depth to recurse down, starting to count from <code>minLevel</code>
	 * @param options The Handlebars options
	 * @return The result of the {@link EachHelper} applied to the navigation elements at level <code>minLevel</code>
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */	public Object eachNavFromFixedLevel(int minLevel, int maxDepth, Options options) throws IOException {
		JsonArray breadcrumbs = getBreadcrumbs(options);

		return eachNavFromLevel(minLevel, maxDepth, breadcrumbs, options);
	}

	/**
	 * Applied the block template when the specified element should be visible in the navigation.
	 *
	 * @see #checkVisibility(JsonObject, int, boolean, String)
	 * @param navElement The navigation element to check
	 * @param options The Handlebars options
	 * @return The result of applying the block template if the element is visible, or the template of the
	 *     inverse block otherwise
	 * @throws IOException When the template or the inverse template cannot be applied
	 */
	public Object visibleInNav(JsonObject navElement, Options options) throws IOException {
		int maxLevel = HelperUtils.getIntegerParam(options).orElse(Integer.MAX_VALUE);
		boolean forceStartpage = !HelperUtils.getBooleanParam(options).orElse(false);

		return checkVisibility(navElement, maxLevel, forceStartpage, "visibleInNav")
			? options.fn()
			: options.inverse();
	}

	/**
	 * Applies the block template when the currently rendered page is at least at the specified navigation level.
	 *
	 * @param level The minimum navigation level the current page must have for the block to be rendered
	 * @param options The Handlebars options
	 * @return The result of applying the block template if the current page has the required level, or the template
	 *     of the inverse block otherwise
	 * @throws IOException When the template or the inverse template cannot be applied
	 */
	public Object minNavLevel(int level, Options options) throws IOException {
		return getBreadcrumbs(options).size() - 1 > level ? options.fn() : options.inverse();
	}

	/**
	 * Tests whether the currently rendered page is the start page of the current folder
	 * (the last folder in the breadcrumbs).
	 *
	 * <p>
	 *     <strong>NOTE</strong> that the UUIDs of the start pages in the breadcrumbs as well as the currently rendered
	 *     page must be available (from the respective GraphQL queries) for this check to be accurate.
	 * </p>
	 *
	 * @param breadcrumbs The breadcrumbs of the currently rendered page
	 * @param page The currently rendered page
	 * @return <code>true</code> when <code>page</code> is the start page of the last folder in
	 *     <code>breadcrumbs</code>, and <code>false</code> otherwise
	 */
	protected boolean isCurrentFolderStartpage(JsonArray breadcrumbs, JsonObject page) {
		if (page == null || page.isEmpty()) {
			log.warn("Empty page specified");

			return false;
		}

		if (breadcrumbs.size() < 2) {
			if (log.isWarnEnabled()) {
				log.warn("Breadcrumbs contain less than two entries:\n{}", breadcrumbs.encodePrettily());
			}

			return false;
		}

		JsonObject lastFolder = breadcrumbs.getJsonObject(breadcrumbs.size() - 2);
		JsonObject lastFolderStartpage = JsonUtils.getValue(lastFolder, "fields.startpage", null);

		if (lastFolderStartpage == null || lastFolderStartpage.isEmpty()) {
			if (log.isWarnEnabled()) {
				log.warn("Folder has no start page information:\n{}", lastFolder.encodePrettily());
			}

			return false;
		}

		if (lastFolderStartpage.containsKey("uuid") && page.containsKey("uuid")) {
			return lastFolderStartpage.getString("uuid").equals(page.getString("uuid"));
		}

		log.debug("Start page or page do not contain UUID, cannot compare");
		if (log.isTraceEnabled()) {
			log.trace("Page:\n{}\nStart page:\n{}", page.encodePrettily(), lastFolderStartpage.encodePrettily());
		}

		return false;
	}

	/**
	 * Iterate over each navigation element at the specified level.
	 *
	 * <p>
	 *     When the currently rendered page is not at least at <code>minLevel</code> nothing will be rendered.
	 * </p>
	 *
	 * <p>
	 *     The optinal <code>Boolean</code> parameter <em>match path prefix</em> can be used to restrict the
	 *     elements to those which paths match the first <code>minLevel</code> segments of the path of the
	 *     currently rendered page. This is <code>true</code> by default.
	 * </p>
	 *
	 * <p>
	 *     The value <code>@maxLevel</code> will be added to the Handlebars context, which is calculated by
	 *     <code>minLevel + maxDepth</code>. When no <code>maxDepth</code> is set (when it is 0 or less),
	 *     <code>@maxLevel</code> will be unlimited.
	 * </p>
	 *
	 * @see #navFromLevel(JsonArray, int, String)
	 * @see #delegateEachNav(JsonArray, NavigationHelperProfile, Options)
	 * @param minLevel The minimal level for navigation elements to iterate over
	 * @param maxDepth The maximum depth starting from <code>minLevel</code>
	 * @param breadcrumbs The breadcrumbs of the currently rendered page
	 * @param options The Handlebars options
	 * @return The result of the {@link EachHelper} applied to the navigation elements at the specified level
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	protected Object eachNavFromLevel(int minLevel, int maxDepth, JsonArray breadcrumbs, Options options)
			throws IOException {
		// The breadcrumbs also contains the page itself, so the number of entries
		// must be actually greater than the minimum level.
		if (breadcrumbs.size() <= minLevel) {
			if (log.isDebugEnabled()) {
				log.debug("Current page level {} is below minimum level {}", breadcrumbs.size(), minLevel);
			}

			return "";
		}

		JsonArray nav = RenderContextUtils.getNavigation(options.context);
		String requiredAncestorUuid = minLevel > 1 && HelperUtils.getBooleanParam(options).orElse(true)
			? breadcrumbs.getJsonObject(minLevel - 1).getString("uuid", "")
			: null;

		options.context.combine(
			"@maxLevel",
			maxDepth <= 0 ? Integer.MAX_VALUE : minLevel + maxDepth);

		NavigationHelperProfile profile =
			profiles.getOrDefault(HelperUtils.getStringParam(options).orElse(PROFILE_DEFAULT), defaultProfile);

		return delegateEachNav(
			navFromLevel(nav, minLevel, requiredAncestorUuid).collect(JsonArray::new, JsonArray::add, JsonArray::addAll),
			profile,
			options);
	}

	/**
	 * Check if the specified navigation element should be visible.
	 *
	 * <p>
	 *     An element is visible if all of the following constraints are satisfied:
	 *     <ul>
	 *         <li>
	 *             it is not an {@link #invalidNavElement(JsonObject, String) invalid} element
	 *         </li>
	 *         <li>
	 *             its level does not exceed <code>maxLevel</code>
	 *         </li>
	 *         <li>
	 *             it has a startpage, <strong>or</strong> <code>forceStartpage</code> is <code>false</code>, and at least
	 *             one of its descendants is visible by the rules of this method
	 *         </li>
	 *     </ul>
	 * </p>
	 *
	 * @param navElement The element to be checked for visibility
	 * @param maxLevel The maximum level of navigation elements
	 * @param forceStartpage Whether each navigation element must have a start page to be visible
	 * @param helper An identification of the helper who called this function for logging purposes
	 * @return <code>true</code> when the constraints above are met, and <code>false</code> otherwise
	 */
	protected boolean checkVisibility(JsonObject navElement, int maxLevel, boolean forceStartpage, String helper) {
		if (invalidNavElement(navElement, helper)) {
			return false;
		}

		int elementLevel = navElement.getInteger("level", 0);

		if (elementLevel > maxLevel) {
			if (log.isDebugEnabled()) {
				log.debug(
					"Ignoring element {} at level {} (max: {})",
					navElement.getString("uuid"),
					navElement.getInteger("level"),
					maxLevel);
			}

			return false;
		}

		if (navElement.getString("url") != null
			|| JsonUtils.getValue(navElement, "fields.startpage", null) != null
			|| JsonUtils.getValue(navElement, "fields.startpageurl", null) != null) {
			return true;
		}

		if (forceStartpage) {
			if (log.isDebugEnabled()) {
				log.debug("Ignoring element {} because it has no startpage", navElement.getString("uuid"));
			}

			return false;
		}

		if (elementLevel >= maxLevel) {
			if (log.isDebugEnabled()) {
				log.debug(
					"Ignoring children of element {} at level {} (max: {})",
					navElement.getString("uuid"),
					navElement.getInteger("level"),
					maxLevel);
			}

			return false;
		}

		JsonArray children = navElement.getJsonArray("children");

		if (children == null || children.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug("Element {} has no children", navElement.getString("uuid"));
			}

			return false;
		}

		for (Object child : children) {
			// When we come here forceStartPage has to be false.
			if (checkVisibility((JsonObject) child, maxLevel, false, helper)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks whether the specified navigation element is valid for the purposes of the helpers in this class.
	 *
	 * <p>
	 *     For an element to be valid, it must not be <code>null</code> and have either the
	 *     <code>path</code> or <code>url</code> fields set.
	 * </p>
	 *
	 * @param navElement The element to check
	 * @param helper An identification of the helper calling this method for logging purposes
	 * @return <code>true</code> when the specified element is valid, and <code>false</code> otherwise
	 */
	protected boolean invalidNavElement(JsonObject navElement, String helper) {
		boolean isInvalid = navElement == null
			|| !navElement.containsKey("uuid")
			|| !navElement.containsKey("level");

		if (isInvalid) {
			log.warn(
				"Invalid navElement encountered for helper \"{}\":{}",
				helper,
				navElement == null ? " (null)" : "\n" + navElement.encodePrettily());
		}

		return isInvalid;
	}

	/**
	 * Apply an {@link EachHelper} to the specified navigation elements.
	 *
	 * <p>
	 *     The following optional parameters affect the rendering:
	 *     <ul>
	 *         <li>
	 *             <em>Force start page</em> (Boolean; default: <code>false</code>): whether to filter out
	 *             elements which do not have a URL.
	 *         </li>
	 *         <li>
	 *             <em>Profile</em> (String; default: <code>"default"</code>): the {@link NavigationHelperProfile} to
	 *             use
	 *         </li>
	 *     </ul>
	 * </p>
	 *
	 * <p>
	 *     When the used <code>NavigationHelperProfile</code> defines a prefix or a postfix, the result of applying
	 *     the <code>EachHelper</code> will be wrapped accordingly.
	 * </p>
	 *
	 * @see #copyNavElement(Object, Set, String, NavigationHelperProfile, String)
	 * @param elements The elements to iterate over
	 * @param options The Handlebars options
	 * @return The result of the {@link EachHelper} applied to the <code>elements</code>
	 * @throws IOException When the <code>EachHelper</code> cannot be applied
	 */
	protected Object delegateEachNav(JsonArray elements, NavigationHelperProfile profile, Options options) throws IOException {
		JsonObject node = RenderContextUtils.getNodeToRender(options.context);
		String parent = JsonUtils.getValue(node, "parent.uuid", null);
		Set<String> breadcrumbs = getBreadcrumbs(node).stream()
			.map(o -> ((JsonObject) o).getString("uuid", ""))
			.collect(Collectors.toSet());
		Predicate<JsonObject> hasUrl = HelperUtils.getBooleanParam(options).orElse(false)
			? o -> !StringUtils.isBlank(o.getString("url"))
			: o -> true;
		String portalLang = RenderContextUtils.getLanguage(options.context).orElse(defaultLanguage);
		List<JsonObject> copy = elements.stream()
			.map(o -> copyNavElement(o, breadcrumbs, parent, profile, portalLang))
			.filter(hasUrl)
			.collect(Collectors.toList());
		EachHelper delegate = new EachHelper();

		if (profile.hasWrapper()) {
			return HelperUtils.wrap(delegate, copy, options, profile.getPrefix(), profile.getPostfix());
		}

		return delegate.apply(copy, options);
	}

	/**
	 * Get the breadcrumbs of the currently rendered page.
	 *
	 * @see #getBreadcrumbs(JsonObject)
	 * @param options The Handlebars options
	 * @return The breadcrumbs	private static final String FIELD_ANCESTORS = "ancestors"; of the currently rendered page
	 */
	protected JsonArray getBreadcrumbs(Options options) {
		return getBreadcrumbs(RenderContextUtils.getNodeToRender(options.context));

	}

	/**
	 * Get the breadcrumbs from the specified node.
	 *
	 * <p>
	 *     When <em>Project per Node</em> is <em>not</em> active the first element will be skipped.
	 * </p>
	 *
	 * @see #processBreadcrumb(Object)
	 * @param node The node to get the breadcrumbs from
	 * @return The breadcrumbs of <code>node</code>
	 */
	protected JsonArray getBreadcrumbs(JsonObject node) {
		return JsonUtils.getValue(node, "breadcrumb", new JsonArray()).stream()
			.skip(isProjectPerNode ? 0 : 1)
			.map(this::processBreadcrumb)
			.collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
	}

	/**
	 * Trim the given <code>breadcrumbs</code> to the specified length <code>maxEntries</code> and
	 * possibly do language fallback on the name and URL.
	 *
	 * <p>
	 *     Folders hidden from Navigation are also removed.
	 * </p>
	 *
	 * <p>
	 *     Language fallback is only performed when the specified profile has
	 *     {@link NavigationHelperProfile#isAutoLanguageFallback() autoLanguageFallback} enabled.
	 * </p>
	 *
	 * <p>
	 *     When any entries are removed due to <code>maxEntries</code> the first entry of the result will be an empty
	 *     entry with just the field <code>trimmed</code> set to <code>true</code>.
	 * </p>
	 *
	 * @param breadcrumbs The breadcrumbs to process
	 * @param maxEntries The maximum number of entries in the result
	 * @param language The desired language for language fallback
	 * @param profileName The name of the navigation profile to use
	 */
	private void processAndTrimBreadcrumbs(JsonArray breadcrumbs, int maxEntries, String language, String profileName) {
		NavigationHelperProfile profile = profiles.getOrDefault(profileName, defaultProfile);
		String hiddenField = navOptions.getHiddenField();
		Iterator<Object> it = breadcrumbs.iterator();

		while (it.hasNext()) {
			JsonObject entry = (JsonObject) it.next();

			if (Boolean.TRUE.equals(entry.getBoolean(hiddenField))) {
				if (log.isTraceEnabled()) {
					log.trace("Removing hidden entry {} from breadcrumb", entry.getString("uuid"));
				}

				it.remove();
			} else {
				JsonObject fields = entry.getJsonObject("fields", new JsonObject());
				String name;
				String url;

				if (profile.isAutoLanguageFallback()) {
					name = HelperUtils.langFallback(entry, "name", language, defaultLanguage);
					url = HelperUtils.pathLangFallback(fields.getJsonObject("startpage"), language, defaultLanguage);
				} else {
					name = fields.getString("name", "");
					url = JsonUtils.getValue(fields, "startpage.path", "");
				}

				// When this is the last entry in the breadcrumbs and we could
				// not determine an URL above, this last entry is actually a
				// page, and we just take its path as the URL.
				if (!it.hasNext() && StringUtils.isBlank(url)) {
					url = entry.getString("path");
				}

				entry
					.put("name", name)
					.put("url", url);
			}
		}

		if (maxEntries > 0 && maxEntries < Integer.MAX_VALUE && breadcrumbs.size() > maxEntries) {
			// Leave the last entry that should be trimmed.
			while (breadcrumbs.size() > maxEntries + 1) {
				breadcrumbs.remove(0);
			}

			// The least entry that would have been trimmed is still here, and marked as a special "trimmed" entry.
			breadcrumbs.getJsonObject(0).clear().put("trimmed", true);
		}
	}

	/**
	 * Gather all navigation elements which match <code>minLevel</code> have
	 * <code>requiredAncestor</code> in its breadcrumbs.
	 *
	 * @param curLevel The elements of the currently inspected level
	 * @param minLevel The minimum level for elements to be returned
	 * @param requiredAncestor The UUID of an ancestor folder which must contain
	 * 		the current element (when this is {@code null} no ancestor is required)
	 * @return All navigation elements at level <code>minLevel</code> which
	 * 		have <code>requiredAncestor</code> in their breadcrumbs
	 */
	private Stream<JsonObject> navFromLevel(JsonArray curLevel, int minLevel, String requiredAncestor) {
		if (curLevel == null) {
			return Stream.empty();
		}

		return curLevel.stream()
			.map(o -> (JsonObject) o)
			.flatMap(navElement -> {
				if (navElement.getInteger("level", 0) < minLevel) {
					if (log.isTraceEnabled()) {
						log.trace("Element {} is below minimum level {}", navElement.getString("uuid"), minLevel);
					}

					return navFromLevel(navElement.getJsonArray("children"), minLevel, requiredAncestor);
				}

				if (requiredAncestor == null || navElement.getJsonArray(FIELD_ANCESTORS, new JsonArray()).contains(requiredAncestor)) {
					if (log.isTraceEnabled()) {
						log.trace(
							"Element {} has ancestor \"{}\"",
							navElement.getString("uuid"),
							requiredAncestor == null ? "(none)" : requiredAncestor);
					}

					return Stream.of(navElement);
				}

				if (log.isTraceEnabled()) {
					log.trace("Element {} does not have ancestor \"{}\"", navElement.getString("uuid"), requiredAncestor);
				}

				return Stream.empty();
			});
	}

	/**
	 * Replace the breadcrumb by its language variant, or try to add
	 * <code>uuid</code>, </code><code>path</code> and <code>fields</code>.
	 *
	 * @param obj The breadcrumb to process
	 * @return The language variant if available, or a {@link JsonObject} with
	 * 		its <code>uuid</code>, </code><code>path</code> and
	 * 		<code>fields</code>
	 */
	private JsonObject processBreadcrumb(Object obj) {
		JsonObject entry = (JsonObject) obj;
		JsonObject languageVariant = entry.getJsonObject("node");

		if (languageVariant != null && !languageVariant.isEmpty() && languageVariant.getJsonObject("fields") != null) {
			return languageVariant;
		}

		return new JsonObject()
			.put("uuid", entry.getString("uuid"))
			// We try not to use the path of the navigation elements anymore,
			// but keep it for the time being for backwards compatibility.
			.put("path", entry.getString("path"))
			.put("fields", entry.getJsonObject("fields"));
	}

	/**
	 * Copy a navigation element and set some helper fields.
	 *
	 * <p>
	 *     The resulting copy will have the following fields:
	 *     <ul>
	 *         <li>
	 *             <code>active</code>: whether the element is on the path of the currently rendered page
	 *         </li>
	 *         <li>
	 *             <code>current</code>: whether the element is the parent folder of the currently rendered page
	 *         </li>
	 *         <li>
	 *             <code>level</code>: the level of the navigation element
	 *         </li>
	 *         <li>
	 *             <code>class</code>: the CSS class provided by {@link NavigationHelperProfile#cssClass(boolean, int)}
	 *         </li>
	 *         <li>
	 *             <code>name</code>: the name of the element, possibly with
	 *             {@link HelperUtils#langFallback(JsonObject, String, String, String) language fallback}
	 *         </li>
	 *         <li>
	 *             <code>url</code>: the URL of the elements start page, possibly with
	 *             {@link HelperUtils#pathLangFallback(JsonObject, String, String) language fallback}
	 *         </li>
	 *         <li>
	 *             <code>path</code>: the original path of the element
	 *         </li>
	 *         <li>
	 *             <code>fields</code>: the original fields of the element (start page and sort order field will
	 *             be removed).
	 *         </li>
	 *         <li>
	 *             <code>children</code>: the original children of the element
	 *         </li>
	 *     </ul>
	 * </p>
	 * @param navElement The navigation element to copy
	 * @param breadcrumbs The breadcrumb UUIDs of the currently rendered page
	 * @param parentUuid The UUID of the currently rendered pages parent folder
	 * @param profile The navigation profile to use
	 * @param portalLang The current portal language
	 * @return A copy of the specified navigation element with some helper fields
	 */
	private JsonObject copyNavElement(
			Object navElement,
			Set<String> breadcrumbs,
			String parentUuid,
			NavigationHelperProfile profile,
			String portalLang) {
		JsonObject empty = new JsonObject();
		JsonObject orig = (JsonObject) navElement;
		JsonObject fields = orig.getJsonObject("fields", empty).copy();
		boolean langFallback = profile.isAutoLanguageFallback();
		JsonObject startpage = langFallback
			? HelperUtils.nodeLangFallback(fields.getJsonObject("startpage"), portalLang, defaultLanguage)
			: fields.getJsonObject("startpage");
		String url = startpage.getString("path", "");
		String name = langFallback
			? HelperUtils.langFallback(orig, "name", portalLang, defaultLanguage)
			: fields.getString("name", "");
		int level = orig.getInteger("level", 0);
		boolean active = breadcrumbs.contains(orig.getString("uuid"));

		return new JsonObject()
			.put("active", active)
			.put("current", parentUuid != null && parentUuid.equals(orig.getString("uuid")))
			.put("level", level)
			.put("class", profile.cssClass(active, level))
			.put("name", name)
			.put("startpage", startpage)
			.put("url", url)
			.put("path", orig.getString("path"))
			.put("uuid", orig.getString("uuid"))
			.put("fields", fields)
			.put("children", orig.getJsonArray("children"));
	}
}
