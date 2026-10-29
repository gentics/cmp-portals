package com.gentics.mesh.portal.custom;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gentics.mesh.portal.content.Redirect;
import com.gentics.mesh.portal.content.RedirectHelper;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RedirectHelperImpl implements RedirectHelper {

	private static final Logger log = LoggerFactory.getLogger(RedirectHelperImpl.class);

	private String fieldName = "addredirecttag";

	@Override
	public void init(Map<String, String> config) {
		if (config.containsKey("fieldName")) {
			fieldName = config.get("fieldName");
		}
	}

	@Override
	public Map<String, String> convertData(JsonObject data) {
		Map<String, String> redirectsMap = new HashMap<>();
		String rawData = data.getJsonObject("fields", new JsonObject()).getString(fieldName);
		try {
			List<Redirect> redirects = new ObjectMapper().readValue(rawData, new TypeReference<List<Redirect>>() {
			});
			redirectsMap = redirects.stream().collect(Collectors.toMap(Redirect::getOldUrl, Redirect::getNewUrl, (o1, o2) -> o1));
		} catch (IOException e) {
			log.error("Exception in gathering redirects data: {}", e.getMessage(), e);
		}
		return redirectsMap;
	}
}
