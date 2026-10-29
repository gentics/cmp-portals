package com.gentics.mesh.portal.util;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with {@link JsonObject JSON objects}.
 */
public class JsonUtils {

	private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
	private static final ObjectMapper jacksonMapper = new ObjectMapper();

	/**
	 * Count the number of entries in a JSON object or array.
	 *
	 * @param obj The object to count fields in
	 * @return The number of entries in the given JSON object or array
	 */
	public static int countEntries(Object obj) {
		Iterator<Object> values = null;

		if (obj instanceof JsonObject) {
			values = ((JsonObject) obj).stream().map(Entry::getValue).collect(Collectors.toList()).iterator();
		} else if (obj instanceof JsonArray) {
			values = ((JsonArray) obj).iterator();
		} else {
			return 1;
		}

		int count = 0;

		while (values.hasNext()) {
			count += countEntries(values.next());
		}

		return count;
	}

	/**
	 * Create a {@link JsonObject} from the source string even if it
	 * is actually a {@link JsonArray}.
	 *
	 * In case the input is an array, the single field <code>elements</code>
	 * will contain the actual data.
	 *
	 * @param str The JSON source string
	 * @return The corresponding JsonObject
	 * @throws DecodeException When the source string is not valid JSON
	 */
	public static JsonObject jsonObjectOf(String str) throws DecodeException {
		if (str.charAt(0) == '[') {
			return new JsonObject().put("elements", new JsonArray(str));
		}

		return new JsonObject(str);
	}

	/**
	 * Create a {@link JsonObject} from the source buffer even if it
	 * actually contains a {@link JsonArray}.
	 *
	 * In case the input is an array, the single field <code>elements</code>
	 * will contain the actual data.
	 *
	 * @param str The JSON source string
	 * @return The corresponding JsonObject
	 * @throws DecodeException When the source string is not valid JSON
	 */
	public static JsonObject jsonObjectOf(Buffer buf) throws DecodeException {
		if ("[".equals(buf.getString(0, 1))) {
			return new JsonObject().put("elements", buf.toJsonArray());
		}

		return buf.toJsonObject();
	}

	/**
	 * Convert the jackson {@link ObjectNode} to a Vert.x {@link JsonObject}
	 *
	 * <p>
	 * The input object is serialized with the {@link #jacksonMapper object mapper} and the resulting
	 * string is used to create a {@link JsonObject Vert.x JSON object}.
	 * </p>
	 *
	 * @param json The jackson JSON object
	 * @return The converted Vert.x JSON object
	 * @throws RuntimeException When the jackson object mapper could not serialize the input object
	 * @throws DecodeException When the string returned by the object mapper is not valid JSON
	 */
	public static JsonObject jsonObjectOf(ObjectNode json) throws DecodeException {
		try {
			return jsonObjectOf(jacksonMapper.writeValueAsString(json));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Get the value stored under the specified path from the JSON object.
	 *
	 * <p>
	 * No checks on the structure of the JSON object are made, when any but
	 * the last element in <code>path</code> is not a JSON object field of
	 * its parent, or if the path is <code>null</code> the
	 * <code>devaultValue</code> will be returned.
	 * </p>

	 * @param obj The JSON object to get a value from
	 * @param path The path to the value
	 * @param defaultValue The default value to return
	 *
	 * @return The value stored under the specified path in the given JSON object
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getValue(JsonObject obj, String path, T defaultValue) {
		if (obj == null) {
			log.debug("No JSON object provided");

			return defaultValue;
		}

		if (path == null || path.isEmpty()) {
			log.warn("No path provided");

			return defaultValue;
		}

		log.debug("Resolving path {} in\n{}", path, obj);

		JsonObject empty = new JsonObject();
		String[] pathElements = path.split("\\.");
		int lastIdx = pathElements.length - 1;

		for (int ii = 0; ii < lastIdx; ii++) {
			boolean error = true;

			try {
				obj = obj.getJsonObject(pathElements[ii], empty);
				error = obj == null;
			} catch (Exception e) {
				log.error("Could not get element: {}", e.getMessage());
			}

			if (error) {
				log.debug(
					"Value for field \"{}\" ({}) is not a JsonObject",
					pathElements[ii],
					ii);

				return defaultValue;
			}
		}

		try {
			Object val = obj.getValue(pathElements[lastIdx]);

			return val == null ? defaultValue : (T) val;
		} catch (ClassCastException e) {
			log.error("Unexpected field type", e);
		}

		return defaultValue;
	}

	/**
	 * Store the given value under the specified path in the JSON object
	 *
	 * <p>
	 * No checks on the structure of the JSON object are made, when any but
	 * the last element in <code>path</code> is not a JSON object field of
	 * its parent, or if the path is <code>null</code> it will result in
	 * a {@link NullPointerException}
	 * </p>
	 *
	 * <p>
	 * <strong>Note</strong> that the value will be stored using
	 * {@link JsonObject#put(String, Object)}, but this should still work for
	 * most object types.
	 * </p>
	 *
	 * @param obj The JSON object to store the value in
	 * @param path The path of field names in the JSON object
	 * @param value The value to store
	 */
	public static void putValue(JsonObject obj, String path, Object value) {
		String[] pathElements = path.split("\\.");
		int lastIdx = pathElements.length - 1;

		for (int ii = 0; ii < lastIdx; ii++) {
			obj = obj.getJsonObject(pathElements[ii]);
		}

		obj.put(pathElements[lastIdx], value);
	}
}
