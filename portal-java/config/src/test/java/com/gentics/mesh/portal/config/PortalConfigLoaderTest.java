package com.gentics.mesh.portal.config;

import com.gentics.mesh.portal.config.option.LoginRedirectOptions;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.rest.client.ProtocolVersion;

import io.vertx.core.http.CookieSameSite;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PortalConfigLoaderTest {

	public static final String MAIN_CONFIG_FILE = "config/server.yml";
	public static final String LOCAL_CONFIG_FILE = "config/server.local.yml";

	private static final String FMT_MAIN_CONFIG = "/config/%s.yml";
	private static final String FMT_LOCAL_CONFIG = "/config/%s.local.yml";
	public static final String MESH_API_KEY = "meshkey";
	public static final String SERVER_URL = "http://serverUrl";
	public static final String PORTAL_API_KEY = "portalAPIKey";
	public static final String MESH_PROJECT = "test123";

	private File mainConfig;
	private File localConfig;

	@Before
	public void setup() throws Exception {
		mainConfig = new File(MAIN_CONFIG_FILE);
		localConfig = new File(LOCAL_CONFIG_FILE);

		if (mainConfig.exists()) {
			mainConfig.delete();
		}

		if (localConfig.exists()) {
			localConfig.delete();
		}

		// Reset the environment for every test.
		set(Collections.emptyMap());
	}

	@After
	public void tearDown() throws IOException {
		FileUtils.deleteDirectory(new File("config"));
	}

	@Test
	public void testOptionsLoader() throws Exception {
		set(createMinimalEnvMap());

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		assertNotNull(config);
		assertTrue("The file should have been created.", mainConfig.exists());
		assertFalse("The local file should not have been created.", localConfig.exists());

	}

	@Test
	public void testApplyEnvs() throws Exception {
		Map<String, String> envMap = createMinimalEnvMap();

		envMap.put(MeshOptions.MESH_HOST_ENV, "host");
		envMap.put(MeshOptions.MESH_PORT_ENV, "8123");
		envMap.put(MeshOptions.MESH_SSL_ENV, "true");
		envMap.put(MeshOptions.MESH_PROTOCOL_VERSION_ENV, ProtocolVersion.HTTP_2.toString());
		envMap.put(SecurityOptions.API_KEY_ENV, PORTAL_API_KEY);

		set(envMap);

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		assertEquals(MESH_PROJECT, config.getMeshProject());
		assertEquals(PORTAL_API_KEY, config.getSecurityOptions().getApiKey());
		assertEquals("host", config.getMeshOptions().getHost());
		assertEquals(true, config.getMeshOptions().isSsl());
		assertEquals(MESH_API_KEY, config.getMeshOptions().getApiKey());
		assertEquals(8123, config.getMeshOptions().getPort());
		assertEquals(new URL(SERVER_URL), config.getServerUrl());
		assertEquals(ProtocolVersion.HTTP_2, config.getMeshOptions().getProtocolVersion());
	}

	/**
	 * Check if environment variables for deprecated entries are propagated
	 * correctly to the new settings.
	 */
	@Test
	public void testDeprecatedEnvsPropagation() throws Exception {
		String portalApiKey = "portalApiKey";
		Map<String, String> envMap = createMinimalEnvMap();

		// The map created by createMinimalEnvMap() already uses the correct
		// environment variable, so we have to remove it to test the
		// propagation.
		envMap.remove(SecurityOptions.API_KEY_ENV);
		envMap.put(PortalConfig.PORTAL_API_KEY_ENV, portalApiKey);

		set(envMap);

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		assertEquals(portalApiKey, config.getSecurityOptions().getApiKey());
	}

	/**
	 * Check if the new versions of deprecated entries are taken, when
	 * both are configured.
	 */
	@Test
	public void testDeprecatedEnvsPriority() throws Exception {
		String deprecatedApiKey = "deprecatedApiKey";
		String expectedApiKey = "expectedApiKey";
		Map<String, String> envMap = createMinimalEnvMap();

		envMap.put(PortalConfig.PORTAL_API_KEY_ENV, deprecatedApiKey);
		envMap.put(SecurityOptions.API_KEY_ENV, expectedApiKey);

		set(envMap);

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		assertEquals(expectedApiKey, config.getSecurityOptions().getApiKey());
	}

	@Test
	public void testApplyEnvsNull() throws Exception {
		Map<String, String> envMap = createMinimalEnvMap();

		envMap.put(PortalConfig.MESH_SCHEMA_PREFIX_ENV, "null");

		set(envMap);

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		assertNull(config.getMeshSchemaPrefix());
	}

	@Test
	public void testApplyEnvsEnum() throws Exception {
		Map<String, String> envMap = createMinimalEnvMap();

		envMap.put(SecurityOptions.COOKIE_SAME_SITE_ENV, "LAX");

		set(envMap);

		PortalConfig config = PortalConfigLoader.createOrLoadOptions();

		assertThat(config.getSecurityOptions().getCookieSameSitePolicy())
			.as("Cookie same site policy")
			.isEqualTo(CookieSameSite.LAX);
	}

	@Test(expected = RuntimeException.class)
	public void testApplyEnvsInvalidEnum() throws Exception {
		Map<String, String> envMap = createMinimalEnvMap();

		envMap.put(SecurityOptions.COOKIE_SAME_SITE_ENV, "InvalidValue");

		set(envMap);

		PortalConfigLoader.createOrLoadOptions();

		fail("Loading configuration should have failed");
	}

	@Test(expected = NullPointerException.class)
	public void testInvalidOptions() {
		PortalConfig config = new PortalConfig();
		config.validate();
	}

	/**
	 * Test whether deprecated settings are still used when the correct setting is not present.
	 */
	@Test
	public void testBackwardsCompatibility() throws IOException {
		PortalConfig config = prepareConfig("backwardsCompatibility");

		assertThat(config)
			.as("Loaded config")
			.isNotNull();

		assertThat(config.getSecurityOptions().getAdditionalResponseHeaders())
			.as("Additional response headers")
			.contains(Pair.of("X-Additional-Header", "deprecated-header-setting"));

		assertThat(config.getSecurityOptions().getApiKey())
			.as("Mesh API key")
			.isEqualTo("deprecated-portal-key-setting");
	}

	/**
	 * Test whether deprecated configuration settings are overridden by the correct settings.
	 */
	@Test
	public void testBackwardsCompatibilityWithRealSettings() throws IOException {
		PortalConfig config = prepareConfig("backwardsCompatibilityWithRealSettings");

		assertThat(config)
			.as("Loaded config")
			.isNotNull();

		assertThat(config.getSecurityOptions().getAdditionalResponseHeaders())
			.as("Additional response headers")
			.contains(Pair.of("X-Additional-Header", "real-header-setting"));

		assertThat(config.getSecurityOptions().getApiKey())
			.as("Mesh API key")
			.isEqualTo("real-portal-key-setting");
	}

	@Test
	public void testLoginRedirectSimple() throws IOException {
		PortalConfig config = prepareConfig("loginRedirectSimple");
		LoginRedirectOptions loginRedirectOptions = config.getAuthOptions().getLoginRedirectOptions();

		assertThat(loginRedirectOptions.getPath())
			.as("Configured login redirect path")
			.isEqualTo("/some/custom/handler");

		assertThat(loginRedirectOptions.getDefaultRedirect())
			.as("Configured default redirect")
			.isEqualTo("/");

		assertThat(loginRedirectOptions.isCmsPage())
			.as("Is CMS page")
			.isEqualTo(false);

	}

	@Test
	public void testLoginRedirectFull() throws IOException {
		PortalConfig config = prepareConfig("loginRedirectFull");
		LoginRedirectOptions loginRedirectOptions = config.getAuthOptions().getLoginRedirectOptions();

		assertThat(loginRedirectOptions.getPath())
			.as("Configured login redirect path")
			.isEqualTo("/some/page.en.html");

		assertThat(loginRedirectOptions.getDefaultRedirect())
			.as("Configured default redirect")
			.isEqualTo("/somewhere/else");

		assertThat(loginRedirectOptions.isCmsPage())
			.as("Is CMS page")
			.isEqualTo(true);
	}

	/**
	 * Create a map with the mandatory settings for the Mesh API key, the Mesh
	 * project name, the server URL and the portal API key.
	 *
	 * @return {@code Map} containing the mandatory settings.
	 */
	private Map<String, String> createMinimalEnvMap() {
		Map<String, String> envMap = new HashMap<>();

		envMap.put(MeshOptions.MESH_API_KEY_ENV, MESH_API_KEY);
		envMap.put(PortalConfig.MESH_PROJECT_ENV, MESH_PROJECT);
		envMap.put(PortalConfig.SERVER_URL_ENV, SERVER_URL);

		return envMap;
	}

	/**
	 * Override the environment variables.
	 *
	 * @param newenv
	 * @throws Exception
	 */
	private void set(Map<String, String> newenv) throws Exception {
		Class[] classes = Collections.class.getDeclaredClasses();
		Map<String, String> env = System.getenv();
		for (Class cl : classes) {
			if ("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
				Field field = cl.getDeclaredField("m");
				field.setAccessible(true);
				Object obj = field.get(env);
				Map<String, String> map = (Map<String, String>) obj;
				map.clear();
				map.putAll(newenv);
			}
		}
	}

	/**
	 * Copy the configuration with the given base name from the resources to the
	 * portals config directory.
	 *
	 * <p>
	 *     The file <em>resources:/config/configBaseName.yml</em> is copied to
	 *     <em>config/server.yml</em>, and if it exists the file
	 *     <em>resources:/config/configBaseName.local.yml</em> is copied to
	 *     <em>config/server.local.yml</em>.
	 * </p>
	 *
	 * @param configBaseName The base name of the test configuration
	 * @return The loaded portal configuration after preparing the configuration files
	 * @throws IOException When the resources cannot be read or the configuration files cannot be written
	 */
	private PortalConfig prepareConfig(String configBaseName) throws IOException {
		InputStream in = getClass().getResourceAsStream(String.format(FMT_MAIN_CONFIG, configBaseName));
		File out = new File(MAIN_CONFIG_FILE);

		if (in == null) {
			fail("Could find test configuration: " + configBaseName);
		}

		FileUtils.copyInputStreamToFile(in, out);

		in = getClass().getResourceAsStream(String.format(FMT_LOCAL_CONFIG, configBaseName));
		out = new File(LOCAL_CONFIG_FILE);

		if (in == null) {
			// It is okay when there is no local config, we are done here.
		} else {
			FileUtils.copyInputStreamToFile(in, out);
		}

		return PortalConfigLoader.createOrLoadOptions();
	}
}
