package com.gentics.mesh.portal.integration.content;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.google.common.net.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF)
public class ResponseHeaderTest extends AbstractEndToEndTest {

	private static final String SVG_EXAMPLE_IMAGE = "/products/Svg_example4.svg";

	@Test
	public void testRangeRequest() {
		HttpClientResponse response = request(portal.portalClient(), SVG_EXAMPLE_IMAGE, "Range", "bytes=0-7");

		assertThat(response.statusCode())
			.as("Response status code")
			.isEqualTo(HttpResponseStatus.PARTIAL_CONTENT.code());
		assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH))
			.as("Response Content-Length")
			.isEqualTo("8");
		assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE))
			.as("Response range")
			.startsWith("bytes 0-7/");
	}

	@Test
	public void testXPoweredBy() {
		HttpClientResponse response = request(portal.portalClient(), SVG_EXAMPLE_IMAGE);

		assertThat(response.getHeader("X-Powered-By"))
			.as("X-Powered-By header")
			.isEqualTo("gentics.com");
	}
}
