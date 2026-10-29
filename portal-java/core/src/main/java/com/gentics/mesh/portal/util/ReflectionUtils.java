package com.gentics.mesh.portal.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionUtils {

	private static final Logger log = LoggerFactory.getLogger(ReflectionUtils.class);

	private static Class<?> getTargetClass(String className, Class<?> expectedBase) {
		if (className == null || className.isEmpty()) {
			log.error("No class name specified");

			return null;
		}

		if (expectedBase == null) {
			log.error("No expected base class specified");

			return null;
		}

		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			log.error("Invalid class name: {}", className, e);
		}

		return null;
	}

	/**
	 * Get an instance of the class with the given classname.
	 *
	 * This method makes sure the created object is assignable to the <code>expectedBase</code> class.
	 *
	 * If anything goes wrong the errors will be logged, and the return value will be null, but no exceptions will be thrown.
	 *
	 * @param className
	 *            The class to create an instance of
	 * @param expectedBase
	 *            The expected super class of the created instance
	 * @param desc
	 *            A description of the class used for error message
	 *
	 * @return An instance of the specified class
	 */
	@SuppressWarnings("unchecked")
	public static <E> E getInstance(String className, Class<E> expectedBase, String desc) {
		Class<?> targetClass = null;

		if (desc == null || desc.isEmpty()) {
			desc = "(no description)";
		}

		targetClass = getTargetClass(className, expectedBase);

		if (targetClass == null) {
			log.error("Invalid {} specified", desc);

			return null;
		}

		if (!expectedBase.isAssignableFrom(targetClass)) {
			log.error("Specified {} is not an implementation of {}", desc, expectedBase.getSimpleName());

			return null;
		}

		Constructor<E> ctor = null;

		try {
			ctor = ((Class<E>) targetClass).getConstructor();
		} catch (NoSuchMethodException e) {
			log.error("The {} class {} does not have a default constructor", desc, className, e);
		} catch (SecurityException e) {
			log.error("Could not instantiate {} class", desc, e);
		}

		if (ctor == null) {
			log.error("No valid constructor found for {}", desc);
		} else {
			try {
				return ctor.newInstance();
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				log.error("Could not instantiate {} class", desc, e);
			}
		}

		return null;
	}

	/**
	 * Tests if <code>obj</code> is an instance of an actual subclass of <code>clazz</code>.
	 *
	 * When <code>obj</code> is <code>null</code> or an instance of <code>clazz</code> this test
	 * will return <code>false</code>.
	 *
	 * @param <U> The class to check against
	 * @param <V> A possible subclass of <code>clazz</code>
	 * @param obj The object to check
	 * @param clazz The class to check against
	 * @return <code>true</code> when <code>obj</code> is an instance of an actual subclass of <code>clazz</code>, and
	 * 		<code>false</code> when <code>obj</code> is <code>null</code> or an instance of <code>clazz</code>
	 */
	public static <U, V extends U> boolean isSubclassInstance(V obj, Class<U> clazz) {
		return obj == null ? false : !obj.getClass().equals(clazz);
	}
}
