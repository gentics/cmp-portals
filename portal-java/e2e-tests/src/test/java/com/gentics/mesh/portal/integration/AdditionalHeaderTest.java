package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Test;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;

import io.vertx.reactivex.core.http.HttpClientResponse;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF, configProvider = AdditionalHeaderTest.Config.class)
public class AdditionalHeaderTest extends AbstractEndToEndTest {

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();

			portalConfig.getSecurityOptions()
				.getAdditionalResponseHeaders()
				.put("Content-Security-Policy", "default-src 'self'");
			return portalConfig;
		}
	}

	@Test
	public void testContentPolicyHeader() {
		HttpClientResponse response = request(portal.portalClient(), "/start.de.html");

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'");
	}
}
