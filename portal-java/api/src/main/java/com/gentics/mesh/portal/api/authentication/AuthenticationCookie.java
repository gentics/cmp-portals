package com.gentics.mesh.portal.api.authentication;

/**
 * POJO for necessary authentication cookie values.
 */
public class AuthenticationCookie {

	private final String name;
	private final String value;
	private final long maxAge;

	public AuthenticationCookie(String name, String value, long maxAge) {
		this.name = name;
		this.value = value;
		this.maxAge = maxAge;
	}

	/**
	 * Get the cookie name.
	 *
	 * @return The cookie name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the cookie value.
	 *
	 * @return The cookie value.
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Get the cookies maximum age.
	 *
	 * @return The cookies maximum age.
	 */
	public long getMaxAge() {
		return maxAge;
	}
}
