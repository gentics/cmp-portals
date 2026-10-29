package com.gentics.mesh.portal.util;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamUtil {
	private StreamUtil() {
	}

	public static <T> Stream<T> fromIterable(Iterable<T> iterable) {
		return StreamSupport.stream(iterable.spliterator(), false);
	}
}
