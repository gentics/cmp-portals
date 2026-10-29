package com.gentics.mesh.portal.content;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.server.CookieProviderImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(Parameterized.class)
public class LanguageSwitchRedirectTest extends AbstractPortalTest {

	@Parameters(name = "{index}: redirect: {0} sanitized redirect: {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(
			new Object[] { "", "" },
			new Object[] { "http://evil.domain/bad", "/bad" },
			new Object[] { "https://evil.domain/bad", "/bad" },
			new Object[] { "http://evil.domain/", "/" },
			new Object[] { "https://evil.domain", "/evil.domain" },
			new Object[] { "//evil.domain/bad", "/bad" },
			new Object[] { "//evil.domain/", "/" },
			new Object[] { "//evil.domain", "/evil.domain" },
			new Object[] { "/\\evil.domain/bad", "/bad" },
			new Object[] { "/\\evil.domain/", "/" },
			new Object[] { "/\\evil.domain", "/evil.domain" },
			new Object[] { "evil.domain/bad", "/bad" },
			new Object[] { "evil.domain/", "/" },
			new Object[] { "evil.domain", "/evil.domain" },
			new Object[] { "/seems/ok\r\nX-Is-Bad: maybe", "/seems/okX-Is-Bad: maybe"},
			new Object[] { "///evil.domain/bad", "/bad" },
			new Object[] { "////evil.domain/bad", "/bad" },
			new Object[] { "/////evil.domain/bad", "/bad" },
			new Object[] { "//evil.domain//bad", "/bad" },
			new Object[] { "//evil.domain\\\\bad", "/bad" },
			new Object[] { "//evil.domain///bad//worse", "/bad/worse" },
			new Object[] { "//evil.domain////bad", "/bad" },
			new Object[] { "//evil.domain/////bad", "/bad" },
			new Object[] { "//evil.domain//\\//bad", "/bad" },
			new Object[] { "//evil.domain/\r\n//bad", "/bad" },
			new Object[] { "//evil.domain/\r\n\r/\\bad", "/bad" },
			new Object[] { "//evil.domain/\r\n\r/\\bad/\r/\n\\worse", "/bad/worse" });
	}

	@Parameter
	public String redirect;

	@Parameter(1)
	public String sanitized;

	@Test
	public void testLanguageSwitch() {
		RoutingContext rc = mockRoutingContext("/api/language", String.format("lang=de&redirect=%s", redirect));

		new ContentLanguageHandler(new CookieProviderImpl(config)).switchLanguage(rc);

		int expectedStatus;

		if (StringUtils.isBlank(redirect)) {
			expectedStatus = HttpResponseStatus.OK.code();
		} else {
			expectedStatus = HttpResponseStatus.SEE_OTHER.code();
			verify(rc.response()).putHeader(eq(HttpHeaders.LOCATION), eq(sanitized));
		}

		verify(rc.response()).setStatusCode(expectedStatus);
	}
}
