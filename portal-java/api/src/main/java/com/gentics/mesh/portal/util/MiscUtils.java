package com.gentics.mesh.portal.util;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class MiscUtils {
	private MiscUtils() {
	}

	/**
	 * More lenient version of an enums <code>valueOf()</code> method.
	 *
	 * This method will search the values of the specified enum for one equal to <code>name</code>, but in contrast to <code>valueOf()</code> ignores the case.
	 *
	 * @param enumValues
	 *            All possible values of the enum. Use <code>YourEnum.values()</code> for this.
	 * @param name
	 *            The case independent name of an value of <code>enumType</code>
	 *
	 * @return An optional value of the specified enum type with the specified name (ignoring case), or an empty optional if no such value exists.
	 */
	public static <T extends Enum<T>> Optional<T> getEnumValue(T[] enumValues, String name) {
		return Stream.of(enumValues)
			.filter(ev -> ev.name().equalsIgnoreCase(name))
			.findAny();
	}

	/**
	 * Check whether the given {@code Throwable} itself or one of its causes is
	 * a {@link SocketTimeoutException}.
	 *
	 * @param e The {@code Throwable} to check
	 * @return {@code true} when the {@code Throwable} or one of its causes is
	 * 		a {@code SocketTimeoutException}, and {@code false} otherwise
	 */
	public static boolean isSocketError(Throwable e) {
		while (e != null) {
			if (e instanceof SocketTimeoutException || e instanceof SocketException) {
				return true;
			}

			e = e.getCause();
		}

		return false;
	}

	/**
	 * Lazily evaluate a string supplier.
	 *
	 * <p>
	 *     This method is intended for expensive methods passed to logging
	 *     calls like
	 *     {@code log.debug("Some JSON: {}", MiscUtils.lazyString(() -> json.encodePrettily()))}
	 * </p>
	 *
	 * @param supplier The string supplier to lazily evaluate.
	 * @return A string supplier whith a {@code toString()} method that calls
	 * 		the suppliers {@code get()} method.
	 */
	public static <T> Supplier<T> lazyString(Supplier<T> supplier) {
		return new Supplier<T>() {
			@Override
			public T get() {
				return supplier.get();
			}

			@Override
			public String toString() {
				return Objects.toString(get());
			}
		};
	}
}
