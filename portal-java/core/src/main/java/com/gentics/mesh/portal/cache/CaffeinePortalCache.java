package com.gentics.mesh.portal.cache;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * A {@link PortalCache} implementation backed by {@link Caffeine}.
 *
 * @param <T> The type of cached objects
 */
public class CaffeinePortalCache<T> implements PortalCache<T> {

	private final static String DISABLED_MSG = "Caching is disabled";

	private final CacheProvider provider;
	private final Function<RoutingContext, String> keyMapper;
	private final Cache<String, T> cache;
	private final Logger log = LoggerFactory.getLogger(CaffeinePortalCache.class);

	/**
	 * Default constructor.
	 *
	 * <p>
	 * Depending on the <code>expireMode</code> the <code>expireTime</code> is either passed to
	 * {@link Caffeine#expireAfterAccess(long, TimeUnit)}, {@link Caffeine#expireAfterWrite(java.time.Duration)},
	 * or not at all.
	 * </p>
	 *
	 * @param provider The cache provider of the portal
	 * @param keyMapper The key mapping function
	 * @param maximumSize The maximum number of elements in the cache
	 * @param expireMode The expire mode
	 * @param expireTime The expire time
	 */
	CaffeinePortalCache(
			CacheProvider provider,
			Function<RoutingContext, String> keyMapper,
			int maximumSize,
			ExpireMode expireMode,
			int expireTime) {
		this.provider = provider;
		this.keyMapper = keyMapper;

		Caffeine<Object, Object> builder = Caffeine.newBuilder()
			.maximumSize(maximumSize);

		switch (expireMode) {
			case Access:
				builder.expireAfterAccess(expireTime, TimeUnit.SECONDS);
				break;

			case Write:
				builder.expireAfterWrite(expireTime, TimeUnit.SECONDS);
				break;

			case None:
				// Fall though

			default:
		}

		this.cache = builder.build();
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.api.cache.PortalCache#clear()
	 */
	@Override
	public void clear() {
		cache.invalidateAll();
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.api.cache.PortalCache#put(io.vertx.ext.web.RoutingContext, java.lang.Object)
	 */
	@Override
	public void put(RoutingContext rc, T value) {
		cache.put(keyMapper.apply(rc), value);
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.api.cache.PortalCache#getIfPresent(io.vertx.ext.web.RoutingContext)
	 */
	@Override
	public T getIfPresent(RoutingContext rc) {
		if (!provider.enabled()) {
			log.trace(DISABLED_MSG);

			return null;
		}

		return cache.getIfPresent(keyMapper.apply(rc));
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.api.cache.PortalCache#get(io.vertx.ext.web.RoutingContext, java.util.function.Function)
	 */
	@Override
	public Single<T> get(RoutingContext rc, Function<RoutingContext, Single<T>> supplier) {
		return Single.defer(() -> {
			if (!provider.enabled()) {
				log.trace(DISABLED_MSG);

				return supplier.apply(rc);
			}

			String key = keyMapper.apply(rc);
			T val = cache.getIfPresent(key);

			if (val == null) {
				return supplier.apply(rc).doOnSuccess(v -> cache.put(key, v));
			}

			return Single.just(val);
		});
	}

	/*
	 * (non-Javadoc)
	 * @see com.gentics.mesh.portal.api.cache.PortalCache#get(java.lang.String, java.util.function.Function)
	 */
	@Override
	public T get(String key, Function<String, T> supplier) {
		if (!provider.enabled()) {
			log.trace(DISABLED_MSG);

			return supplier.apply(key);
		}

		T val = cache.getIfPresent(key);

		if (val == null) {
			val = supplier.apply(key);
			cache.put(key, val);
		}

		return val;
	}

	@Override
	public boolean isBlockingCache() {
		return false;
	}
}
