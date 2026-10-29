package com.gentics.mesh.portal.cache;

import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.portal.api.cache.CacheBuilder;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.rest.client.EventbusEvent;
import io.vertx.ext.web.RoutingContext;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dummy <code>CacheBuilder</code> implementation for tests.
 *
 * <p>
 *     All settings will be ignored, and {@link #build()} always returns a {@link DummyCache} which does nothing.
 * </p>
 * @param <T> The type of cached objects
 */
public class DummyCacheBuilder<T> implements CacheBuilder<T> {

	@Override
	public CacheBuilder<T> namespace(String namespace) {
        return this;
	}

	@Override
	public CacheBuilder<T> keyMapper(Function<RoutingContext, String> keyMapper) {
		return this;
	}

	@Override
	public CacheBuilder<T> maximumSize(int maximumSize) {
		return this;
	}

	@Override
	public CacheBuilder<T> expireMode(ExpireMode expireMode) {
		return this;
	}

	@Override
	public CacheBuilder<T> expireTime(int expireTime) {
		return this;
	}

	@Override
	public CacheBuilder<T> clearEvents(MeshEvent... clearEvents) {
		return this;
	}

	@Override
	public CacheBuilder<T> eventFilter(Predicate<EventbusEvent> eventFilter) {
		return this;
	}

	@Override
	public CacheBuilder<T> useCustomKeyParams() {
		return this;
	}

	@Override
	public CacheBuilder<T> diskCacheRegion(String region) {
		return this;
	}

	@Override
	public PortalCache<T> build() {
		return new DummyCache<>();
	}
}
