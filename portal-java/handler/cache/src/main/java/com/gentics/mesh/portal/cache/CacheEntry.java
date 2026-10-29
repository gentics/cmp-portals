package com.gentics.mesh.portal.cache;

import java.io.Serializable;

/**
 * Container for a cache entry.
 */
public class CacheEntry implements Serializable {

	private static final long serialVersionUID = -8228693971523734901L;

	private final String content;
	private final String etagHeader;

	/**
	 * Create cache entry for the given content (includes content and the generated etag)
	 * @param content cotnent
	 * @return cache entry
	 */
	public static CacheEntry content(String content) {
		return new CacheEntry(content, ETag.prepareHeader(ETag.hash(content), false));
	}

	/**
	 * Create cache entry for the etag (will not include content)
	 * @param eTag etag
	 * @return cache entry
	 */
	public static CacheEntry eTag(String eTag) {
		return new CacheEntry(null, eTag);
	}

	private CacheEntry(String content, String eTag) {
		this.content = content;
		this.etagHeader = eTag;
	}

	public String getContent() {
		return content;
	}

	public String getEtagHeader() {
		return etagHeader;
	}
}
