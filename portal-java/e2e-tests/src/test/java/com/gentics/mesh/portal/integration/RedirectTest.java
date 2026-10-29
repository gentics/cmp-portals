package com.gentics.mesh.portal.integration;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF, configProvider = RedirectTest.Config.class)
public class RedirectTest extends AbstractEndToEndTest {

	public static final String URL_NOT_FOUND_ERROR_PAGE = "/404.html";
	public static final String URL_NON_EXISTING_PAGE = "/non-existing.html";
	public static final String URL_NON_EXISTING_PDF = "/non-existing.pdf";
	public static final String URL_NON_EXISTING_DOCX = "/non-existing.docx";
	public static final String URL_NON_EXISTING_IMAGE = "/non-existing.jpg";

	@Parameters(name = "{index}: URL: {0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(
			new Object[] { URL_NON_EXISTING_PAGE },
			new Object[] { URL_NON_EXISTING_PDF },
			new Object[] { URL_NON_EXISTING_DOCX },
			new Object[] { URL_NON_EXISTING_IMAGE });
	}

	@Parameter
	public String testUrl;

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();
			Map<String, String> statusPages = portalConfig.getRedirectOptions().getStatusPages();

			statusPages.put("404", URL_NOT_FOUND_ERROR_PAGE);

			return portalConfig;
		}
	}

	@Test
	public void testRedirect() {
		String expectedBody = getNotFoundBody();
		ResponseWithBody response = getResponseWithBody(portal.portalClient().rxRequest(HttpMethod.GET, testUrl).blockingGet());

		assertThat(response.getResponse().statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.NOT_FOUND.code());

		assertThat(response.getBody())
			.as("Response body")
			.isEqualTo(expectedBody);
	}

	private String getNotFoundBody() {
		ResponseWithBody response = getResponseWithBody(portal.portalClient().rxRequest(HttpMethod.GET, URL_NOT_FOUND_ERROR_PAGE).blockingGet());

		assertThat(response.getResponse().statusCode())
			.as("Not Found page status")
			.isEqualTo(HttpResponseStatus.OK.code());

		return response.getBody();
	}
}
