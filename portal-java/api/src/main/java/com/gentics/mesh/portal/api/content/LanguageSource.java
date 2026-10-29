package com.gentics.mesh.portal.api.content;

/**
 * Different sources for the portal to determine the language for the current request.
 *
 * <p>
 *     Sources with higher priorities will always be preferred.
 * </p>
 */
public enum LanguageSource {
	/** The language was specified in the language cookie of the request. */
	COOKIE(3),
	/** The language was specified in the node loaded from Mesh. */
	PAGE(2),
	/** The language was specified in the <code>Accept-Language</code> HTTP header. */
	HEADER(1),
	/** The configured default language is used. */
	DEFAULT(0);

	private final int priority;

	LanguageSource(int priority) {
		this.priority = priority;
	}

	/**
	 * Checks whether this language source has a higher priority than the <code>other</code> source.
	 *
	 * @param other The other language source to compare to
	 * @return <code>true</code> if this language source has a higher priority than <code>other</code>, and
	 * <code>false</code> otherwise
	 */
	public boolean hasHigherPriority(LanguageSource other) {
		return other == null || other.priority < this.priority;
	}

	/**
	 * Tests whether this language source is <em>not</em> the default fallback.
	 *
	 * @return <code>true</code> when this language source is not the default fallback, and <code>false</code> otherwise
	 */
	public boolean isNonDefault() {
		return this != DEFAULT;
	}

	@Override
	public String toString() {
		switch (this) {
		case COOKIE:
			return "language cookie";

		case PAGE:
			return "loaded page";

		case HEADER:
			return "Accept-Language header";

		default:
		case DEFAULT:
			return "default language";
		}
	}
}
