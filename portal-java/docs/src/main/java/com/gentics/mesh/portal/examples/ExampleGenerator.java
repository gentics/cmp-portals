package com.gentics.mesh.portal.examples;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.PortalConfigLoader;
import com.gentics.mesh.portal.config.util.ConfigUtil;
import com.gentics.mesh.util.UUIDUtil;

/**
 * Create a default configuration file and move it to the output directory.
 */
public class ExampleGenerator {

	private static final String CONFIG_FILE_NAME = "server.yml";

	public static void main(String[] args) throws IOException {
		PortalConfig defaultConfig = new PortalConfig();
		defaultConfig.setPortalAPIKey(UUIDUtil.randomUUID());
		defaultConfig.getMeshOptions().setApiKey(UUIDUtil.randomUUID());
		defaultConfig.setMeshProject("demo");
		defaultConfig.setServerUrl(new URL("https://demo"));
		PortalConfigLoader.createOrLoadOptions(defaultConfig);

		File configDir = new File(ConfigUtil.CONFIG_DIR);
		File outputFolder = new File("target", args.length > 0 ? args[0] : "output/examples");

		outputFolder.mkdirs();

		Files.move(
			new File(configDir, CONFIG_FILE_NAME).toPath(),
			new File(outputFolder, CONFIG_FILE_NAME).toPath(),
			StandardCopyOption.REPLACE_EXISTING);

		configDir.delete();
	}
}
