package com.gentics.mesh.portal.api.cache;

/**
 * Expire modes for caches.
 */
public enum ExpireMode {
	/** Entries do not expire by themselves and are only removed when the cache reaches. */
	None,
	/** Entries expire a certain time after they are created or updated. */
	Write,
	/** Entries expire a certain time after they are created, updated or read. */
	Access
}
