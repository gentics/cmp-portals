package com.gentics.mesh.portal.config;

import com.gentics.mesh.portal.config.option.AuthenticationOptions;
import com.gentics.mesh.portal.config.option.KeycloakOptions;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.NavigationOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.config.option.StaticHandlerOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.common.WebEnvironment;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

import static com.gentics.mesh.portal.config.util.ConfigUtil.loadConfig;
import static com.gentics.mesh.portal.config.util.ConfigUtil.loadJson;

/**
 * Utility class that is used to create and load configurations.
 */
public final class PortalConfigLoader {

	private static final Logger log = LoggerFactory.getLogger(PortalConfigLoader.class);

	private PortalConfigLoader() {

	}

	/**
	 * Load the main configuration.
	 *
	 * <p>
	 *     The base configuration is loaded from <em>config/server.yml</em>,
	 *     which is then supplemented by the values in
	 *     <em>config/server.local.yml</em>, and finally settings from
	 *     environment variables are applied.
	 * </p>
	 *
	 * @return The main portal configuration
	 */
	public static PortalConfig createOrLoadOptions() {
		return createOrLoadOptions(null);
	}

	/**
	 * Load the main configuration file with the given {@code defaultConfig}
	 * as default configuration.
	 *
	 * <p>
	 *     After loading the configuration from the file system, the settings
	 *     will be overriden by environment variables if available.
	 * </p>
	 *
	 * @param defaultConfig The default configuration to use
	 * @return The loaded portal configuration merged from the {@code defaultConfig}
	 * 		the contents of <em>server.yml</em> and <em>server.local.yml</em>
	 */
	public static PortalConfig createOrLoadOptions(PortalConfig defaultConfig) {
		PortalConfig options = loadPortalConfig(defaultConfig);

		options.overrideWithEnv();
		checkConfig(options);

		return options;
	}

	/**
	 * Load the Keycloak configuration file.
	 *
	 * @return The merged contents of <em>keycloak.json</em> and
	 * 		<em>keycloak.local.json</em>
	 */
	public static JsonObject loadKeycloakOptions() {
		return loadJson("keycloak.json").mergeIn(loadJson("keycloak.local.json"), true);
	}

	/**
	 * Check the given configuration for problems.
	 *
	 * @param config The configuration to check
	 */
	private static void checkConfig(PortalConfig config) {
		checkDeprecatedConfig(config);

		config.validate();

		if (!config.getCachingOptions().isEnabled()) {
			System.setProperty(WebEnvironment.SYSTEM_PROPERTY_NAME, "dev");
		}
	}

	/**
	 * Try to load the mesh options from different locations (classpath, config folder). Otherwise a default configuration will be generated.
	 *
	 * @param defaultConfig The default configuration to use
	 * @return The loaded portal configuration merged from the {@code defaultConfig}
	 * 		the contents of <em>server.yml</em> and <em>server.local.yml</em>
	 */
	private static PortalConfig loadPortalConfig(PortalConfig defaultConfig) {
		PortalConfig serverOptions = defaultConfig;
		serverOptions = loadConfig("server.yml", serverOptions, PortalConfig.class, true);
		serverOptions = loadConfig("server.local.yml", serverOptions, PortalConfig.class, false);
		return serverOptions;
	}

	/**
	 * Check the configuration for deprecated entries and print warnings.
	 *
	 * @param config The portal configuration to check
	 */
	private static void checkDeprecatedConfig(PortalConfig config) {
		int networkTimeout = config.getNetworkTimeout();
		MeshOptions meshOptions = config.getMeshOptions();

		if (meshOptions.getNetworkTimeout() != PortalConfig.DEFAULT_NETWORK_TIMEOUT) {
			logDeprecation("mesh.networkTimeout", "networkTimeout");

			if (meshOptions.getNetworkTimeout() != networkTimeout) {
				if (networkTimeout == PortalConfig.DEFAULT_NETWORK_TIMEOUT) {
					// The new timeout setting seems unchanged, but the old one has a custom value. For backwards
					// compatibility use the value of the old setting.
					logConflict( "networkTimeout", networkTimeout, "mesh.networkTimeout", meshOptions.getNetworkTimeout());
					config.setNetworkTimeout(meshOptions.getNetworkTimeout());
				} else {
					logConflict("mesh.networkTimeout", meshOptions.getNetworkTimeout(), "networkTimeout", networkTimeout);
				}
			}
		}

		String staticPath = config.getStaticPath();

		// if the static path is not configured the old way
		if (staticPath != null) {
			StaticHandlerOptions staticHandlerOptions = config.getStaticHandlerOptions();

			logDeprecation("staticPath", "static.path");

			if (!staticPath.equals(staticHandlerOptions.getPath())) {
				logConflict("staticPath", staticPath, "static.path", staticHandlerOptions.getPath());
			}
		}

		/***********************************************************************
		 *** Breadcrumbs configuration
		 **********************************************************************/

		int breadcrumbsLevel = config.getBreadcrumbsLevel();

		if (breadcrumbsLevel != 0) {
			NavigationOptions navigationOptions = config.getNavigationOptions();

			logDeprecation("breadcrumbsLevel", "navigation.breadcrumbsLevel");

			if (breadcrumbsLevel != navigationOptions.getBreadcrumbsLevel()) {
				logConflict(
					"breadcrumbsLevel",
					breadcrumbsLevel,
					"navigation.breadcrumbsLevel",
					navigationOptions.getBreadcrumbsLevel());
			}
		}

		/***********************************************************************
		 *** Keycloak configuration
		 **********************************************************************/

		AuthenticationOptions authOptions = config.getAuthOptions();

		if (StringUtils.isNotBlank(authOptions.getCheckEndpoint())) {
			logDeprecation("authentication.checkEndpoint");
		}

		if (StringUtils.isNotBlank(authOptions.getCallbackHost())) {
			logDeprecation("authentication.callbackHost");
		}

		String authCallbackEndpoint = authOptions.getCallbackEndpoint();

		if (authCallbackEndpoint != null) {
			KeycloakOptions keycloakOptions = authOptions.getKeycloakOptions();

			logDeprecation("authentication.callbackEndpoint", "authentication.keycloak.callbackPath");

			if (!authCallbackEndpoint.equals(keycloakOptions.getCallbackPath())) {
				logConflict(
					"authentication.callbackEndpoint",
					authCallbackEndpoint,
					"authentication.keycloak.callbackPath",
					keycloakOptions.getCallbackPath());
			}
		}

		/***********************************************************************
		 *** Security configuration
		 **********************************************************************/

		SecurityOptions securityOptions = config.getSecurityOptions();
		String portalApiKey = config.getPortalAPIKey();

		if (portalApiKey != null) {
			logDeprecation("portalAPIKey", "security.apiKey");

			// The portal API key has no default value, so we propagate the
			// value from the deprecated setting.
			if (StringUtils.isBlank(securityOptions.getApiKey())) {
				securityOptions.setApiKey(portalApiKey);
			} else if (!securityOptions.getApiKey().equals(portalApiKey)) {
				logConflict(
					"portalAPIKey",
					portalApiKey,
					"security.apiKey",
					securityOptions.getApiKey());
			}
		}

		Map<String, String> additionalResponseHeaders = config.getAdditionalResponseHeaders();

		if (!additionalResponseHeaders.isEmpty()) {
			logDeprecation("additionalResponseHeaders", "security.additionalResponseHeaders");

			if (securityOptions.getAdditionalResponseHeaders().isEmpty()) {
				securityOptions.setAdditionalResponseHeaders(additionalResponseHeaders);
			} else {
				logConflict(
					"additionalResponseHeaders",
					additionalResponseHeaders,
					"security.additionalResponseHeaders",
					securityOptions.getAdditionalResponseHeaders());
			}
		}
	}

	/**
	 * Log a warning about a deprecated configuration entry, and point to the
	 * entry that should be used instead.
	 *
	 * @param oldPath
	 *            The deprecated entry
	 * @param newPath
	 *            The new entry
	 */
	private static void logDeprecation(String oldPath, String newPath) {
		log.warn("Configuration contained deprecated entry \"{}\", it is recommended to use \"{}\" instead", oldPath, newPath);
	}

	/**
	 * Log a warning about a deprecated configuration entry.
	 *
	 * @param path
	 *            The deprecated entry
	 */
	private static void logDeprecation(String path) {
		log.warn("Configuration contained deprecated entry \"{}\", which is obsolete now", path);
	}

	/**
	 * Log a warning about a conflict between a deprecated configuratio entry and a correct one.
	 *
	 * @param oldPath
	 *            The deprecated entry
	 * @param oldValue
	 *            The value of the deprecated entry
	 * @param newPath
	 *            The new entry
	 * @param newValue
	 *            The value of the new entry
	 */
	private static void logConflict(String oldPath, Object oldValue, String newPath, Object newValue) {
		log.warn("Conflicting settings for {}: {} and {}: {}, using {}", oldPath, oldValue, newPath, newValue, newValue);
	}
}
