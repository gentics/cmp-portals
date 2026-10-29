package com.gentics.mesh.portal.cache;

import com.gentics.mesh.portal.api.cache.PortalCache;
import io.reactivex.Single;
import io.vertx.ext.web.RoutingContext;

import java.util.function.Function;

/**
 * Dummy <code>PortalCache</code> implementation for tests which does nothing.
 *
 * <p>
 *     The {@link #get(RoutingContext, Function)} and {@link #get(String, Function)} methods will always just invoke
 *     the <code>supplier</code> and return the result.
 * </p>
 * @param <T> The type of the cached objects
 */
public class DummyCache<T> implements PortalCache<T> {
	@Override
	public void clear() {
	}

	@Override
	public void put(RoutingContext rc, T value) {
	}

	@Override
	public T getIfPresent(RoutingContext rc) {
		return null;
	}

	@Override
	public Single<T> get(RoutingContext rc, Function<RoutingContext, Single<T>> supplier) {
		return supplier.apply(rc);
	}

	@Override
	public T get(String key, Function<String, T> supplier) {
		return supplier.apply(key);
	}

	@Override
	public boolean isBlockingCache() {
		return false;
	}
}
