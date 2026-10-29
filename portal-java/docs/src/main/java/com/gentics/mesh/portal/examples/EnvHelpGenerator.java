package com.gentics.mesh.portal.examples;

import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.google.common.base.CaseFormat;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate AsciiDoc documentation for environment variables.
 */
public class EnvHelpGenerator extends AbstractRenderingGenerator {

	private static final String ENV_TABLE_TEMPLATE_NAME = "env-table.hbs";
	private static final String OUTPUT_FILENAME = "portal-env.adoc-include";

	private EnvHelpGenerator(File outputFolder) throws IOException {
		super(outputFolder, false);
	}

	public static void main(String[] args) throws IOException {
		String targetDir = args.length > 0 ? args[0] : "output/environment";

		new EnvHelpGenerator(new File("target", targetDir)).run();
	}

	public void run() throws IOException {
		System.out.println("Writing files to  {" + outputFolder.getAbsolutePath() + "}");

		List<Map<String, String>> list = new ArrayList<>();
		Reflections reflections = new Reflections("com.gentics.mesh.portal", new FieldAnnotationsScanner());
		for (Field field : reflections.getFieldsAnnotatedWith(EnvironmentVariable.class)) {
			if (field.isAnnotationPresent(EnvironmentVariable.class)) {
				EnvironmentVariable envInfo = field.getAnnotation(EnvironmentVariable.class);
				String name = envInfo.name();
				String description = envInfo.description();
				Map<String, String> map = new HashMap<>();

				map.put("name", name);
				map.put("description", description);
				map.put("field", generateLink(field));

				list.add(map);
			}
		}

		list.sort(Comparator.comparing(m -> m.get("name")));

		Map<String, Object> context = new HashMap<>();
		context.put("entries", list);
		String table = renderTable(context, getTemplate(ENV_TABLE_TEMPLATE_NAME));
		writeFile(OUTPUT_FILENAME, table);
	}

	private String generateLink(Field field) {
		return String.format(
			"link:#%s[%s]",
			CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, field.getDeclaringClass().getSimpleName()),
			field.getName());
	}
}
