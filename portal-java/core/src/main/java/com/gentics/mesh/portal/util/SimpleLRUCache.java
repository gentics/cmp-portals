package com.gentics.mesh.portal.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple implementation of a <em>Least Recently Used</em> cache backed by a
 * {@link LinkedHashMap}.
 *
 * When the maximum size of the cache {@link #maxEntries} is reached, inserting a
 * new element will remove the element that was not accessed for the longest time.
 *
 * TODO Maybe this could be replaced by Caffeine in the future.
 *
 * @deprecated Use {@link com.gentics.mesh.portal.api.cache.PortalCache} instead
 *
 * @param <K> The type of the cache keys
 * @param <V> The type of the cached values
 */
@Deprecated
public class SimpleLRUCache<K, V> extends LinkedHashMap<K, V> {
	/** The default initial capacity for the underlying map. */
	public static final int DEFAULT_INITIAL_CAPACITY = 16;
	/** The default load factor for the underlying map. */
	public static final float DEFAULT_LOAD_FACTOR = .75f;
	/** The default maximum cache size. */
	public static final int DEFAULT_MAX_ENTRIES = 512;

	private static final long serialVersionUID = -7312234902945121496L;

	/** The maximum size of the cache. */
	private final int maxEntries;

	/**
	 * Create a new cache backed by a map with the specified initial capacity,
	 * load factor and maximum cache size.
	 *
	 * @param initialCapacity The initial capacity of the backing map
	 * @param loadFactor The load factor of the backing map
	 * @param maxEntries The maximum cache size
	 */
	public SimpleLRUCache(int initialCapacity, float loadFactor, int maxEntries) {
		super(initialCapacity, loadFactor, true);

		this.maxEntries = maxEntries;
	}

	/**
	 * Create a new cache using default values for the backing map and
	 * the specified maximum size.
	 *
	 * @param maxEntries The maximum cache size
	 */
	public SimpleLRUCache(int maxEntries) {
		this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, maxEntries);
	}

	/**
	 * Create a new cache using default values for the backing map and
	 * a maximum size of {@link #DEFAULT_MAX_ENTRIES}.
	 */
	public SimpleLRUCache() {
		this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_MAX_ENTRIES);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.LinkedHashMap#removeEldestEntry(java.util.Map.Entry)
	 */
	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
		return size() >= maxEntries;
	}
}
