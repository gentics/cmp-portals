package com.gentics.mesh.portal.api.authentication;

import io.vertx.core.http.Cookie;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.Authorizations;

import java.util.Collection;
import java.util.Collections;

/**
 * An extension of {@link User} with convenience methods for getting the
 * commonly needed data.
 */
public abstract class PortalUser implements User {

	protected User wrappedUser;

	protected boolean cookiesChanged;

	protected PortalUser(User wrappedUser) {
		this.wrappedUser = wrappedUser;
	}

	/**
	 * The wrapped users authorizations that have previously been loaded by the
	 * providers.
	 *
	 * @return The users authorizations.
	 */
	@Override
	public Authorizations authorizations() {
		return wrappedUser.authorizations();
	}

	/**
	 * Checks whether any of the authentication cookies has changed, since the
	 * user was created.
	 *
	 * <p>
	 *     This is checked during post authentication actions when optional
	 *     additional user data was added after the initial authentication.
	 * </p>
	 *
	 * @return {@code true} when the authentication cookes changes since the
	 * 		user was created or this method was called last, and {@code false}
	 * 		otherwise
	 */
	public final boolean authCookiesChanged() {
		return cookiesChanged;
	}

	/**
	 * Calling this method makes sure, that the next call to
	 * {@link #authCookiesChanged()} returns {@code true} to indicate that the
	 * updated cookies need to be sent to the client.
	 *
	 * @return Fluent API.
	 */
	public final PortalUser dirtCookies() {
		cookiesChanged = true;

		return this;
	}

	/**
	 * Get all necessary authentication cookies.
	 *
	 * <p>
	 *     The {@link Cookie#setSecure(boolean) secure flag} will be set by
	 *     the {@code AbstractAuthenticationHandler}, but the following values
	 *     must be set by the {@code PortalUser} implementation:
	 *     <ul>
	 *         <li>{@code setHttpOnly()} (preferably {@code true})</li>
	 *         <li>{@code setPath()} (preferably {@code "/"})</li>
	 *         <li>{@code setMaxAge()} (only if a meaningful max age can be set)</li>
	 *     </ul>
	 * </p>
	 *
	 * <p>
	 *     Implementations should make sure, that after calling this method,
	 *     calls to {@link #authCookiesChanged()} returns {@code false}.
	 * </p>
	 *
	 * @return All necessary authentication cookies
	 */
	public abstract Collection<AuthenticationCookie> authCookies();

	/**
	 * Get the access token to be sent to Mesh representing this user.
	 *
	 * @return The access token to be sent to Mesh representing this user
	 */
	public abstract String meshAccessToken();


	/**
	 * The unique ID of the user.
	 *
	 * <p>
	 *     This is likely identical to {@link #username()} but not necessarily.
	 * </p>
	 *
	 * @return The user unique ID
	 */
	public abstract String userId();

	/**
	 * The username used for logging in.
	 *
	 * <p>
	 *     This is likely identical to {@link #userId()} but not necessarily.
	 * </p>
	 *
	 * @return The username used for logging in
	 */
	public abstract String username();

	/**
	 * The first name of the user.
	 *
	 * @return The first name of the user
	 */
	public abstract String firstName();

	/**
	 * The last name of the user.
	 *
	 * @return The last name of the user
	 */
	public abstract String lastName();

	/**
	 * The users email address.
	 *
	 * @return The users email address
	 */
	public abstract String email();

	/**
	 * The full name of the user.
	 *
	 * @return The full name of the user
	 */
	public String fullName() {
		return firstName() + " " + lastName();
	}

	/**
	 * Extra data provided by the authenticator implementation.
	 *
	 * @return Extra data provided by the authenticator implementation
	 */
	public abstract JsonObject rawData();

	/**
	 * Provides a JSON object with the information from {@link #userId()},
	 * {@link #username()}, and {@link #fullName()}.
	 *
	 * @return A JSON object with all the convenience information about the user
	 */
	public JsonObject userInfo() {
		return new JsonObject()
			.put("userId", userId())
			.put("username", username())
			.put("firstName", firstName())
			.put("lastName", lastName())
			.put("email", email())
			.put("fullName", fullName())
			.put("raw", rawData());
	}
}
