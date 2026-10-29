package com.gentics.mesh.portal.i18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CachingOptions;

/**
 * Control for {@link ResourceBundle} implementation that reads translations from YAML files located in configured i18n path (see {@link PortalConfig#getI18nPath()}).
 */
@Singleton
public class YamlResourceBundleControl extends Control {
	/**
	 * Supported formats (i.e. extensions)
	 */
	private final static List<String> FORMATS = Arrays.asList("yml", "yaml");

	private final PortalConfig config;

	@Inject
	public YamlResourceBundleControl(PortalConfig config) {
		this.config = config;
	}

	@Override
	public List<String> getFormats(String baseName) {
		return FORMATS;
	}

	@Override
	public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
			throws IllegalAccessException, InstantiationException, IOException {
		Objects.requireNonNull(baseName, "Basename must not be null");
		Objects.requireNonNull(locale, "Locale must not be null");
		Objects.requireNonNull(format, "Format must not be null");
		Objects.requireNonNull(loader, "Classloader must not be null");

		ResourceBundle bundle = null;
		File file = getFile(baseName, locale, format);
		if (file != null && file.exists()) {
			try (InputStream in = new FileInputStream(file)) {
				bundle = new YamlResourceBundle(in);
			}
		}

		return bundle;
	}

	@Override
	public long getTimeToLive(String baseName, Locale locale) {
		Objects.requireNonNull(baseName, "Basename must not be null");
		Objects.requireNonNull(locale, "Locale must not be null");

		CachingOptions options = config.getCachingOptions();

		if (options == null) {
			options = new CachingOptions();
		}

		if (!options.isEnabled()) {
			return TTL_DONT_CACHE;
		}

		return super.getTimeToLive(baseName, locale);
	}

	@Override
	public boolean needsReload(String baseName, Locale locale, String format, ClassLoader loader, ResourceBundle bundle, long loadTime) {
		Objects.requireNonNull(baseName, "Basename must not be null");
		Objects.requireNonNull(locale, "Locale must not be null");
		Objects.requireNonNull(format, "Format must not be null");
		Objects.requireNonNull(loader, "Classloader must not be null");

		File file = getFile(baseName, locale, format);

		if (file != null && file.exists()) {
			return file.lastModified() > loadTime;
		} else {
			return false;
		}
	}

	/**
	 * Get the resource file (may be null)
	 * @param baseName base name
	 * @param locale locale
	 * @param format format
	 * @return file (or null)
	 */
	protected File getFile(String baseName, Locale locale, String format) {
		if (FORMATS.contains(format)) {
			String bundleName = toBundleName(baseName, locale);
			String resourceName = toResourceName(bundleName, format);

			return new File(config.getI18nPath(), resourceName);
		} else {
			return null;
		}
	}
}
