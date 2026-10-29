package com.gentics.mesh.portal.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.util.ConfigUtil;

/**
 * {@link ResourceBundle} implementation that reads translations from YAML files located in configured i18n path (see {@link PortalConfig#getI18nPath()}).
 */
public class YamlResourceBundle extends ResourceBundle {
	private Map<String, Object> lookup;

	@SuppressWarnings("unchecked")
	YamlResourceBundle(InputStream stream) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper yamlMapper = ConfigUtil.getYAMLMapper();
		lookup = yamlMapper.readValue(stream, Map.class);
	}

	@Override
	protected Object handleGetObject(String key) {
		Object value = lookup.get(key);
		if (value != null) {
			return value.toString();
		} else {
			return null;
		}
	}

	@Override
	public Enumeration<String> getKeys() {
		Iterator<String> keyIterator = lookup.keySet().iterator();
		Enumeration<String> parentKeys = parent != null ? parent.getKeys() : null;

		return new Enumeration<String>() {
			@Override
			public boolean hasMoreElements() {
				if (keyIterator.hasNext()) {
					return true;
				} else if (parentKeys != null) {
					return parentKeys.hasMoreElements();
				} else {
					return false;
				}
			}

			@Override
			public String nextElement() {
				if (keyIterator.hasNext()) {
					return keyIterator.next();
				} else if (parentKeys != null) {
					return parentKeys.nextElement();
				} else {
					throw new NoSuchElementException();
				}
			}
		};
	}
}