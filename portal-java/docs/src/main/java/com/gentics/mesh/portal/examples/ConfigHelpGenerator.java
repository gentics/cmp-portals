package com.gentics.mesh.portal.examples;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.config.PortalConfig;
import com.google.common.base.CaseFormat;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Generator for configuration documentation
 */
public class ConfigHelpGenerator extends AbstractRenderingGenerator {
	private static final String MANDATORY_VALUE = "*Mandatory*";
	private static final String CONFIG_TABLE_TEMPLATE_NAME = "config-table.hbs";

	private ConfigHelpGenerator(File outputFolder) throws IOException {
		super(outputFolder, true);
	}

	public static void main(String[] args) throws IOException {
		String targetDir = args.length > 0 ? args[0] : "output/config";

		new ConfigHelpGenerator(new File("target", targetDir)).run();
	}

	public void run() throws IOException {
		System.out.println("Writing files to  {" + outputFolder.getAbsolutePath() + "}");

		Map<String, List<Map<String, String>>> classLists = new HashMap<>();

		generate(PortalConfig.class, classLists, null);

		for (Entry<String, List<Map<String, String>>> entry : classLists.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			String fileName = entry.getKey();
			List<Map<String, String>> list = entry.getValue();

			Map<String, Object> context = new HashMap<>();
			context.put("anchor", CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, fileName));
			context.put("entries", list);
			String table = renderTable(context, getTemplate(CONFIG_TABLE_TEMPLATE_NAME));
			writeFile(fileName + ".adoc-include", table);
		}
	}

	/**
	 * Recursively generate documentation for the configuration, starting with the given class.
	 * @param clazz class to start with
	 * @param classLists map of documentation entries (keys are the filenames, one for each class)
	 * @param prefix current prefix (null for no prefix)
	 * @return true if at least one annotated field was found
	 */
	protected boolean generate(Class<?> clazz, Map<String, List<Map<String, String>>> classLists, String prefix) {
		String fileName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clazz.getSimpleName());

		if (classLists.containsKey(fileName)) {
			return classLists.get(fileName) != null;
		}

		List<Map<String, String>> list = new ArrayList<>();

		for (Field field : clazz.getDeclaredFields()) {
			if (field.isAnnotationPresent(JsonProperty.class) && !field.isAnnotationPresent(Deprecated.class)) {
				JsonProperty propertyInfo = field.getAnnotation(JsonProperty.class);
				Map<String, String> map = new HashMap<>();
				String fieldName = StringUtils.defaultIfEmpty(propertyInfo.value(), field.getName());
				if (prefix != null) {
					fieldName = String.format("%s.%s", prefix, fieldName);
				}
				map.put("anchor", CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clazz.getSimpleName()));
				map.put("name", fieldName);
				map.put("default", propertyInfo.required() ? "*Mandatory*" : propertyInfo.defaultValue());
				if (field.isAnnotationPresent(JsonPropertyDescription.class)) {
					map.put("description", field.getAnnotation(JsonPropertyDescription.class).value());
				}
				map.put("type", renderType(field.getGenericType(), classLists, fieldName));

				list.add(map);
			}
		}

		if (!list.isEmpty()) {
			if (prefix == null) {
				// Just keep the generic options for the main configuration class.
				list.removeIf(e -> e.get("type").startsWith("link:"));
			}

			list.sort((e1, e2) -> {
				boolean e1Mandatory = MANDATORY_VALUE.equals(e1.get("default"));
				boolean e2Mandatory = MANDATORY_VALUE.equals(e2.get("default"));

				// When one of them is mandatory, it should come first ...
				if (e1Mandatory ^ e2Mandatory) {
					return e1Mandatory ? -1 : 1;
				}

				// ... otherwise just compare the names.
				return e1.get("name").compareTo(e2.get("name"));
			});
			classLists.put(fileName, list);

			return true;
		} else {
			classLists.put(fileName, null);

			return false;
		}
	}

	/**
	 * Recursively render the human readable, optionally linked type
	 * @param type start type
	 * @param classLists list of classes
	 * @param fieldName field name
	 * @return rendered type
	 */
	private String renderType(Type type, Map<String, List<Map<String, String>>> classLists, String fieldName) {
		if (type instanceof ParameterizedType) {
			ParameterizedType paramType = (ParameterizedType)type;
			String baseType = renderType(paramType.getRawType(), classLists, fieldName);
			String subTypes = Arrays.stream(paramType.getActualTypeArguments())
				.map(subType -> renderType(subType, classLists, fieldName))
				.collect(Collectors.joining(", "));

			return  baseType + "<" + subTypes + ">";
		} else {
			Class<?> clazz = (Class<?>)type;
			if (clazz.isArray()) {
				String link = generateLink(clazz.getComponentType(), classLists, fieldName);
				return String.format("List<%s>", getHumanReadableLinkedType(clazz.getComponentType(), link));
			} else {
				String link = generateLink(clazz, classLists, fieldName);
				return getHumanReadableLinkedType(clazz, link);
			}
		}
	}

	/**
	 * Generate link to embedded option classes (if annotations found)
	 * @param typeClazz embedded type class
	 * @param classLists class lists object
	 * @param fieldName field name
	 * @return link, if embedded option classes file was generated, null if not
	 */
	private String generateLink(Class<?> typeClazz, Map<String, List<Map<String, String>>> classLists, String fieldName) {
		if (generate(typeClazz, classLists, fieldName)) {
			return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, typeClazz.getSimpleName());
		} else {
			return null;
		}
	}

	/**
	 * Generate human readable type (optionally linked)
	 * @param clazz type class
	 * @param link optional link
	 * @return human readable type
	 */
	private String getHumanReadableLinkedType(Class<?> clazz, String link) {
		String label;

		if (clazz.isEnum()) {
			label = StringUtils.join(clazz.getEnumConstants(), ", ");
		} else {
			label = clazz.getSimpleName();
		}

		if (link != null) {
			return String.format("link:#%s[%s]", link, label);
		} else {
			return label;
		}
	}
}
