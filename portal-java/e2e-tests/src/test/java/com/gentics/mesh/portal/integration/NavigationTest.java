package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.junit.Test;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF)
public class NavigationTest extends AbstractEndToEndTest {
	@Test
	public void testNavigation() {
		ResponseWithBody response = getResponseWithBody(portal.portalClient().rxRequest(HttpMethod.GET, "/products/navtest.html").blockingGet());

		assertThat(response.getResponse().statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		assertThat(response.getBody()).isEqualTo(loadResource("/assertions/navtest.html"));
	}
}
