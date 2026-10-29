package com.gentics.mesh.portal.handler;

import com.gentics.mesh.portal.AbstractPortalTest;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class SecurityHandlerTest extends AbstractPortalTest {

	private enum ApiKeyMode {
		MISSING_KEY(null, false, false),
		WRONG_KEY_BODY("wrong", false, false),
		WRONG_KEY_HEADER("wrong", true, false),
		CORRECT_KEY_BODY("correct", false, true),
		CORRECT_KEY_HEADER("correct", true, true);

		private final String key;
		private final boolean useHeader;
		private final boolean isCorrect;

		ApiKeyMode(String key, boolean useHeader, boolean isCorrect) {
			this.key = key;
			this.useHeader = useHeader;
			this.isCorrect = isCorrect;
		}

		public boolean isCorrect() {
			return isCorrect;
		}

		public boolean useHeader() {
			return useHeader;
		}

		public String key() {
			return key;
		}
	}

	@Parameters(name = "{index} allowed IPs: {0}, request IP: {1}, API key required: {2} provided API key: {3}, expect success: {4}")
	public static Collection<Object[]> data() {
		Collection<Object[]> data = new ArrayList<>();
		List<Object[]> addressOnlyData = Arrays.asList(
			new Object[] { "", "127.0.0.1", false },
			new Object[] { "127.0.0.1", "127.0.0.1", true },
			new Object[] { "127.0.0.1", "127.0.0.2", false },
			new Object[] { "127.0.0.1/24", "127.0.0.2", true },
			new Object[] { "localhost", "127.0.0.1", true },
			new Object[] { "localhost", "127.0.0.2", false },
			new Object[] { "127.0.0.1", "localhost", true },
			new Object[] { "127.0.0.0/24", "localhost", true },
			new Object[] { "127.0.0.0/24", "www.gentics.com", false },
			new Object[] { "167.235.218.64/32", "www.gentics.com", true },
			new Object[] { "167.235.218.0/24", "www.gentics.com", true },
			new Object[] { "194.232.44.25", "localhost", false },
			new Object[] { "127.0.0.1", "unknown-host", false },
			new Object[] { "0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:1", true },
			new Object[] { "0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:2", false },
			new Object[] { "::1", "0:0:0:0:0:0:0:1", true },
			new Object[] { "0:0:0:0:0:0:0:1/128", "0:0:0:0:0:0:0:1", true },
			new Object[] { "::1/128", "0:0:0:0:0:0:0:2", false },
			new Object[] { "::0/127", "0:0:0:0:0:0:0:0", true },
			new Object[] { "::0/127", "0:0:0:0:0:0:0:1", true },
			new Object[] { "::1/128", "::2", false },
			new Object[] { "::1/126", "0:0:0:0:0:0:0:2", true },
			new Object[] { "::1/126", "::2", true },
			new Object[] { "fc00::/7", "fd01:2345:6789:abcd:0:0:0:1", true },
			new Object[] { "fc00::/7", "fd01:2345:6789:abcd::1", true });

		for (Object[] addressOnly : addressOnlyData) {
			for (ApiKeyMode providedKey : ApiKeyMode.values()) {
				boolean addressOkay = (boolean) addressOnly[2];

				data.add(new Object[] {
					addressOnly[0],
					addressOnly[1],
					false,
					providedKey,
					addressOkay && providedKey == ApiKeyMode.MISSING_KEY
				});

				boolean noAllowedIps = ((String) addressOnly[0]).isEmpty();

				data.add(new Object[] {
					addressOnly[0],
					addressOnly[1],
					true,
					providedKey,
					// When the API key is required, and the allowed IP list is
					// empty, the request should be allowed.
					(addressOkay || noAllowedIps) && providedKey.isCorrect()
				});
			}
		}

		return data;
	}

	@Parameter
	public String allowedIp;

	@Parameter(1)
	public String requestIp;

	@Parameter(2)
	public boolean keyRequired;

	@Parameter(3)
	public ApiKeyMode providedKey;

	@Parameter(4)
	public boolean expectSuccess;

	@Before
	@Override
	public void setup() throws Exception {
		super.setup();

		SecurityOptions securityOptions = config.getSecurityOptions();

		securityOptions.setApiKey(keyRequired ? ApiKeyMode.CORRECT_KEY_BODY.key() : null);

		securityOptions.getAllowedIps().clear();

		if (StringUtils.isNotBlank(allowedIp)) {
			securityOptions.getAllowedIps().add(allowedIp);
		}
	}

	@Test
	public void testSecurityHandler() {
		RoutingContext rc = mockRoutingContext("/", "");
		SocketAddress addressMock = mock(SocketAddress.class);

		when(addressMock.host()).thenReturn(requestIp);
		when(rc.request().remoteAddress()).thenReturn(addressMock);

		if (providedKey != ApiKeyMode.MISSING_KEY) {
			if (providedKey.useHeader()) {
				when(rc.request().getHeader(SecurityHandler.HEADER_API_KEY)).thenReturn(providedKey.key());
			} else {
				var mockBody = mock(RequestBody.class);

				when(mockBody.asJsonObject()).thenReturn(new JsonObject().put("key", providedKey.key()));
				when(rc.body()).thenReturn(mockBody);
			}
		}

		new SecurityHandler(config).handle(rc);

		if (expectSuccess) {
			verify(rc, times(1)).next();
			verify(rc, never()).response();
		} else {
			verify(rc, never()).next();
			verify(rc.response(), times(1)).setStatusCode(HttpResponseStatus.FORBIDDEN.code());
			verify(rc.response(), times(1)).end();
		}
	}
}
