package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.TestRoutingContext;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.option.SingleSignOutOptions;
import com.gentics.mesh.portal.server.CookieProviderImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentMatcher;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class LogoutAllHandlerTest extends AbstractPortalTest {

	@Parameters(name = "{index}: start index: {0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(
			new Object[] { 0 },
			new Object[] { 1 },
			new Object[] { 2 },
			new Object[] { PORTALS.size() },
			new Object[] { INVALID_START_PARAMETER });
	}

	@Parameter
	public int startIdx;

	private static final int NO_START_PARAMETER = -1;
	private static final int INVALID_START_PARAMETER = -2;

	private static final List<String> PORTALS = Arrays.asList(
		"http://main-portal",
		"http://second-portal",
		"http://third-portal-with-slash/");

	private Cookie dummyAuthCookie;
	private SingleSignOutOptions options;

	@Before
	@Override
	public void setup() throws Exception {
		super.setup();

		dummyAuthCookie = Cookie.cookie(AuthenticationHandler.COOKIE_AUTH, "dummy");
		options = config.getAuthOptions().getSingleSignOutOptions();
		options.getExternalPortals().clear();
	}

	@Test
	public void testLogoutAllStart() throws MalformedURLException {
		int fixedStartIdx = startIdx < 0 || startIdx >= PORTALS.size() ? 0 : startIdx;
		config.setServerUrl(new URL(PORTALS.get(fixedStartIdx)));
		options.getExternalPortals().addAll(PORTALS);

		RoutingContext rc = createRoutingContext(NO_START_PARAMETER);

		// TODO mock AbstractAuthHandler and just check if finishLogout() is called with the correct arguments?
		new LogoutAllHandler(config, new CookieProviderImpl(config)).handle(rc);

		String expectedRedirect = String.format(
			"%s%s?start=%d",
			StringUtils.removeEnd(PORTALS.get((fixedStartIdx + 1) % PORTALS.size()), "/"),
			options.getSingleSignOutPath(),
			fixedStartIdx);


		// When starting the logout all process, the first response will always be 303 See Other.
		checkResponse(rc, HttpResponseStatus.SEE_OTHER.code(), expectedRedirect, false);
	}

	@Test
	public void testLogoutAllIntermediate() throws MalformedURLException {
		config.setServerUrl(new URL(PORTALS.get(0)));
		options.getExternalPortals().addAll(PORTALS);

		RoutingContext rc = createRoutingContext(startIdx);

		// TODO mock AbstractAuthHandler and just check if finishLogout() is called with the correct arguments?
		new LogoutAllHandler(config, new CookieProviderImpl(config)).handle(rc);

		String expectedRedirect;

		boolean lastRequest = startIdx == 0;

		if (lastRequest) {
			expectedRedirect = config.getAuthOptions().getLogoutEndpoint();
		} else {
			expectedRedirect = String.format(
				"%s%s?start=%d",
				StringUtils.removeEnd(PORTALS.get(1), "/"),
				options.getSingleSignOutPath(),
				startIdx);
		}

		int expectedStatus = startIdx < 0 || startIdx >= PORTALS.size()
			? HttpResponseStatus.BAD_REQUEST.code()
			: HttpResponseStatus.SEE_OTHER.code();

		checkResponse(rc, expectedStatus, expectedRedirect, !lastRequest);
	}

	private RoutingContext createRoutingContext(int startIdx) {
		String query;

		switch (startIdx) {
		case NO_START_PARAMETER:
			query = "";
			break;

		case INVALID_START_PARAMETER:
			query="?start=not-a-number";
			break;

		default:
			query = "?start=" + startIdx;
		}

		TestRoutingContext rc = mockRoutingContext(options.getSingleSignOutPath(), query);

		when(rc.request().getCookie(eq(AuthenticationHandler.COOKIE_AUTH))).thenReturn(dummyAuthCookie);
		when(rc.user()).thenReturn(FakePortalUser.defaultUserInstance());

		return rc;
	}

	private void checkResponse(RoutingContext rc, int expectedStatus, String expectedRedirect, boolean deleteCookies) {
		HttpServerResponse response = rc.response();

		verify(response).setStatusCode(expectedStatus);

		if (expectedStatus == HttpResponseStatus.BAD_REQUEST.code()) {
			return;
		}

		if (expectedRedirect != null) {
			verify(response).putHeader(HttpHeaders.LOCATION, expectedRedirect);
		}

		if (deleteCookies) {
			verify(rc.response(), times(1)).addCookie(argThat(logoutCookieMatcher(AuthenticationHandler.COOKIE_AUTH)));
			verify(rc.response(), times(1)).addCookie(argThat(logoutCookieMatcher(FakePortalUser.COOKIE_USER_AUTHORITIES)));
		}
	}

	private ArgumentMatcher<Cookie> logoutCookieMatcher(String name) {
		return cookie -> cookie.getName().equals(name) && cookie.getValue().equals(CookieProvider.DELETED_COOKIE_VALUE);
	}
}
