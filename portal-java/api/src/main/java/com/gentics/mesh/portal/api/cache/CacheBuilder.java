package com.gentics.mesh.portal.api.cache;

import java.util.function.Function;
import java.util.function.Predicate;

import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.rest.client.EventbusEvent;
import com.gentics.mesh.rest.client.MeshWebsocket;

import io.vertx.ext.web.RoutingContext;

/**
 * A helper to create a new {@link PortalCache}.
 *
 * <p>
 * Current implementations create only in-memory <code>Caffeine</code> caches.
 * </p>
 *
 * @param <T> Type of the objects stored in the cache
 */
public interface CacheBuilder<T> {

	/**
	 * Set the namespace for the cache.
	 *
	 * <p>
	 * When no namespace is set, the default namespace defined in the implementation of the {@link CacheProvider}
	 * is used.
	 * </p>
	 *
	 * @param namespace The namespace for the cache
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> namespace(String namespace);

	/**
	 * Set the key mapper.
	 *
	 * <p>
	 * By default the cache key should be created from the current {@link RoutingContext}, so that caches are
	 * aware of user permissions. When this is not desired a custom key mapper can be provided with this method.
	 * </p>
	 *
	 * <p>
	 * When no key mapper is specified, a default mapper from the {@link CacheProvider} implementation is used.
	 * </p>
	 *
	 * @param keyMapper The key mapper
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> keyMapper(Function<RoutingContext, String> keyMapper);

	/**
	 * Set the maximum number of elements stored in the cache.
	 *
	 * @param maximumSize The maximum number of elements to store in the cache
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> maximumSize(int maximumSize);

	/**
	 * Set the expire mode for the cache.
	 *
	 * <p>
	 * When no expire mode is specified, the default mode is {@link ExpireMode#Access}.
	 * </p>
	 *
	 * @param expireMode The desired expire mode
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> expireMode(ExpireMode expireMode);

	/**
	 * Set the expire time for cache entries.
	 *
	 * <p>
	 * Cache entries will expire when they are not accessed for the specified number of seconds.
	 * </p>
	 *
	 * <p>
	 * When no expire time is specified, it is read from the portal configuration.
	 * </p>
	 *
	 * @param expireTime The expire time in seconds
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> expireTime(int expireTime);

	/**
	 * Which {@link MeshEvent MeshEvents} should cause the cache to be cleared.
	 *
	 * <p>
	 * If any events are specified the cache will be cleared, when one of these events is received or when the
	 * {@link MeshWebsocket eventbus} reconnects.
	 * </p>
	 *
	 * <p>
	 * When an {@link #eventFilter(Predicate) eventFilter} is also specified, the cache will only be cleared,
	 * if the received event matches the filter.
	 * </p>
	 *
	 * @see #eventFilter(Predicate)
	 * @param clearEvents The events which should cause a cache clear
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> clearEvents(MeshEvent... clearEvents);

	/**
	 * Define a {@link Predicate} to decide whether a received {@link #clearEvents(MeshEvent...) event} should
	 * really clear the cache.
	 *
	 * @see #clearEvents(MeshEvent...)
	 * @param eventFilter The <code>Predicate</code> to filter cache clear events
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> eventFilter(Predicate<EventbusEvent> eventFilter);

	/**
	 * Whether to use custom parameters from the routing context in the cache key generation.
	 *
	 * <p>
	 * The custom parameter names can be configured in <code>caching.customParameters</code>
	 * </p>
	 *
	 * @return A reference to <code>this</code> for fluent API use
	 */
	CacheBuilder<T> useCustomKeyParams();

	CacheBuilder<T> diskCacheRegion(String region);

	/**
	 * Return a {@link PortalCache} instance with the current configuration.
	 *
	 * @return A {@link PortalCache} instance with the current configuration
	 */
	PortalCache<T> build();
}
