package com.gentics.mesh.portal.log;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.slf4j.LoggerFactory;

public class LoggingConfigurator {

	private static final String DEFAULT_LOGBACK_FILE_NAME = "logback.default.xml";

	private static boolean initialized = false;

	/**
	 * Initialize the logback logger and ensure that the logback file is placed in the config folder
	 */
	public static void init() {
		if (initialized) {
			return;
		}

		File logbackFile = new File("config", "logback.xml");

		if (!logbackFile.exists()) {
			writeFile(logbackFile);
		}

		System.setProperty("logback.configurationFile", logbackFile.getAbsolutePath());
		LoggerFactory.getLogger(LoggingConfigurator.class);

		initialized = true;
	}

	/**
	 * Write the default logback file to the config folder.
	 *
	 * @param file
	 */
	public static void writeFile(File file) {
		try {
			String resourcePath = "/" + DEFAULT_LOGBACK_FILE_NAME;
			InputStream configIns = LoggingConfigurator.class.getResourceAsStream(resourcePath);

			if (configIns == null) {
				throw new Exception("Could not find default logback file {" + resourcePath + "} within classpath.");
			}

			StringWriter writer = new StringWriter();

			IOUtils.copy(configIns, writer, StandardCharsets.UTF_8);
			FileUtils.writeStringToFile(file, writer.toString(), Charset.defaultCharset());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
