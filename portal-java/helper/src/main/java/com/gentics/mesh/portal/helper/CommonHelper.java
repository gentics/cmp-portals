package com.gentics.mesh.portal.helper;

import static com.gentics.mesh.portal.api.Constants.COOKIE_DISCLAIMER;
import static com.gentics.mesh.portal.util.MiscUtils.getEnumValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.api.content.PreviewMode;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.helper.EachHelper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CommonHelper {

	private final static Logger log = LoggerFactory.getLogger(CommonHelper.class);

	private final PortalConfig config;

	private final EachHelper eachHelper;

	/**
	 * The key for preview mode.
	 *
	 * <p>
	 *     When the routing context key contains dots ('.') handlebars will interpret these as accessing fields, so
	 *     the key needs to be "escaped".
	 * </p>
	 */
	private final String previewKey = String.format("[%s]", PortalRoutingContext.PREVIEW_KEY);

	@Inject
	public CommonHelper(PortalConfig config, EachHelper eachHelper) {
		this.config = config;
		this.eachHelper = eachHelper;
	}

	/**
	 * Determine the current {@link PreviewMode} by looking it up it the context.
	 *
	 * <p>
	 *     When no value is associated with the {@link #previewKey}, the current mode is {@link PreviewMode#PUBLISH},
	 *     when it is an instance of <code>PreviewMode</code> it is exactly this mode. When it is any other value,
	 *     the returned preview mode is {@link PreviewMode#UNKNOWN}.
	 * </p>
	 *
	 * @param options The handlebars options
	 * @return The current <code>PreviewMode</code>
	 */
	private PreviewMode determinePreviewMode(Options options) {
		Object previewMode = options.context.get(previewKey);

		if (previewMode instanceof PreviewMode) {
			return (PreviewMode) previewMode;
		}

		return previewMode == null ? PreviewMode.PUBLISH : PreviewMode.UNKNOWN;
	}

	/**
	 * Return the name of the current render mode.
	 *
	 * @param options The handlebars options
	 * @return The current render mode
	 */
	public Object getRenderMode(Options options) {
		return determinePreviewMode(options).name();
	}

	/**
	 * Check if <code>mode</code> is the current render mode.
	 *
	 * @param modeName The mode to be checked against
	 * @param options The handlebars options
	 * @return The contents of the block when the current render mode is <code>mode</code>, and the inverse
	 * 		block otherwise.
	 * @throws IOException When the block or the reverse block cannot be rendered
	 */
	public Object renderMode(String modeName, Options options) throws IOException {
		PreviewMode wantedMode = getEnumValue(PreviewMode.values(), modeName).orElse(null);
		PreviewMode actualMode = determinePreviewMode(options);

		return wantedMode == actualMode ? options.fn() : options.inverse();
	}

	public Object equals(Object o1, Object o2, Options options) throws IOException {
		if (Objects.equals(o1, o2)) {
			return options.fn();
		}
		return options.inverse();
	}

	/**
	 * returns reversed order list
	 *
	 * @param jsonArray
	 * @param options
	 * @return
	 * @throws IOException
	 */
	public Object eachReversed(JsonArray jsonArray, Options options) throws IOException {
		if (jsonArray == null) {
			return eachHelper.apply(null, options);
		}
		List<Object> reversed = new ArrayList<>();
		// It makes sense to reverse list only if the list contains at least 2 items.
		if (jsonArray.size() >= 2) {
			for (int i = jsonArray.size() - 1; i >= 0; i--) {
				reversed.add(jsonArray.getValue(i));
			}
			return eachHelper.apply(reversed, options);
		}
		return eachHelper.apply(jsonArray, options);
	}

	public Object getArraySize(JsonArray jsonArray) {
		Integer size = Optional.ofNullable(jsonArray)
			.map(JsonArray::size)
			.orElse(0);
		return String.valueOf(size);
	}

	public Object getCurrentProject() {
		return config.getMeshProject();
	}

	public Object getCookieHintPage() {
		return config.getCookieHintPage();
	}

	public Object showCookieHint(Options options) throws IOException {
		String cookieVal = options.get(COOKIE_DISCLAIMER).toString();
		if ((cookieVal != null && cookieVal.equals("true")) || Boolean.TRUE.equals(options.get(PortalRoutingContext.PREVIEW_KEY))) {
			return options.inverse();
		}
		return options.fn();
	}

	/**
	 * Create a pretty printed string representation of the given JSON object.
	 *
	 * @param data The JSON object to pretty print.
	 * @return A pretty printed string representation of the JSON object.
	 */
	public Object prettyPrint(JsonObject data) {
		return data == null ? "(null)" : data.encodePrettily();
	}

	/**
	 * Print the portal base URL.
	 *
	 * <p>
	 *     Intended when a {@code &lt;base href=...&gt;} element is needed.
	 * </p>
	 *
	 * @return The portal base URL.
	 */
	public Object portalBaseUrl() {
		return config.getServerUrl().toString();
	}
}
