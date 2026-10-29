package com.gentics.mesh.portal.util;

import com.gentics.mesh.portal.api.authentication.AuthenticationCookie;
import com.gentics.mesh.portal.api.authentication.PortalUser;
import com.gentics.mesh.portal.api.server.CookieProvider;
import io.vertx.core.http.Cookie;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.binary.Base64;

import java.util.Collection;
import java.util.function.Function;

/**
 * Utility methods for handling JWTs.
 */
public class AuthUtils {

	private static final Logger log = LoggerFactory.getLogger(AuthUtils.class);

	/**
	 * Add the given authentication cookies to the routing contexts response.
	 *
	 * <p>
	 *     The provided {@code encryption} function is applied to the cookie
	 *     values before adding them.
	 * </p>
	 *
	 * @param rc The current routing context.
	 * @param cookieProvider The cookie provider.
	 * @param cookies The cookies to add.
	 * @param encryption The encryption function.
	 */
	public static void addAuthCookies(RoutingContext rc, CookieProvider cookieProvider, Collection<AuthenticationCookie> cookies, Function<String, String> encryption) {
		boolean isUseSessionCookies = false;
		addAuthCookies(rc, cookieProvider, cookies, encryption, isUseSessionCookies);
	}
	/**
	 * Add the given authentication cookies to the routing contexts response.
	 *
	 * <p>
	 *     The provided {@code encryption} function is applied to the cookie
	 *     values before adding them.
	 * </p>
	 *
	 * @param rc The current routing context.
	 * @param cookieProvider The cookie provider.
	 * @param cookies The cookies to add.
	 * @param encryption The encryption function.
	 * @param isUseSessionCookies ignore the max age from the original cookies and create session cookies instead
	 */
	public static void addAuthCookies(RoutingContext rc, CookieProvider cookieProvider, Collection<AuthenticationCookie> cookies, Function<String, String> encryption, boolean isUseSessionCookies) {
		cookies.forEach(authCookie -> {
			if (log.isDebugEnabled()) {
				log.debug("Adding authentication cookie {}", authCookie.getName());
			}

			Cookie cookie = cookieProvider.create(authCookie.getName(), encryption.apply(authCookie.getValue()));
			if (!isUseSessionCookies) {
				cookie.setMaxAge(authCookie.getMaxAge());
			}


			rc.response().addCookie(cookie);
		});
	}

	/**
	 * Delete the authentication cookies.
	 *  @param rc The current routing context
	 *
	 */
	public static void deleteAuthCookies(CookieProvider cookieProvider, RoutingContext rc) {
		User user = rc.user();

		if (user instanceof PortalUser) {
			if (log.isDebugEnabled()) {
				log.debug("Deleting {} authentication cookies", user.getClass().getSimpleName());
			}

			for (AuthenticationCookie authCookie : ((PortalUser) user).authCookies()) {
				if (log.isTraceEnabled()) {
					log.trace("Deleting cookie {}", authCookie.getName());
				}

				Cookie cookie = cookieProvider.create(authCookie.getName(), CookieProvider.DELETED_COOKIE_VALUE)
					.setMaxAge(0);

				rc.response().addCookie(cookie);
			}
		} else {
			log.warn(
				"Cannot delete authentication cookies for non PortalUser: {}",
				user == null ? "(null)" : user.getClass().getName());
		}
	}

	/**
	 * Tries to decode the given string as a JWT and returns the payload part.
	 *
	 * <strong>caution:</strong> this method will <em>not</em> check the tokens signature, and should not be used for verification.
	 *
	 * @deprecated Use {@code OAuth2PortalUser#accessToken()} instead
	 * @param token the jwt to be decoded
	 * @return the payload part of the given jwt as a <code>jsonobject</code>
	 */
	@Deprecated
	public static JsonObject decodeToken(String token) {
		return decodeToken(token, true);
	}

	/**
	 * Tries to decode the given string as a JWT and returns the payload part.
	 *
	 * <strong>Caution:</strong> this method will <em>not</em> check the tokens signature, and should not be used for verification.
	 *
	 * @param token The JWT to be decoded
	 * @return The payload part of the given JWT as a <code>JsonObject</code>
	 */
	public static JsonObject decodeToken(String token, boolean payloadOnly) {
		if (token != null) {
			if (CookieProvider.DELETED_COOKIE_VALUE.equals(token)) {
				log.debug("Token cookie was already deleted");

				return new JsonObject();
			}

			try {
				String[] parts = token.split("\\.", 3);

				if (payloadOnly) {
					return decodeTokenPart(parts[1]);
				}

				return new JsonObject()
					.put("head", decodeTokenPart(parts[0]))
					.put("payload", decodeTokenPart(parts[1]))
					.put("signature", parts[2]);
			} catch (Exception e) {
                log.error("Could not decode token: {}\n{}", e.getMessage(), token, e);
			}
		} else {
			log.warn("No token specified");
		}

		return new JsonObject();
	}

	/**
	 * Creates a {@link JsonObject} from the given Base64 web encoded String.
	 *
	 * @param part The JWT part to decode
	 * @return The <code>JsonObject</code> for the given JWT part
	 */
	private static JsonObject decodeTokenPart(String part) {
		try {
			return new JsonObject(new String(Base64.decodeBase64(part)));
		} catch (Exception e) {
			log.error("Could not decode token part: {}\nPart: {}", e.getMessage(), part);
		}

		return new JsonObject();
	}
}
