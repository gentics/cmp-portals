package com.gentics.mesh.portal.authentication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.gentics.mesh.portal.api.authentication.AuthenticationCookie;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.authentication.PortalUser;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.AndAuthorization;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.Authorizations;
import io.vertx.ext.auth.authorization.OrAuthorization;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.authorization.impl.AuthorizationsImpl;
import io.vertx.ext.auth.impl.UserImpl;
import io.vertx.ext.web.RoutingContext;

/**
 * Simple {@link PortalUser} implementation for testing.
 *
 * <p>
 *     It declares two cookies as authentication cookies:
 *     <ul>
 *         <li>{@code AuthenticationHandler.COOKIE_AUTH} contains the username, and</li>
 *         <li>{@code COOKIE_USER_AUTHORITIES} contains a comma separated list of authorities this user has</li>
 *     </ul>
 * </p>
 *
 * <p>
 *     Any changes like when the user is upgraded, logged out, or checked for
 *     a certain authorization, are reflected in the {@link #principal()}.
 * </p>
 */
public class FakePortalUser extends PortalUser {

	public static final String EMAIL = "fake@user.com";
	public static final String FIRSTNAME = "Fake";
	public static final String LASTNAME = "User";
	public static final JsonObject RAW_DATA = new JsonObject()
		.put("firstname", FIRSTNAME)
		.put("lastname", LASTNAME)
		.put("email", EMAIL)
		.put("fake", "data");

	static final String COOKIE_USER_AUTHORITIES = "gpj.auth.authorities";
	static final String COOKIE_AUTH_EXPIRED = "gpj.auth.expired";

	private final String username;
	/** Authorizations for this user. */
	private final Authorizations authorizations = new AuthorizationsImpl();
	/** List of authorizations that have been checked via {@link #isAuthorized(String, Handler)}. */
	private final List<String> checkedAuthorizations = new ArrayList<>();

	private boolean upgraded = false;
	private boolean loggedout = false;

	FakePortalUser(String username, String ...authorizations) {
		super(new UserImpl());

		this.username = username;

		for (String authz : authorizations) {
			this.authorizations.put(FakeAuthenticationHandler.PROVIDER_ID, PermissionBasedAuthorization.create(authz));
		}
	}

	@Override
	public Authorizations authorizations() {
		return authorizations;
	}

	public static FakePortalUser defaultUserInstance() {
		return new FakePortalUser("fake-default-user");
	}

	@Override
	public String meshAccessToken() {
		return null;
	}

	@Override
	public Collection<AuthenticationCookie> authCookies() {
		Set<String> authoritiesCookieValues = new HashSet<>();
		authorizations.forEach(FakeAuthenticationHandler.PROVIDER_ID, authz -> {
			authoritiesCookieValues.add(((PermissionBasedAuthorization) authz).getPermission());
		});

		return Arrays.asList(
			new AuthenticationCookie(AuthenticationHandler.COOKIE_AUTH, username, 0),
			new AuthenticationCookie(COOKIE_USER_AUTHORITIES, authoritiesCookieValues.stream().collect(Collectors.joining(", ")), 0));
	}

	@Override
	public String userId() {
		return username;
	}

	@Override
	public String username() {
		return username;
	}

	@Override
	public String firstName() {
		return FIRSTNAME;
	}

	@Override
	public String lastName() {
		return LASTNAME;
	}

	@Override
	public String email() {
		return EMAIL;
	}

	@Override
	public JsonObject rawData() {
		return RAW_DATA.copy().put("username", username);
	}

	@Override
	public JsonObject attributes() {
		return null;
	}

	public void checkAuthorization(RoutingContext rc, Authorization required) {
		addCheckedAuthorization(required);

		if (required.match(this)) {
			rc.next();
		} else {
			rc.fail(HttpResponseStatus.FORBIDDEN.code());
		}
	}


	@Override
	public JsonObject principal() {
		JsonArray roles = new JsonArray();

		return new JsonObject()
			.put("username", username)
			.put("roles", roles)
			.put("upgraded", upgraded)
			.put("loggedout", loggedout)
			.put("checkedAuthorities", checkedAuthorizations);
	}

	public FakePortalUser upgrade() {
		upgraded = true;

		return this;
	}

	@Override
	public User merge(User other) {
		throw new UnsupportedOperationException("FakePortalUser cannot be merged");
	}

	/**
	 * To be called by {@link FakeAuthenticationHandler#logout(RoutingContext, Handler)}.
	 *
	 * @return This user for fluent API access
	 */
	public FakePortalUser logout() {
		loggedout = true;

		return this;
	}

	/**
	 * Add the given authorization to the list of checked authorizations.
	 *
	 * @param required The authorization to add.
	 */
	private void addCheckedAuthorization(Authorization required) {
		if (required instanceof OrAuthorization) {
			((OrAuthorization) required).getAuthorizations().forEach(this::addCheckedAuthorization);
		} else if (required instanceof AndAuthorization) {
			((AndAuthorization) required).getAuthorizations().forEach(this::addCheckedAuthorization);
		} else if (required instanceof  PermissionBasedAuthorization) {
			checkedAuthorizations.add(((PermissionBasedAuthorization) required).getPermission());
		} else {
			throw new IllegalArgumentException("FakePortalUser must use PermissionBasedAuthorization instances");
		}
	}
}
