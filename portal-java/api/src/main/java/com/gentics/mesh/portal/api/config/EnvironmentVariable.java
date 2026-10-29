package com.gentics.mesh.portal.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which can be used in conjunction with {@link OverridableOptions} in order to allow environment variable handling for the annotated field.
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface EnvironmentVariable {

	/**
	 * Description of the variable.
	 *
	 * @return
	 */
	String description();

	/**
	 * Name of the variable.
	 *
	 * @return
	 */
	String name();
}
