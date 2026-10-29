package com.gentics.mesh.portal;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.gentics.mesh.portal.server.dagger.BaseComponent;

public class Portal {

	static AtomicReference<BaseComponent> applicationComponent = new AtomicReference<>(null);

	/**
	 * Creates a new portal dagger context or returns a previously created one.
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T extends BaseComponent> T create(Supplier<T> contextProvider) {
		if (applicationComponent.get() == null) {
			applicationComponent.set(contextProvider.get());
		}
		return (T) applicationComponent.get();
	}

	/**
	 * Clear the reference to the dagger mesh component. This is useful if you want to start a new dagger context (e.g. for unit tests).
	 */
	public static void clear() {
		applicationComponent.set(null);
	}

	/**
	 * Return the created context.
	 * 
	 * @return Created dagger context or null if none has been created previously
	 */
	@SuppressWarnings("unchecked")
	public static <T extends BaseComponent> T get() {
		return (T) applicationComponent.get();
	}

	/**
	 * Set the component (useful for testing)
	 * 
	 * @param component
	 */
	public static <T extends BaseComponent> void set(T component) {
		applicationComponent.set(component);
	}

}
