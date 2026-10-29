package com.gentics.mesh.portal.authentication.oauth2;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.portal.api.authentication.AuthenticationCookie;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.authentication.PortalUser;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

/**
 * A {@code PortalUser} implementation created from a JWT issued by Keycloak.
 */
public class OAuth2PortalUser extends PortalUser {

	private static final Logger log = LoggerFactory.getLogger(OAuth2PortalUser.class);

	private static final String FIELD_EXPIRES_AT = "expires_at";
	private static final String FIELD_EXPIRES_IN = "expires_in";
	private static final String FIELD_REFRESH_EXPIRES_IN = "refresh_expires_in";

	/**
	 * Wrap the given access token in a portal user.
	 *
	 * <p>
	 *     This constructor is intended for a user object that was just created
	 *     by the {@link io.vertx.ext.web.handler.OAuth2AuthHandler}. It
	 *     should contain an access token and a refresh token.
	 * </p>
	 *
	 * @param wrappedUser The wrapper user object
	 */
	OAuth2PortalUser(User wrappedUser) {
		this(wrappedUser, null, null);

		if (StringUtils.isBlank(wrappedUser.principal().getString(KeycloakAuthenticationHandler.FIELD_REFRESH_TOKEN))) {
			log.warn("Created user does not have a refresh token.");
		}
	}

	/**
	 * Wrap the given access token in a portal user.
	 *
	 * <p>
	 *     This constructor is intended for creating an instance from
	 *     the values of the authentication and refresh cookies.
	 * </p>
	 *
	 * @param wrappedUser The wrapper user object
	 * @param refreshToken The refresh token for the users access token
	 * @param idToken The ID token for the user
	 */
	OAuth2PortalUser(User wrappedUser, String refreshToken, String idToken) {
		super(wrappedUser);

		if (StringUtils.isNotBlank(refreshToken)) {
			this.wrappedUser.principal()
				.put(KeycloakAuthenticationHandler.FIELD_REFRESH_TOKEN, refreshToken);
		}

		if (StringUtils.isNotBlank(idToken)) {
			this.wrappedUser.principal()
				.put(KeycloakAuthenticationHandler.FIELD_ID_TOKEN, idToken);
		}
	}

	/**
	 * Since Mesh accepts JWTs this method just returns the wrapped
	 * opaque access token.
	 *
	 * @return The opaque access token of the wrapped user
	 */
	@Override
	public String meshAccessToken() {
		return wrappedUser.principal().getString(KeycloakAuthenticationHandler.FIELD_ACCESS_TOKEN);
	}

	/**
	 * Get the encoded refresh token for this user.
	 *
	 * @return The encoded refresh token for this user
	 */
	public String refreshToken() {
		return wrappedUser.principal().getString(KeycloakAuthenticationHandler.FIELD_REFRESH_TOKEN);
	}

	/**
	 * Get the decoded access token.
	 *
	 * @return The decoded access token.
	 */
	public JsonObject accessToken() {
		JsonObject token = wrappedUser.attributes().getJsonObject("accessToken");

		return token != null ? token : new JsonObject();
	}

	/**
	 * Get the opaque ID token for this user.
	 * @return The opaque ID token for this user
	 */
	public String idToken() {
		return wrappedUser.principal().getString(KeycloakAuthenticationHandler.FIELD_ID_TOKEN);
	}

	/**
	 * Create the cookies containing the actual access token as well as the
	 * refresh token.
	 *
	 * <p>
	 *     The cookie max age is the maximum of the expiry dates of both
	 *     tokens when they are both present, or just the expiry date of the
	 *     access token when no refresh token is available. When none of the
	 *     tokens defines an expiration date, the cookies will be session
	 *     cookies.
	 * </p>
	 *
	 * @return The cookies containing the access token and refresh token
	 */
	@Override
	public Collection<AuthenticationCookie> authCookies() {
		JsonObject principal = wrappedUser.principal();
		long maxAge;

		if (principal.containsKey(FIELD_EXPIRES_IN)) {
			if (principal.containsKey(FIELD_REFRESH_EXPIRES_IN)) {
				maxAge = Math.max(
					principal.getLong(FIELD_EXPIRES_IN),
					principal.getLong(FIELD_REFRESH_EXPIRES_IN));
			} else {
				maxAge = principal.getLong(FIELD_EXPIRES_IN);
			}
		} else if (principal.containsKey(FIELD_EXPIRES_AT)) {
			maxAge = (principal.getLong(FIELD_EXPIRES_AT) - System.currentTimeMillis()) / 1000;
		} else {
			log.warn("Principal did not contain expires_in nor expires_at fields, authentication cookie will be a session cookie");
			maxAge = 0;
		}

		String accessToken = principal.getString(KeycloakAuthenticationHandler.FIELD_ACCESS_TOKEN);
		String refreshToken = principal.getString(KeycloakAuthenticationHandler.FIELD_REFRESH_TOKEN);
		String idToken = principal.getString(KeycloakAuthenticationHandler.FIELD_ID_TOKEN);

		Collection<AuthenticationCookie> cookies = new ArrayList<>();

		cookies.add(new AuthenticationCookie(AuthenticationHandler.COOKIE_AUTH, accessToken, maxAge));
		cookies.add(new AuthenticationCookie(KeycloakAuthenticationHandler.COOKIE_AUTH_REFRESH, refreshToken, maxAge));

		if (idToken != null) {
			cookies.add(new AuthenticationCookie(KeycloakAuthenticationHandler.COOKIE_AUTH_ID, idToken, maxAge));
		}

		return cookies;
	}

	@Override
	public String userId() {
		return accessToken().getString("sub");
	}

	@Override
	public String username() {
		return accessToken().getString("preferred_username");
	}

	@Override
	public String firstName() {
		return accessToken().getString("given_name");
	}

	@Override
	public String lastName() {
		return accessToken().getString("family_name");
	}

	@Override
	public String email() {
		return accessToken().getString("email");
	}

	@Override
	public JsonObject rawData() {
		return wrappedUser.attributes();
	}

	@Override
	public JsonObject principal() {
		return wrappedUser.principal();
	}

	@Override
	public JsonObject attributes() {
		return wrappedUser.attributes();
	}

	@Override
	public User merge(User other) {
		throw new UnsupportedOperationException("Merging OAuth2 portal users is not supported");
	}

	@Override
	public String toString() {
		return "[" + getClass().getSimpleName() + " userId: " + userId() + ", username: " + username() + "]";
	}
}
