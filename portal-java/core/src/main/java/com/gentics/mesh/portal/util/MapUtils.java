package com.gentics.mesh.portal.util;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Static utility functions with maps.
 */
public final class MapUtils {
	private MapUtils() {
	}

	public static <K, V> Map<K, V> mapOf(K key, V value) {
		return mapOf(
			(K[]) new Object[]{key},
			(V[]) new Object[]{value}
		);
	}

	public static <K, V> Map<K, V> mapOf(K[] keys, V[] values) {
		Objects.requireNonNull(keys);
		Objects.requireNonNull(values);
		if (keys.length != values.length) {
			throw new InvalidParameterException("Arrays must be the same length");
		}

		HashMap<K, V> map = new HashMap<>(keys.length);
		for (int i = 0; i < keys.length; i++) {
			map.put(keys[i], values[i]);
		}

		return map;
	}
}
