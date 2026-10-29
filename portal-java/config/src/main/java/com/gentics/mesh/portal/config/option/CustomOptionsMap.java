package com.gentics.mesh.portal.config.option;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.gentics.mesh.portal.api.config.Config;

/**
 * Allows to configure custom POJOs in server.yml.
 *
 * <p>
 * All Custom OptionClass Objects are stored in a Map (<code>configMap</code>).
 * You need to specify the class for each configMap entry - see example below
 * </p>
 *
 * <pre>
 * customConfigMap:
 *   configMap:
 *     key1:  !&lt;com.gentics.mesh.portal.config.MyOptionsClass&gt;
 *       attr1: "value1"
 * </pre>
 *
 * <p>
 *     With this config configMap now holds an config entry "key1" of type "MyOptionsClass"
 *     where "attr1" is filled with "value1".
 * </p>
 *
 * <p>
 *     <strong>IMPORTANT:</strong> Using this setting is <em>not</em> recommended.
 *     To avoid polluting the server configuration with project specific settings, it is
 *     recommended to load custom settings with
 *     {@link com.gentics.mesh.portal.config.util.ConfigUtil#loadConfig(String, Config, Class) loadConfig()}, or
 *     {@link com.gentics.mesh.portal.config.util.ConfigUtil#loadJson(String) loadJson()}.
 * </p>
 */
public class CustomOptionsMap {
	@JsonProperty
	@JsonPropertyDescription("Map of custom configuration options.")
	@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
	private Map<String, Object> configMap = new HashMap<>();

	public Map<String, Object> getConfigMap() {
		return configMap;
	}

	public void setConfigMap(Map<String, Object> configMap) {
		this.configMap = configMap;
	}

	@SuppressWarnings("unchecked")
	public <T> T getCustomConfig(String key) {
		return (T) configMap.get(key);
	}
}
