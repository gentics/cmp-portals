package com.gentics.mesh.portal.helper;

import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.i18n.YamlResourceBundleControl;
import com.gentics.mesh.portal.util.RenderContextUtils;
import com.gentics.mesh.portal.utils.HelperUtils;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Options;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;

/**
 * Helper that can be used in Handlebar templates for translations.
 */
@Singleton
public class LanguageHelper {
	private static final Logger log = LoggerFactory.getLogger(LanguageHelper.class);

	/** The default bundle to use. */
	public final static String DEFAULT_BUNDLE = "portal";

	private final YamlResourceBundleControl resourceBundleControl;

	/** The configured {@link PortalConfig#getDefaultLanguage() default language}. */
	private final String defaultLanguage;

	@Inject
	public LanguageHelper(YamlResourceBundleControl resourceBundleControl, PortalConfig config) {
		this.resourceBundleControl = resourceBundleControl;
		this.defaultLanguage = config.getDefaultLanguage();
	}

	/**
	 * Get the language specific property with given name from the object.<br>
	 *
	 * <p>
	 *     The first found field will be returned. In the following list, <code>languageParam</code> is the optional
	 *     parameter, <code>portalLanguage</code> is the current portal language, and <code>defaultLanguage</code>
	 *     is the portals default language.
	 *
	 *     <ol>
	 *         <li>fields.[fieldName]_[languageParam]</li>
	 *         <li>fields.[fieldName]_[portalLanguage]</li>
	 *         <li>fields.[fieldName]_[defaultLanguage]</li>
	 *         <li>fields.[fieldName]</li>
	 *     </ol>
	 * </p>
	 *
	 * @param node The node to do language fallback for
	 * @param fieldName The name of the field
	 * @param options The handlebars options
	 * @return The value in the best matching language
	 */
	public Object langFallback(JsonObject node, String fieldName, Options options) {
		String portalLanguage = RenderContextUtils.getLanguage(options.context).orElse(defaultLanguage);
		String language = HelperUtils.getStringParam(options).orElse(portalLanguage);

		return HelperUtils.langFallback(
			node,
			fieldName,
			language,
			defaultLanguage);
	}

	/**
	 * Do language fallback for node path
	 *
	 * <p>
	 *     When the languages field is present in the node, the path is chosen via the following priorities:
	 *     <ul>
	 *         <li>Variant of the current portal language</li>
	 *         <li>Variant of the language of the currently requested page</li>
	 *         <li>Any available language variant</li>
	 *     </ul>
	 * </p>
	 *
	 * @param node The node for which a language fallback for its path is needed
	 * @param options The handlebars options
	 * @return The path to the best fitting language variant of the given node
	 */
	public Object pathLangFallback(JsonObject node, Options options) {
		String portalLanguage = RenderContextUtils.getLanguage(options.context).orElse(defaultLanguage);
		String language = HelperUtils.getStringParam(options).orElse(portalLanguage);

		return HelperUtils.pathLangFallback(node, language, defaultLanguage);
	}

	/**
	 * Translate the given key, using the optionally provided language and resource bundle.
	 *
	 * <p>
	 *     The first optional parameter is the desired language code. When no language is
	 *     specified, the one determined by the portal with
	 *     {@link com.gentics.mesh.portal.context.PortalRoutingContext#setLanguage(String, LanguageSource)}
	 *     will be used.
	 * </p>
	 *
	 * <p>
	 *     The second optional parameter is the resource bundle to use. The default bundle
	 *     is <code>portal</code>.
	 * </p>
	 *
	 * <p>
	 *     <strong>NOTE:</strong> Since the actual language of the rendered page has not the highest priority
	 *     for this helper, it is mainly intended for translations in portal handlebars templates.
	 * </p>
	 *
	 * @param key The key to translate
	 * @param options The options for the current template part (might contain the language and bundle)
	 * @return The translation or empty string
	 */
	public Object i18n(final String key, final Options options) {
		return translate(key, options, RenderContextUtils::getLanguage);
	}

	/**
	 * Translate the given key, using the optionally provided language and resource bundle.
	 *
	 * @see #i18n(String, Options)
	 *
	 * @param key The key to translate
	 * @param options The options for the current template part (might contain the language and bundle)
	 * @param langFallback A function to get a language, when it is not specified in the options
	 * @return The translation or empty string
	 *
	 * @return The translation or empty string
	 */
	private String translate(String key, Options options, Function<Context, Optional<String>> langFallback) {
		String lang = options.param(0, null);
		String bundle = options.param(1, DEFAULT_BUNDLE);

		if (lang == null) {
			lang = langFallback.apply(options.context).orElse(defaultLanguage);
		}

		Locale locale = lang == null ? Locale.getDefault() : new Locale(lang);

		try {
			return ResourceBundle.getBundle(bundle, locale, resourceBundleControl).getString(key);
		} catch (MissingResourceException e) {
			log.warn(
				"Error while translating {} into language {} (bundle {}): {}",
				e,
				key,
				locale.getLanguage(),
				bundle,
				e.getMessage());

			return "";
		}
	}
}
