package com.gentics.mesh.portal.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import com.gentics.mesh.portal.api.authentication.PortalUser;
import io.vertx.ext.web.UserContext;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.option.EncryptionMode;
import com.gentics.mesh.portal.config.option.EndpointOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.content.LanguageVariantLoader;
import com.gentics.mesh.portal.server.CookieProviderImpl;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.UserContextInternal;

/**
 * Test cases for {@link AuthenticationHandler}.
 */
public class AuthenticationHandlerTest extends AbstractPortalTest {

	public static final String RC_MISSING_PERM = "gpj.content.missingPerm";

	/**
	 * Make sure no user is set when no authentication cookie is provided.
	 */
	@Test
	public void testSetUserNoCookie() {
		RoutingContext rc = mockRoutingContext("/", "");
		UserContextInternal mockUserContext = mock(UserContextInternal.class);

		when(rc.userContext()).thenReturn(mockUserContext);

		createAuthenticationHandler().handle(rc);

		verify(mockUserContext, never()).setUser(any());
	}

	/**
	 * Make sure the user is set, when both cookies are present.
	 */
	@Test
	public void testSetUser() {
		RoutingContext rc = mockRoutingContext("/", "");
		UserContextInternal mockUserContext = mock(UserContextInternal.class);
		String username = "fake-user";

		when(rc.request().getCookie(eq(AuthenticationHandler.COOKIE_AUTH)))
			.thenReturn(Cookie.cookie(AuthenticationHandler.COOKIE_AUTH, username));
		when(rc.request().getCookie(eq(FakePortalUser.COOKIE_USER_AUTHORITIES)))
			.thenReturn(Cookie.cookie(FakePortalUser.COOKIE_USER_AUTHORITIES, "anonymous"));
		when(rc.userContext()).thenReturn(mockUserContext);

		createAuthenticationHandler().handle(rc);

		JsonObject expectedUserInfo = new JsonObject()
			.put("userId", username)
			.put("username", username)
			.put("firstName", FakePortalUser.FIRSTNAME)
			.put("lastName", FakePortalUser.LASTNAME)
			.put("email", FakePortalUser.EMAIL)
			.put("fullName", FakePortalUser.FIRSTNAME + " " + FakePortalUser.LASTNAME)
			.put("raw", FakePortalUser.RAW_DATA.copy().put("username", username));

		verify(mockUserContext, times(1)).setUser(any());
		verify(rc, times(1)).put(eq("userInfo"), eq(expectedUserInfo));
	}

	@Test
	public void testSetUserInvalidCookies() {
		RoutingContext rc = mockRoutingContext("/", "");
		String username = "fake-user";
		UserContextInternal mockUserContext = mock(UserContextInternal.class);

		when(rc.request().getCookie(eq(AuthenticationHandler.COOKIE_AUTH)))
			.thenReturn(Cookie.cookie(AuthenticationHandler.COOKIE_AUTH, username));
		when(rc.request().getCookie(eq(FakePortalUser.COOKIE_USER_AUTHORITIES)))
			.thenReturn(Cookie.cookie(FakePortalUser.COOKIE_USER_AUTHORITIES, "anonymous"));
		when(rc.request().getCookie(eq(FakePortalUser.COOKIE_AUTH_EXPIRED)))
			.thenReturn(Cookie.cookie(FakePortalUser.COOKIE_AUTH_EXPIRED, "true"));
		when(rc.userContext()).thenReturn(mockUserContext);

		createAuthenticationHandler().handle(rc);

		// Make sure no user was set, and the request continues anonymously.
		verify(mockUserContext, never()).setUser(any());
		verify(rc, times(1)).next();
	}

	@Test
	public void testPostAuthProtectedPage() {
		doTestPostAuth();
	}

	public void doTestPostAuth() {
		RoutingContext rc = mockRoutingContext("/auth-callback", "");

		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		rc.put("gpj.auth.active", true);

		assertThat(rc.user().principal().getMap())
			.as("User principal before upgrade")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.emptyList());

		FakeAuthenticationHandler handler = createAuthenticationHandler();

		handler.handlePreAuth(rc);
		handler.handle(rc);

		assertThat(rc.user().principal().getMap())
			.as("User principal after upgrade")
			.containsEntry("upgraded", true)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.emptyList());
	}

	/**
	 * Test that no user is reset, and no cookies are added.
	 */
	@Test
	public void testLogoutAnonymous() {
		RoutingContext rc = mockRoutingContext("/auth/logout", "");
		UserContext mockUserContext = mock(UserContext.class);

		when(rc.userContext()).thenReturn(mockUserContext);

		createAuthenticationHandler().handleLogout(rc);

		verify(mockUserContext, never()).clear();
		verify(rc.response(), never()).addCookie(argThat(cookieMatcher(AuthenticationHandler.COOKIE_AUTH, CookieProvider.DELETED_COOKIE_VALUE)));
		verify(rc.response(), never()).addCookie(argThat(cookieMatcher(FakePortalUser.COOKIE_USER_AUTHORITIES, CookieProvider.DELETED_COOKIE_VALUE)));
		verify(rc, times(1)).user();
		verify(rc, times(1)).next();
	}

	/**
	 * Test that the user is reset, the authentication cookies deleted, and
	 * that {@link FakeAuthenticationHandler#logout(RoutingContext, Handler)}
	 * is called.
	 */
	@Test
	public void testLogoutWithUser() {
		RoutingContext rc = mockRoutingContext("/auth/logout", "");
		PortalUser fakeUser = FakePortalUser.defaultUserInstance();
		UserContext mockUserContext = mock(UserContext.class);

		when(mockUserContext.get()).thenReturn(fakeUser);
		when(rc.userContext()).thenReturn(mockUserContext);
		when(rc.user()).thenAnswer(in -> mockUserContext.get());

		assertThat(rc.user().principal().getMap())
			.as("User principal before logout")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.emptyList());

		createAuthenticationHandler().handleLogout(rc);

		verify(mockUserContext, times(1)).clear();
		verify(rc.response(), times(1)).addCookie(argThat(cookieMatcher(AuthenticationHandler.COOKIE_AUTH, CookieProvider.DELETED_COOKIE_VALUE)));
		verify(rc.response(), times(1)).addCookie(argThat(cookieMatcher(FakePortalUser.COOKIE_USER_AUTHORITIES, CookieProvider.DELETED_COOKIE_VALUE)));
		verify(rc, times(1)).next();

		assertThat(rc.user().principal().getMap())
			.as("User principal after logout")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", true)
			.containsEntry("checkedAuthorities", Collections.emptyList());
	}

	/**
	 * Test that the request fails with 403 Forbidden when a user is logged
	 * in but is missing perms.
	 */
	@Test
	public void testMissingPermWithUser() {
		RoutingContext rc = mockRoutingContext("/private/page.html", "");

		rc.put(RC_MISSING_PERM, Collections.singletonList("fake-uuid-for-unauthorized-page"));
		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		createAuthenticationHandler().handleMissingPerms(rc);

		verify(rc, times(1)).user();
		verify(rc, times(1)).get(Constants.RC_NODE_TO_RENDER);
		verify(rc, times(1)).get(RC_MISSING_PERM);
		verify(rc, times(1)).fail(HttpResponseStatus.FORBIDDEN.code());
	}

	/**
	 * Test that {@link AuthenticationHandler#handleAuthentication(RoutingContext)}
	 * is called when no user is logged in.
	 */
	@Test
	public void testMissingPermAnonymous() {
		RoutingContext rc = mockRoutingContext("/private/page.html", "");

		rc.put(RC_MISSING_PERM, Collections.singletonList("fake-uuid-for-unauthorized-page"));
		createAuthenticationHandler().handleMissingPerms(rc);

		// The FakeAuthenticationHandler will respond with 401.
		verify(rc.response(), times(1)).setStatusCode(HttpResponseStatus.UNAUTHORIZED.code());
		verify(rc.response(), times(1)).putHeader(HttpHeaderNames.WWW_AUTHENTICATE, "Fake realm=dummy");
		verify(rc.response(), times(1)).end();
	}

	@Test
	public void testMissingPermAnonymousWithRedirect() {
		config.getAuthOptions().setUseUnauthorizedRedirect(true);
		config.getRedirectOptions().getStatusPages().put(HttpResponseStatus.UNAUTHORIZED.codeAsText().toString(), "fake-401-error-page.html");

		RoutingContext rc = mockRoutingContext("/private/page.html", "");

		rc.put(RC_MISSING_PERM, Collections.singletonList("fake-uuid-for-unauthorized-page"));
		createAuthenticationHandler().handleMissingPerms(rc);

		verify(rc, times(1)).fail(eq(HttpResponseStatus.UNAUTHORIZED.code()));
	}

	@Test
	public void testProtectedEndpointUnauthorized() {
		RoutingContext rc = mockRoutingContext("/protected", "");

		createAuthenticationHandler().handleAuthorization(rc);

		verify(rc, times(1)).fail(HttpResponseStatus.UNAUTHORIZED.code());
	}

	@Test
	public void testProtectedEndpointSimple() {
		String path = "/protected";
		RoutingContext rc = mockRoutingContext(path, "");

		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		createAuthenticationHandler()
			.addProtectedEndpoint(new EndpointOptions().setPath(path))
			.handleAuthorization(rc);

		verify(rc, times(1)).next();
	}

	@Test
	public void testProtectedEndpointWithPerms() {
		String path = "/protected";
		RoutingContext rc = mockRoutingContext(path, "");
		FakePortalUser user = FakePortalUser.defaultUserInstance();

		when(rc.user()).thenReturn(user);

		assertThat(rc.user().principal().getMap())
			.as("User principal before authorization")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.emptyList());

		String requiredPerm = "required-permission";

		user.authorizations().put(FakeAuthenticationHandler.PROVIDER_ID, PermissionBasedAuthorization.create(requiredPerm));

		createAuthenticationHandler()
			.addProtectedEndpoint(new EndpointOptions().setPath(path).setPermissions(Collections.singletonList(requiredPerm)))
			.handleAuthorization(rc);

		verify(rc, times(1)).next();

		assertThat(rc.user().principal().getMap())
			.as("User principal after authorization")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.singletonList(requiredPerm));
	}

	@Test
	public void testProtectedEndpointWithMissingPerms() {
		String path = "/protected";
		RoutingContext rc = mockRoutingContext(path, "");

		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		assertThat(rc.user().principal().getMap())
			.as("User principal before authorization")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.emptyList());

		String requiredPerm = "required-permission";

		createAuthenticationHandler()
			.addProtectedEndpoint(new EndpointOptions().setPath(path).setPermissions(Collections.singletonList(requiredPerm)))
			.handleAuthorization(rc);

		verify(rc, times(1)).fail(HttpResponseStatus.FORBIDDEN.code());

		assertThat(rc.user().principal().getMap())
			.as("User principal after authorization")
			.containsEntry("upgraded", false)
			.containsEntry("loggedout", false)
			.containsEntry("checkedAuthorities", Collections.singletonList(requiredPerm));
	}

	@Test
	public void testEncryptCookies() {
		SecurityOptions secOptions = config.getSecurityOptions();

		secOptions.setEncryptCookies(true);
		secOptions.setEncryptionMode(EncryptionMode.PASSWORD_ONLY_KEY);
		secOptions.setEncryptionPassword("test123");

		RoutingContext rc = mockRoutingContext("/", "");

		when(rc.user()).thenReturn(new FakePortalUser("testuser"));

		createAuthenticationHandler().handlePostAuth(rc);

		ArgumentMatcher<io.vertx.core.http.Cookie> isAuthCookie =
			c -> c.getName().equals(AuthenticationHandler.COOKIE_AUTH) && c.getValue().equals("nYVyTONqVFURjtJlnBS86g==");
		ArgumentMatcher<io.vertx.core.http.Cookie> isRefreshCookie =
			c -> c.getName().equals(FakePortalUser.COOKIE_USER_AUTHORITIES) && c.getValue().equals("SThccGMGPDc8uxT8GOoWtQ==");

		verify(rc.response()).addCookie(argThat(isAuthCookie));
		verify(rc.response()).addCookie(argThat(isRefreshCookie));
	}

	@Test
	public void testEncryptedCookies() {
		SecurityOptions secOptions = config.getSecurityOptions();

		secOptions.setEncryptCookies(true);
		secOptions.setEncryptionMode(EncryptionMode.PASSWORD_ONLY_KEY);
		secOptions.setEncryptionPassword("test123");

		RoutingContext rc = mockRoutingContext("/", "");
		UserContextInternal mockUserContext = mock(UserContextInternal.class);
		Cookie cookieMock = mock(Cookie.class);

		// Dummy JWT that will expire November 16 5138.
		when(cookieMock.getValue()).thenReturn("nYVyTONqVFURjtJlnBS86g==");
		when(rc.request().getCookie(anyString())).thenReturn(cookieMock);
		when(rc.userContext()).thenReturn(mockUserContext);

		createAuthenticationHandler().handle(rc);

		verify(mockUserContext).setUser(argThat(u -> u instanceof FakePortalUser && ((FakePortalUser) u).username().equals("testuser")));
	}

	/**
	 * Test if a request to /auth/login reroutes to /auth/login/referer/path.
	 */
	@Test
	public void testLoginReroute() {
		String referer = "/private/page.html";
		String refererParams = "?foo=bar";
		String refererWithParams = referer + refererParams;
		String loginEndpoint = config.getAuthOptions().getLoginEndpoint();

		RoutingContext rc = mockRoutingContext(loginEndpoint, refererParams);

		// Everything before the path part of the referer URL will be dropped by the sanitizer.
		when(rc.request().getHeader(eq(HttpHeaders.REFERER))).thenReturn("http://portal.domain.tld" + refererWithParams);
		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		FakeAuthenticationHandler handler = createAuthenticationHandler();

		handler.handleLogin(rc);

		verify(rc, times(1)).reroute(loginEndpoint + refererWithParams);
	}

	@Test
	public void testLoginAfterReroute() {
		String path = "/private/page.html";
		String params = "?foo=bar";
		String pathWithParams = path + params;

		RoutingContext rc = mockRoutingContext(path, params);

		when(rc.pathParam(eq(AuthenticationBaseHandler.PATH_PARAM_LOGIN_REDIRECT))).thenReturn(pathWithParams);
		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		FakeAuthenticationHandler handler = createAuthenticationHandler();

		handler.handleLogin(rc);

		verify(rc.response(), times(1))
			.setStatusCode(HttpResponseStatus.UNAUTHORIZED.code());
		verify(rc.response(), times(1))
			.putHeader(HttpHeaderNames.WWW_AUTHENTICATE, "Fake realm=dummy");
		verify(rc.response(), times(1))
			.end();
	}

	private FakeAuthenticationHandler createAuthenticationHandler() {
		LanguageVariantLoader languageVariantLoaderMock = mock(LanguageVariantLoader.class);

		when(languageVariantLoaderMock.canLoadLanguageVariant(anyString())).thenReturn(false);

		CookieProvider cookieProvider = new CookieProviderImpl(config);
		return new FakeAuthenticationHandler(config, languageVariantLoaderMock, cookieProvider, new EncryptionService(config), new ServerLoginRedirectHandler(config, cookieProvider));
	}

	private ArgumentMatcher<Cookie> cookieMatcher(String name, String value) {
		return cookie -> {
			System.out.println("Checking [actual name:" + cookie.getName() + ", value: " + cookie.getValue() + "] against [expected name: " + name + ", value: " + value + "]");

			return cookie.getName().equals(name) && cookie.getValue().equals(value);
		};
	}
}

