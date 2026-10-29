package com.gentics.mesh.portal.api.config;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Options which can be configured via Environment variables via the {@link EnvironmentVariable} annotation must implement this interface.
 */
public interface OverridableOptions {

	/**
	 * Iterate over all declared fields of the class and update the value if a matching environment variable could be found.
	 */
	default void overrideWithEnv() {
		for (Field field : getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(EnvironmentVariable.class)) {
				EnvironmentVariable envInfo = field.getAnnotation(EnvironmentVariable.class);
				String name = envInfo.name();
				String envValue = System.getenv(name);

				if (envValue == null) {
					continue;
				}

				try {
					field.setAccessible(true);

					Class<?> typeClazz = field.getType();

					if ("null".equals(envValue)) {
						envValue = null;
					}

					boolean isPrimitiveType = typeClazz.equals(boolean.class)
						|| typeClazz.equals(int.class)
						|| typeClazz.equals(long.class)
						|| typeClazz.equals(float.class)
						|| typeClazz.equals(double.class);

					if (isPrimitiveType) {
						if (typeClazz.equals(boolean.class)) {
							field.setBoolean(this, Boolean.parseBoolean(envValue));
						} else if (typeClazz.equals(int.class)) {
							field.setInt(this, Integer.parseInt(envValue));
						} else if (typeClazz.equals(long.class)) {
							field.setLong(this, Long.parseLong(envValue));
						} else if (typeClazz.equals(float.class)) {
							field.setFloat(this, Float.parseFloat(envValue));
						} else if (typeClazz.equals(double.class)) {
							field.setDouble(this, Double.parseDouble(envValue));
						}

						continue;
					}

					Object value;

					if (typeClazz.equals(String.class)) {
						value = envValue;
					} else if (typeClazz.equals(URL.class)) {
						value = new URL(envValue);
					} else if (typeClazz.equals(Boolean.class)) {
						value = Boolean.valueOf(envValue);
					} else if (typeClazz.equals(Long.class)) {
						value = Long.valueOf(envValue);
					} else if (typeClazz.equals(Integer.class)) {
						value = Integer.valueOf(envValue);
					} else if (typeClazz.equals(Float.class)) {
						value = Float.valueOf(envValue);
					} else if (typeClazz.equals(Double.class)) {
						value = Double.valueOf(envValue);
					} else if (Enum.class.isAssignableFrom(typeClazz)) {
						value = Enum.valueOf((Class<? extends Enum>) typeClazz, envValue);
					} else {
						System.err.printf(
							"Unexpected field type %s for property %s, cannot convert value from environgment %s=%s\n",
							typeClazz.getCanonicalName(),
							field.getName(),
							name,
							envValue);

						value = null;
					}

					field.set(this, value);
				} catch (IllegalArgumentException | IllegalAccessException | MalformedURLException e) {
					String msg = String.format(
						"Could not set property %s of type %s with value from environment %s=%s: %s",
						field.getName(),
						field.getType().getCanonicalName(),
						name,
						envValue,
						e.getMessage());

					throw new RuntimeException(msg, e);
				}
			}
		}
	}
}
