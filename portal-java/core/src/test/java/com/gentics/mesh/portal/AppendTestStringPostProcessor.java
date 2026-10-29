package com.gentics.mesh.portal;

import java.util.Map;

import io.vertx.core.json.JsonObject;

public class AppendTestStringPostProcessor implements DataProviderPostProcessor {

	public static String DATA_FIELD = "data";
	public static String ADDED_STRING = "FOO";

	@Override
	public JsonObject process(Object data) {
		if (data == null) {
			data = "";
		}

		return new JsonObject().put(DATA_FIELD, data + ADDED_STRING);
	}

	@Override
	public void init(Map<String, String> options) {
	}
}
