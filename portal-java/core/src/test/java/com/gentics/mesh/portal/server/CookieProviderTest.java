package com.gentics.mesh.portal.server;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.server.CookieProvider;
import io.vertx.core.http.Cookie;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for cookie creation via CookieProvider.
 */
@RunWith(Parameterized.class)
public class CookieProviderTest extends AbstractPortalTest {

	private static final String COOKIE_BASE_NAME = AuthenticationHandler.COOKIE_AUTH;
	private static final String ATTR_PATTERN_FMT = "(?i).*%s\\b.*";
	private static final int NO_MAX_AGE = -1;

	@Parameters(name = "{index} secure: {0}, prefix: {1}, expectedName: {2}")
	public static Collection<Object[]> data() {
		Collection<Object[]> data = new ArrayList<>();

		for (boolean secure : new boolean[] { false, true }) {
			for (boolean prefix : new boolean[] { false, true }) {
				data.add(new Object[] {
					secure,
					prefix,
					secure && prefix ? "__Host-" + COOKIE_BASE_NAME : COOKIE_BASE_NAME
				});
			}
		}

		return data;
	}

	@Parameter
	public boolean secure;

	@Parameter(1)
	public boolean prefix;

	@Parameter(2)
	public String realCookieName;

	@Before
	public void setup() throws Exception {
		super.setup();

		config.getSecurityOptions().setUseHostCookiePrefix(prefix);
		config.setServerUrl(new URL(String.format("http%s://test:8000/", secure ? "s" : "")));
	}

	@Test
	public void testCookieCreation() {
		String cookieValue = "cookie-value";

		checkCookie(
			new CookieProviderImpl(config).create(COOKIE_BASE_NAME, cookieValue),
			cookieValue,
			NO_MAX_AGE);
	}

	@Test
	public void testCookieDeletion() {
		checkCookie(
			new CookieProviderImpl(config).delete(COOKIE_BASE_NAME),
			CookieProvider.DELETED_COOKIE_VALUE,
			0);
	}

	private void checkCookie(Cookie cookie, String value, int maxAge) {
		System.out.println("Checking cookie: " + cookie.encode());

		assertThat(cookie.getName())
			.as("Created cookie name")
			.isEqualTo(realCookieName);

		assertThat(cookie.getValue())
			.as("Created cookie value")
			.isEqualTo(value);

		assertThat(cookie.getPath())
			.as("Created cookie path")
			.isEqualTo("/");

		assertThat(cookie.getDomain())
			.as("Created cookie domain")
			.isNull();

		// There are no getters for the following attributes for some reason.
		String encodedCookie = cookie.encode();

		if (maxAge == NO_MAX_AGE) {
			assertThat(encodedCookie)
				.as("Cookie has no max age")
				.doesNotMatch(String.format(ATTR_PATTERN_FMT, "Max-Age"));
		} else {
			assertThat(encodedCookie)
				.as("Cookie has max age")
				.matches(String.format(ATTR_PATTERN_FMT, "Max-Age=" + maxAge));
		}

		assertThat(encodedCookie)
			.as("Cookie is SameSite=STRICT")
			.matches(String.format(ATTR_PATTERN_FMT, "SameSite=STRICT"));

		assertThat(encodedCookie)
			.as("Cookie is HTTPOnly")
			.matches(String.format(ATTR_PATTERN_FMT, "HTTPOnly"));

		if (secure) {
			assertThat(encodedCookie)
				.as("Cookie is secure")
				.matches(String.format(ATTR_PATTERN_FMT, "Secure"));
		} else {
			assertThat(encodedCookie)
				.as("Cookie is not secure")
				.doesNotMatch(String.format(ATTR_PATTERN_FMT, "Secure"));
		}
	}
}
