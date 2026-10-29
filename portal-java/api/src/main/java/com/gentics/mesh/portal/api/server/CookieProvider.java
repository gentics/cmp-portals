package com.gentics.mesh.portal.api.server;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;

/**
 * Creates cookies according to the portal configuration.
 *
 * <p>
 *     When the server URL uses HTTPS, the created cookies will have the
 *     {@code Secure} flag set.
 * </p>
 *
 * <p>
 *     When the usage of the {@code __Host-} prefix is enabled, the actual
 *     cookie name will be fixed beforehand, if and only if the {@code Secure}
 *     flag is also active.
 * </p>
 */
public interface CookieProvider {

	String DELETED_COOKIE_VALUE = "deleted";

	/**
	 * Prepend the {@code __Host-} prefix to the given cookie name if enabled in
	 * the configuration.
	 *
	 * @param name The original cookie name
	 * @return The original cookie name with the optional {@code __Host-} prefix
	 */
	String realName(String name);

	/**
	 * Get the cookie with the given base name from the routing context.
	 *
	 * <p>
	 *     The name is adapted by {@link #realName(String)} first.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param name The base name of the cookie to retrieve
	 * @return The cookie with the given base name
	 */
	default Cookie get(RoutingContext rc, String name) {
		return rc.request().getCookie(realName(name));
	}

	/**
	 * Convenience method for {@link #create(String, String, CookieSameSite)}
	 * where the {@link CookieSameSite same site policy} is taken from the
	 * portal configuration.
	 *
	 * @see #create(String, String, CookieSameSite)
	 * @param name The cookie name (maybe adjusted by {@link #realName(String)})
	 * @param value The cookie value
	 * @return The created cookie
	 */
	default Cookie create(String name, String value) {
		return create(name, value, null);
	}

	/**
	 * Create the cookie with the given name, value and CookieSameSite Attribute
	 *
	 * <p>
	 *     The name will be passed to {@link #realName(String)} to prepend
	 *     the {@code __Host-} prefix if required.
	 * </p>
	 *
	 * <p>
	 *     Apart from name and value, the following flags will be set for
	 *     created cookies:
	 *     <ul>
	 *         <li>{@code path}: {@code "/"}</li>
	 *         <li>{@code HttpOnly}: {@code true}</li>
	 *         <li>{@code Secure}: if and only if the server URL uses HTTPS</li>
	 *     </ul>
	 * </p>
	 *
	 * <p>
	 *     When {@code sameSite} is {@code null}, the value is taken from the
	 *     configuration (and defaults to {@link CookieSameSite#STRICT}).
	 * </p>
	 *
	 * @see #realName(String)
	 * @param name The cookie name (maybe adjusted by {@link #realName(String)})
	 * @param value The cookie value
	 * @param sameSite The SameSite attribute (may be {@code null})
	 * @return The created cookie
	 */
	Cookie create(String name, String value, CookieSameSite sameSite);

	/**
	 * Create a cookie for the given name with the {@link #DELETED_COOKIE_VALUE}
	 * and a maximum age of 0 so that the browser deletes the cookie.
	 *
	 * @param name The name of the cookie to be deleted
	 * @return A cookie with the given name and the {@code "deleted"} value, and
	 * 		a max-age of 0
	 */
	default Cookie delete(String name) {
		return create(name, DELETED_COOKIE_VALUE)
			.setMaxAge(0);
	}

	/**
	 * Checks whether the given cookie is already deleted.
	 *
	 * @param cookie The cookie to check
	 * @return {@code true} when the {@code cookie} is either {@code null} or
	 * 		its value is equal to {@link #DELETED_COOKIE_VALUE}, and
	 * 		{@code false} otherwise
	 */
	default boolean isDeleted(Cookie cookie) {
		return cookie == null || DELETED_COOKIE_VALUE.equalsIgnoreCase(cookie.getValue());
	}
}
