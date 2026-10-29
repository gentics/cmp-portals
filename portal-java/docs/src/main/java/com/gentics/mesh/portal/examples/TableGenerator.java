package com.gentics.mesh.portal.examples;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.reflections.Reflections;

import com.gentics.mesh.doc.GenerateDocumentation;

/**
 * Generator tool which extracts the schema field description from all models. The extracted information will be converted in tabular form to be included in the
 * documentation. The generator will also generate tables for query parameter information.
 */
public class TableGenerator extends AbstractRenderingGenerator {

	public TableGenerator(File outputFolder) throws IOException {
		super(new File(outputFolder, "tables"), false);
	}

	public void run() throws Exception {
		for (Class<?> clazz : allFoundClassesAnnotatedWithEntityToBeScanned()) {
			// Don't handle abstract classes
			if (clazz.getSimpleName().startsWith("Abstract")) {
				continue;
			}
		}

	}

	/**
	 * Locate all rest model classes.
	 * 
	 * @return
	 */
	public static Set<Class<?>> allFoundClassesAnnotatedWithEntityToBeScanned() {
		Reflections reflections = new Reflections("com.gentics.mesh.*");
		Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(GenerateDocumentation.class);
		return annotated;
	}
}
