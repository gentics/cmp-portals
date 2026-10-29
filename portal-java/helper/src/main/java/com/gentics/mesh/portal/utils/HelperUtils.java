package com.gentics.mesh.portal.utils;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.portal.util.JsonUtils;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import io.reactivex.annotations.NonNull;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Miscellaneous utility methods for Handlebars helpers.
 */
public class HelperUtils {

	private static final Logger log = LoggerFactory.getLogger(HelperUtils.class);

	private static final String FIELD_LANGUAGES = "languages";
	private static final String FIELD_LANGUAGE = "language";
	private static final String FIELD_PATH = "path";

	/**
	 * Wrap the result of the given helper with the specifed pre- and postfix.
	 *
	 * @param delegate The helper which output should be wrapped
	 * @param context The current context
	 * @param options The handlebars options
	 * @param prefix The prefix for wrapping
	 * @param postfix The postfix for wrapping
	 * @param <T> The result type of the specified helper
	 * @return The given <code>option</code>s buffer after wrapping
	 * @throws IOException When the helper cannot be applied
	 */
	public static <T> Object wrap(Helper<T> delegate, T context, Options options, String prefix, String postfix)
			throws IOException {
		Options.Buffer buffer = options.buffer();

		buffer.append(prefix);
		delegate.apply(context, options);
		buffer.append(postfix);

		return buffer;
	}

	/**
	 * Get the first parameter of the specified <code>type</code> from the
	 * <code>options</code> (optionally skipping <code>skip</code> parameters).
	 *
	 * <p>
	 *     Only parameters with the specified type will be skipped.
	 * </p>
	 *
	 * <p>
	 *     When specifying a helper explicit parameters are still in the
	 *     <code>option</code>s parameters, so to get an optional parameter
	 *     with the same type as a mandatory parameter, one has to skip the
	 *     mandatory ones.
	 * </p>
	 *
	 * @param options The Handlebars options
	 * @param type The desired type of the parameter
	 * @param skip How many matching parameters to skip
	 * @param <T> The type of the parameter
	 * @return The first parameter of the specified type after skipping <code>skip</code> parameters
	 */
	public static <T> Optional<T> getParam(Options options, Class<T> type, int skip) {
		for (Object param : options.params) {
			if (type.isInstance(param)) {
				if (skip <= 0) {
					return Optional.of(type.cast(param));
				}
				skip--;

				if (log.isTraceEnabled()) {
					log.trace("Skipping {} parameter {} ({} left to skip)", type.getSimpleName(), param, skip);
				}
			}
		}

		if (log.isDebugEnabled()) {
			log.debug("No parameter of type {} found", type.getName());
		}

		return Optional.empty();
	}

	/**
	 * Get the first Boolean parameter.
	 *
	 * @param options The handlebars options
	 * @return The first Boolean parameter if present
	 */
	public static Optional<Boolean> getBooleanParam(Options options) {
		return getBooleanParam(options, 0);
	}

	/**
	 * Get the first Boolean parameter after skipping the specified number of parameters.
	 *
	 * @param options The handlebars options
	 * @return The first Boolean parameter if present
	 */
	public static Optional<Boolean> getBooleanParam(Options options, int skip) {
		return getParam(options, Boolean.class, skip);
	}

	/**
	 * Get the first Integer parameter.
	 *
	 * @param options The handlebars options
	 * @return The first Integer parameter if present
	 */
	public static Optional<Integer> getIntegerParam(Options options) {
		return getIntegerParam(options, 0);
	}

	/**
	 * Get the first Integer parameter after skipping the specified number of parameters.
	 *
	 * @param options The handlebars options
	 * @return The first Integer parameter if present
	 */
	public static Optional<Integer> getIntegerParam(Options options, int skip) {
		return getParam(options, Integer.class, skip);
	}

	/**
	 * Get the first String parameter.
	 *
	 * @param options The handlebars options
	 * @return The first String parameter if present
	 */
	public static Optional<String> getStringParam(Options options) {
		return getStringParam(options, 0);
	}

	/**
	 * Get the first String parameter after skipping the specified number of parameters.
	 *
	 * @param options The handlebars options
	 * @return The first String parameter if present
	 */
	public static Optional<String> getStringParam(Options options, int skip) {
		return getParam(options, String.class, skip);
	}

	/**
	 * Get the first JsonArray parameter.
	 *
	 * @param options The handlebars options
	 * @return The first JsonArray parameter if present
	 */
	public static Optional<JsonArray> getJsonArrayParam(Options options) {
		return getJsonArrayParam(options, 0);
	}

	/**
	 * Get the first JsonArray parameter after skipping the specified number of parameters.
	 *
	 * @param options The handlebars options
	 * @return The first JsonArray parameter if present
	 */
	public static Optional<JsonArray> getJsonArrayParam(Options options, int skip) {
		return getParam(options, JsonArray.class, skip);
	}

	/**
	 * Get the first JsonObject parameter.
	 *
	 * @param options The handlebars options
	 * @return The first JsonObject parameter if present
	 */
	public static Optional<JsonObject> getJsonObjectParam(Options options) {
		return getJsonObjectParam(options, 0);
	}

	/**
	 * Get the first JsonObject parameter after skipping the specified number of parameters.
	 *
	 * @param options The handlebars options
	 * @return The first JsonObject parameter if present
	 */
	public static Optional<JsonObject> getJsonObjectParam(Options options, int skip) {
		return getParam(options, JsonObject.class, skip);
	}

	/**
	 * Do language fallback on the given nodes path.
	 *
	 * <p>
	 *     When no <code>language</code> field is present in the node, its <code>path</code> field is returned.
	 *     Otherwise it will iterate over the languages (which need to have a <code>language</code> and a
	 *     <code>path</code> field), and pick one using this priorities in descending order:
	 *     <ol>
	 *         <li>The specified language</li>
	 *         <li>The portals default language</li>
	 *         <li>Any language, but only if the <code>path</code> field of the node is not set</li>
	 *     </ol>
	 * </p>
	 *
	 * @see #nodeLangFallback(JsonObject, String, String)
	 * @param node The node to do language fallback for
	 * @param language The desired language
	 * @param defaultLanguage
	 * @return The path for the best matching language
	 */
	public static String pathLangFallback(JsonObject node, String language, String defaultLanguage) {
		return nodeLangFallback(node, language, defaultLanguage).getString(FIELD_PATH, "");
	}

	/**
	 * Do language fallback on the given node.
	 *
	 * <p>
	 *     When no <code>language</code> field is present in the node, the node
	 *     itself is returned. Otherwise the method will iterate over the
	 *     languages (which need to have a <code>language</code> and a
	 *     <code>path</code> field), and pick one using this priorities in
	 *     descending order:
	 *     <ol>
	 *         <li>The specified language</li>
	 *         <li>The portals default language</li>
	 *         <li>Any language, but only if the <code>path</code> field of the node is not set</li>
	 *     </ol>
	 * </p>
	 *
	 * @param node The node to do language fallback for
	 * @param language The desired language
	 * @param defaultLanguage The portals default language
	 * @return The best matching language variant of the given node
	 */
	@NonNull
	public static JsonObject nodeLangFallback(JsonObject node, String language, String defaultLanguage) {
		if (node == null || node.isEmpty()) {
			log.warn("Cannot perform language fallback: missing or empty node");

			return new JsonObject();
		}

		// When the GraphQL query does not retrieve all languages, just return
		// the given node itself.
		if (!node.containsKey(FIELD_LANGUAGES)) {
			log.trace("No language variants available");

			return node;
		}

		JsonObject bestMatch = null;

		for (Object variant : node.getJsonArray(FIELD_LANGUAGES)) {
			String nodeLanguage = ((JsonObject) variant).getString(FIELD_LANGUAGE);

			// Prefer the page in the current portal language ...
			if (language.equals(nodeLanguage)) {
				bestMatch = (JsonObject) variant;
				break;
			}

			// ... take the default language or any language if none has matched yet.
			if (defaultLanguage.equals(nodeLanguage) || bestMatch == null) {
				bestMatch = (JsonObject) variant;
			}
		}

		return bestMatch != null ? bestMatch : new JsonObject();
	}

	/**
	 * Do language fallback of a field of the specified <code>node</code>.
	 *
	 * <p>
	 *     The <code>fields</code> field of the specified <code>node</code> is searched for the following field names
	 *     in this order and the first match is returned.
	 *     <ol>
	 *         <li><code>fields.[fieldName]_[language]</code></li>
	 *         <li><code>fields.[fieldName]_[defaultLanguage]</code></li>
	 *         <li><code>fields.[fieldName]</code></li>
	 *     </ol>
	 * </p>
	 *
	 * @param node The node to do language fallback for
	 * @param fieldName The field name to translate
	 * @param language The desired language
	 * @param defaultLanguage The portals default language
	 * @return The field with the best matching language
	 */
	public static String langFallback(JsonObject node, String fieldName, String language, String defaultLanguage) {
		if (node == null || StringUtils.isBlank(fieldName)) {
			log.warn("Cannot perform language fallback: missing node or property");

			return "";
		}

		if (StringUtils.isBlank(language)) {
			language = defaultLanguage;
		}

		String basePath = "fields." + fieldName;
		String[] paths = new String[] {
			basePath + "_" + language,
			basePath + "_" + defaultLanguage,
			basePath
		};

		for (String path : paths) {
			String value = JsonUtils.getValue(node, path, null);

			if (!StringUtils.isBlank(value)) {
				return value;
			}
		}

		log.warn("No language fallback found for \"{}\"", fieldName);

		if (log.isTraceEnabled()) {
			log.trace(
				"property: {}, language: {}, default language: {}, node:\n{}",
				fieldName,
				language,
				defaultLanguage,
				node.encodePrettily());
		}

		return "";
	}
}
