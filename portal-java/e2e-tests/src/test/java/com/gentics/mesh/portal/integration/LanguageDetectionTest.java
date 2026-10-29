package com.gentics.mesh.portal.integration;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.reactivex.core.http.HttpClientRequest;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "en", testData = TestData.REF)
public class LanguageDetectionTest extends AbstractEndToEndTest {

	@Test
	public void testPortalApplicationNonDefault() {
		loadEchoLanguageApp("de", null);
	}

	@Test
	public void testPortalApplicationDefaultLanguage() {
		loadEchoLanguageApp("en", null);
	}

	@Test
	public void testPortalApplicationCookie() {
		loadEchoLanguageApp("de", "fr");
	}

	private void loadEchoLanguageApp(String lang, String langCookie) {
		String path = String.format("/applications/Echo_Language.%s.html", lang);
		RequestOptions options = new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI(path);
		String expected;

		if (StringUtils.isNotBlank(langCookie)) {
			options.putHeader("Cookie", String.format("%s=%s; path=/", Constants.COOKIE_LANG, langCookie));
			expected = langCookie;
		} else {
			expected = lang;
		}

		HttpClientRequest request = portal.portalClient().rxRequest(options).blockingGet();
		ResponseWithBody response = getResponseWithBody(request);

		assertThat(response.getResponse().statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		assertThat(response.getBody())
			.as("Detected language")
			.isEqualTo(expected);

	}
}
