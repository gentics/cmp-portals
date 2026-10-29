package com.gentics.mesh.portal.cache;

import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.portal.api.cache.CacheBuilder;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.rest.client.EventbusEvent;
import io.vertx.ext.web.RoutingContext;

/**
 * Dummy <code>CacheProvider</code> implementation for tests.
 *
 * <p>
 *     Most methods do nothing, and the {@link #cacheBuilder()} will always be a {@link DummyCacheBuilder}.
 * </p>
 */
public class DummyCacheProvider implements CacheProvider {
	@Override
	public void clearAll() {
	}

	@Override
	public void clear(String namespace) {
	}

	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public <T> CacheBuilder<T> cacheBuilder() {
		return new DummyCacheBuilder<>();
	}

	@Override
	public MeshEvent[] defaultClearEvents() {
		return new MeshEvent[0];
	}

	@Override
	public boolean defaultEventFilter(EventbusEvent event) {
		return true;
	}

	@Override
	public void handle(RoutingContext event) {
	}
}
