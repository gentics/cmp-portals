package com.gentics.mesh.portal.cache;

import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.reactivex.core.Vertx;
import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.access.exception.CacheException;
import org.apache.commons.jcs.engine.behavior.ICompositeCacheAttributes;
import org.apache.commons.jcs.engine.behavior.IElementAttributes;
import org.apache.commons.jcs.engine.control.CompositeCacheManager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;

/**
 * A {@link PortalCache} implementation backed by {@link org.apache.commons.jcs.JCS}.
 *
 * @param <T> The type of cached objects
 */
public class JcsPortalCache<T> implements PortalCache<T> {

	private static final String DISABLED_MSG = "Caching is disabled";
	private static final int NO_EXPIRY = -1;

	private final CacheProvider provider;
	private final Function<RoutingContext, String> keyMapper;
	private final CacheAccess<String, T> cache;
	private Vertx rxVertx;
	private final boolean isEternal;
	private final int maxLife;
	private final int idleTime;
	private final Logger log = LoggerFactory.getLogger(JcsPortalCache.class);

	/**
	 * Create a new JCS cache.
	 *
	 * <p>
	 * Depending on the <code>expireMode</code> the <code>expireTime</code> is either passed to
	 * {@link IElementAttributes#setIdleTime(long)}, {@link IElementAttributes#setMaxLife(long)} or
	 * elements are marked as eternal with {@link IElementAttributes#setIsEternal(boolean)}.
	 * </p>
	 *
	 * @param provider The cache provider of the portal
	 * @param keyMapper The key mapping function
	 * @param maximumSize The maximum number of elements in the cache
	 * @param expireMode The expire mode
	 * @param expireTime The expire time
	 */
	JcsPortalCache(
			String configFilePath,
			CacheProvider provider,
			Function<RoutingContext, String> keyMapper,
			int maximumSize,
			ExpireMode expireMode,
			int expireTime,
			String diskCacheRegion,
			Vertx rxVertx) {
		this.provider = provider;
		this.keyMapper = keyMapper;
		this.rxVertx = rxVertx;

		this.cache = initCache(configFilePath, diskCacheRegion, maximumSize, expireTime);

		switch (expireMode) {
		case Access:
			isEternal = false;
			maxLife = NO_EXPIRY;
			idleTime = expireTime;
			break;

		case Write:
			isEternal = false;
			maxLife = expireTime;
			idleTime = expireTime;
			break;

		case None: // Fall through
		default:
			isEternal = true;
			maxLife = NO_EXPIRY;
			idleTime = NO_EXPIRY;
		}
	}

	/**
	 * Initialize the JSC cache.
	 *
	 * <p>
	 *     The basic configuration is loaded from the <em>cache.ccf</em> file, but the cache regions
	 *     <code>MaxObjects</code> and <code>MaxMemoryIdleTimeSeconds</code> will be set to
	 *     <code>maximumSize</code> and <code>expireTime</code> respectively.
	 * </p>
	 *
	 * @param configFilePath The path to the <em>cache.ccf</em> file
	 * @param region The cache region name
	 * @param maximumSize The maximum number of objects stored in the cache
	 * @param expireTime The desired expire time
	 */
	private CacheAccess<String, T> initCache(String configFilePath, String region, int maximumSize, int expireTime) {
		File jcsConfig = new File(configFilePath);

		if (!jcsConfig.exists()) {
			log.warn(
				"The configuration file <{}> could not be found, created cache will be disabled",
				jcsConfig.getAbsolutePath());

			return null;
		}

		CacheAccess<String, T> cache = null;
		Properties props = new Properties();
		CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();

		try (FileReader f = new FileReader(jcsConfig)) {
			props.load(f);
			ccm.configure(props);

			// TODO Check if this is a problem for portal multi tenancy.
			cache = JCS.getInstance(region);
		} catch (CacheException | IOException e) {
			log.error("Could not initialize JCS cache \"{}\": {}", region, e.getMessage(), e);
		}

		if (cache != null) {
			ICompositeCacheAttributes attributes = cache.getCacheAttributes();

			attributes.setMaxObjects(maximumSize);
			attributes.setMaxMemoryIdleTimeSeconds(expireTime);
		}

		return cache;
	}

	@Override
	public void clear() {
		if (cache != null) {
			cache.clear();
		}
	}

	@Override
	public void put(RoutingContext rc, T value) {
		if (cache != null && value != null) {
			IElementAttributes attr = cache.getDefaultElementAttributes();

			attr.setIsEternal(isEternal);
			attr.setMaxLife(maxLife);
			attr.setIdleTime(idleTime);

			cache.put(keyMapper.apply(rc), value, attr);
		}
	}

	@Override
	public T getIfPresent(RoutingContext rc) {
		if (!provider.enabled() || cache == null) {
			log.trace(DISABLED_MSG);

			return null;
		}

		return cache.get(keyMapper.apply(rc));
	}

	@Override
	public Single<T> get(RoutingContext rc, Function<RoutingContext, Single<T>> supplier) {
		return Single.defer(() -> {
			if (!provider.enabled() || cache == null) {
				log.trace(DISABLED_MSG);

				return supplier.apply(rc);
			}

			String key = keyMapper.apply(rc);

			return rxVertx.<T>rxExecuteBlocking(() -> cache.get(key))
				.switchIfEmpty(Single.defer(() -> supplier.apply(rc).doOnSuccess(v -> cache.put(key, v))));
		});
	}

	@Override
	public T get(String key, Function<String, T> supplier) {
		if (!provider.enabled() || cache == null) {
			log.trace(DISABLED_MSG);

			return supplier.apply(key);
		}

		T val = cache.get(key);

		if (val == null) {
			val = supplier.apply(key);
			cache.put(key, val);
		}

		return val;
	}

	@Override
	public boolean isBlockingCache() {
		return true;
	}
}
