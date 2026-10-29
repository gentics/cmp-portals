package com.gentics.mesh.portal.config.util;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertNotNull;

import com.gentics.mesh.portal.config.PortalConfigLoaderTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ConfigUtilTest {

	public static final String CONFIG_DIR = "config";
	public static final String MAIN_CONFIG_FILE = "server.yml";
	public static final String LOCAL_CONFIG_FILE = "server.local.yml";

	@Test
	public void configShouldNotIncludeNullFieldWhenSerializing() throws Exception {
		createConfigFiles();

		Assertions.assertThatCode(() -> {
			TestConfig config = null;
			config = ConfigUtil.loadConfig(MAIN_CONFIG_FILE, config, TestConfig.class, true);
			ConfigUtil.loadConfig(LOCAL_CONFIG_FILE, config, TestConfig.class, true);

			assertNotNull(config);
		}).doesNotThrowAnyException();
	}

	private void createConfigFiles() throws IOException {
		var resource = PortalConfigLoaderTest.class.getClassLoader()
				.getResource("config/test.config.yml");

		Files.createDirectories(Path.of(CONFIG_DIR));
		Files.copy(
				Path.of(resource.getPath()),
				Path.of(CONFIG_DIR + "/" + MAIN_CONFIG_FILE), REPLACE_EXISTING);

		Files.copy(
				Path.of(resource.getPath()),
				Path.of(CONFIG_DIR + "/" + LOCAL_CONFIG_FILE), REPLACE_EXISTING);
	}

}