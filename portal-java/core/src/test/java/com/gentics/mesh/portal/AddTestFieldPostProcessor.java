package com.gentics.mesh.portal;
import java.util.Map;

import io.vertx.core.json.JsonObject;

public class AddTestFieldPostProcessor implements DataProviderPostProcessor {

	public static String FIELD = "foo";
	public static String VALUE = "bar";

	@Override
	public JsonObject process(Object data) {
		if (data instanceof JsonObject) {
			JsonObject ret = (JsonObject) data;

			ret.put(FIELD, VALUE);

			return ret;
		}

		return null;
	}

	@Override
	public void init(Map<String, String> options) {
	}
}
