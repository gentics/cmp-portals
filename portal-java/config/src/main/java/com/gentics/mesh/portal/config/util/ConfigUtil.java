package com.gentics.mesh.portal.config.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.config.PortalConfigLoader;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public final class ConfigUtil {

	private static final Logger log = LoggerFactory.getLogger(PortalConfigLoader.class);

	public static final String CONFIG_DIR = "config";

	public static <T extends Config> T loadConfig(String filename, T config, Class<T> clazzOfT, boolean createFileIfMissing) {
		T fullConfig = null;
		InputStream ins = loadFile(filename);

		if (ins == null) {
			log.info("Loading default configuration.");

			File confFile = new File(CONFIG_DIR, filename);
			try {
				// Generate default config
				if (config != null) {
					fullConfig = config;
				} else {
					fullConfig = clazzOfT.newInstance();
				}
				if (createFileIfMissing) {
					FileUtils.writeStringToFile(confFile, getYAMLMapper().writeValueAsString(fullConfig), Charset.defaultCharset(), false);
					log.info("Saved default configuration to file \"{}\".", confFile.getAbsolutePath());
				}
			} catch (InstantiationException | IOException | IllegalAccessException e) {
				log.warn("Error while saving default configuration to file \"{}\".", confFile.getAbsolutePath(), e);
			}
		} else {
			fullConfig = loadConfig(ins, config, clazzOfT);
			if (fullConfig == null) {
				throw new RuntimeException("Could not read configuration file.");
			}
		}

		return fullConfig;
	}

	/**
	 * Return the preconfigured object mapper which is used to transform YAML documents.
	 *
	 * @return
	 */
	public static ObjectMapper getYAMLMapper() {
		YAMLFactory factory = new YAMLFactory();
		ObjectMapper mapper = new ObjectMapper(factory);
		mapper.setSerializationInclusion(Include.NON_NULL);
		mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
		return mapper;
	}

	/**
	 * Creates and returns an {@link ObjectMapper} preconfigured object mapper to transform Json
	 * documents.
	 *
	 * @return a configured {@link ObjectMapper} instance
	 */
	public static ObjectMapper getJsonMapper() {
		ObjectMapper jsonMapper = new ObjectMapper();
		jsonMapper.setSerializationInclusion(Include.NON_NULL);
		return jsonMapper;
	}

		/**
		 * Load the configuration from the stream.
		 *
		 * @param ins
		 * @param config
		 * @param clazzOfT
		 * @return
		 */
	private static <T extends Config> T loadConfig(InputStream ins, T config, Class<T> clazzOfT) {
		if (ins == null) {
			log.info("Config file not found. Using default configuration.");
			if (config == null) {
				try {
					return clazzOfT.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					log.error("FATAL: Could not instantiate configuration from class \"{}\"", clazzOfT, e);
					throw new RuntimeException("Error while loading config", e);
				}
			} else {
				return config;
			}
		}

		ObjectMapper mapper = getYAMLMapper();

		try {
			if (config == null) {
				return mapper.readValue(ins, clazzOfT);
			} else {
				String serializedJsonConfig = getJsonMapper().writeValueAsString(config);
				JsonObject baseConfigJson = new JsonObject(serializedJsonConfig);

				Object newConfig = mapper.readValue(ins, Object.class);
				JsonObject newConfigJson = new JsonObject(new ObjectMapper().writeValueAsString(newConfig));
				JsonObject mergedJson = baseConfigJson.mergeIn(newConfigJson, true);

				log.debug("Merging configurations");
				if (log.isTraceEnabled()) {
					log.trace("Base config:\n{}", baseConfigJson.encodePrettily());
					log.trace("New config:\n{}", newConfigJson.encodePrettily());
					log.trace("Merged config:\n{}", mergedJson.encodePrettily());
				}

				return mapper.readValue(mergedJson.encode(), clazzOfT);
			}
		} catch (Exception e) {
			log.error("FATAL: Could not parse configuration.", e);
			throw new RuntimeException("Could not parse options file", e);
		}
	}

	public static JsonObject loadJson(String filename) {
		InputStream ins = loadFile(filename);

		if (ins == null) {
			log.info("Configuration file \"{}\" not found", filename);
			return new JsonObject();
		}

		try {
			return new JsonObject(IOUtils.toString(ins, Charset.defaultCharset()));
		} catch (IOException e) {
			log.error("Could not load \"{}\" configuration", filename, e);
			return new JsonObject();
		}
	}

	private static InputStream loadFile(String filename) {
		InputStream inputStream = null;
		File configFile = new File(CONFIG_DIR, filename);

		// Try to use the server local file first. This is useful for loading dev configuration
		if (configFile.canRead()) {
			if (log.isInfoEnabled()) {
				log.info("Loading configuration file \"{}\".", configFile);
			}

			try {
				inputStream = new FileInputStream(configFile);
			} catch (FileNotFoundException e) {
				log.error("Could not load configuration file \"{}\".", configFile.getAbsolutePath(), e);
			}
		} else {
			inputStream = PortalConfigLoader.class.getResourceAsStream("/" + filename);

			log.debug("Configuration file \"config/{}\" was not found within filesystem.", filename);

			if (inputStream == null) {
				log.debug("Configuration file \"{}\" was not found within classpath.", filename);
			} else {
				log.info("Loading configuration file \"{}\" from classpath.", filename);
			}
		}

		return inputStream;
	}

}
