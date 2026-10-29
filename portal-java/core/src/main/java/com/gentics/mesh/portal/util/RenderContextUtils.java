package com.gentics.mesh.portal.util;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.github.jknack.handlebars.Context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Various utility methods for handling {@link Context} during rendering.
 *
 * <p>
 *     This class is intended to access values stored in the routing context
 *     under keys from {@link Constants} or by special methods of
 *     {@link com.gentics.mesh.portal.context.PortalRoutingContext}.
 * </p>
 */
public final class RenderContextUtils {

	// The routing context keys contain '.' which are interpreted as path
	// separators by Context, so we need to escape them.
	private static final String LANGUAGE_KEY = '[' + Constants.RC_LANGUAGE + ']';
	private static final String NODE_TO_RENDER_KEY = '[' + Constants.RC_NODE_TO_RENDER + ']';

	// Prevent instantiation.
	private RenderContextUtils() {
	}

	/**
	 * Get the value for the specified {@code key} from the {@code context}.
	 *
	 * <p>
	 *     If the {@code key} contains the '{@code .}' character the key will
	 *     be escaped as {@code [key]}.
	 * </p>
	 *
	 * <p>
	 *     When the value cannot be found in the context (or is {@code null})
	 *     and a {@code defaultValue} supplier has been specified, it will be
	 *     used to create a default value. Otherwise the final result will be
	 *     {@code null}.
	 * </p>
	 *
	 * @param context The helper context
	 * @param key The key to retrieve the value for
	 * @param defaultValue An optional supplier for a default value if
	 * 		{@code key} does not yield a result in the {@code context}
	 * @param <T> The value of the returned value
	 * @return The value that is stored in the {@code context} under the
	 * 		specified {@code key}, or the value created by the
	 * 		{@code defaultValue} supplier if present. May be {@code null}.
	 */
	public static <T> T getValue(Context context, String key, Supplier<T> defaultValue) {
		if (context == null || key == null ) {
			return null;
		}

		if (key.indexOf('.') >= 0) {
			key = '[' + key + ']';
		}

		Object val = context.get(key);

		if (val == null && defaultValue != null) {
			return defaultValue.get();
		}

		return (T) val;
	}

	/**
	 * Convenience method for {@link #getValue(Context, String, Supplier)}
	 * without a {@code defaultValue} supplier.
	 *
	 * @param context The helper context
	 * @param key The key to retrieve the value for
	 * 		{@code key} does not yield a result in the {@code context}
	 * @param <T> The value of the returned value
	 * @return The value that is stored in the {@code context} under the
	 * 		specified {@code key}, or the value created by the
	 * 		{@code defaultValue} supplier if present. May be {@code null}.
	 */
	public static <T> T getValue(Context context, String key) {
		return getValue(context, key, null);
	}

	/**
	 * Get the currently rendered page.
	 *
	 * @param context The Handlebars context
	 * @return The currently rendered page
	 */
	public static JsonObject getNodeToRender(Context context) {
		return (JsonObject) context.get(NODE_TO_RENDER_KEY);
	}

	/**
	 * Get the navigation elements.
	 *
	 * @param context The Handlebars context
	 * @return The navigation elements
	 */
	public static JsonArray getNavigation(Context context) {
		return getValue(context, PortalRoutingContext.NAVIGATION_KEY, JsonArray::new);
	}

	/**
	 * Try to get the currently set language of the portal.
	 *
	 * @see PortalRoutingContext#setLanguage(String, LanguageSource)
	 * @param context The data in the current routing context
	 * @return The currently set language
	 */
	public static Optional<String> getLanguage(Context context) {
		Object language = context.get(LANGUAGE_KEY);

		if (language != null) {
			return Optional.of(language.toString());
		}

		return Optional.empty();
	}

	/**
	 * Try to get the language of the rendered page.
	 *
	 * @param context The data in the current routing context
	 * @return The language of the page
	 */
	public static Optional<String> getNodeLanguage(Context context) {
		Object node = context.get(NODE_TO_RENDER_KEY);

		if (node instanceof JsonObject) {
			return Optional.ofNullable(((JsonObject) node).getString("language"));
		}

		return Optional.empty();
	}

	/**
	 * Check whether the current request is for a preview.
	 *
	 * @param context The Handlebars context
	 * @return <code>true</code> when the current request is for a preview, and <code>false</code> otherwise
	 */
	public static boolean isPreview(Context context) {
		return context.get(PortalRoutingContext.PREVIEW_KEY) != null;
	}
}
