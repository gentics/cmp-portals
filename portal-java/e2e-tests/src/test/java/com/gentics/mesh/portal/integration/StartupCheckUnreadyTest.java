package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.StartupCheckMode;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tests that the portal starts up even when start-up checks fail (invalid
 * query path).
 *
 * <p>
 *     Requests to the ready endpoint must respond with "503 Service Unavailable",
 *     and requests for the start page must return "500 Internal Server Error".
 * </p>
 */
@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	defaultLanguage = "de",
	configProvider = StartupCheckUnreadyTest.Config.class,
	startupChecks = TestContextStartupCheckMode.SHOULD_START,
	startupCheckDelay = StartupCheckUnreadyTest.STARTUP_CHECK_DELAY,
	testData = TestData.REF)
public class StartupCheckUnreadyTest extends AbstractEndToEndTest {

	public static final int STARTUP_CHECK_DELAY = 10;

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig config = new DefaultPortalConfig().get();
			String invalidQueryPath;

			try {
				invalidQueryPath = new File(getClass().getResource("/invalidQueries").toURI()).getAbsolutePath();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}

			config.setNetworkTimeout(5);
			config.getCachingOptions().setEnabled(false);
			config.setQueryPath(invalidQueryPath);
			config.getMonitoringOptions().setStartupCheckMode(StartupCheckMode.UNREADY);

			return config;
		}
	}

	private static final String URL_HEALTH_READY = "/health/ready";
	private static final String URL_STARTPAGE = "/start.de.html";

	/**
	 * Verify the ready endpoints reports "503 Service Unavailable".
	 */
	@Test
	public void testReadyEndpoint() throws InterruptedException {
		HttpClientResponse response = request(portal.monitoringClient(), URL_HEALTH_READY);

		// Start-up checks are delayed by 10 seconds, so the first ready check
		// should return 200 OK.
		assertThat(response.statusCode())
			.as("Ready response status before checks")
			.isEqualTo(HttpResponseStatus.OK.code());

		// Wait for the start-up checks to run.
		if (!portal.startupChecksLatch().await(Math.round(STARTUP_CHECK_DELAY * 1.5f), TimeUnit.SECONDS)) {
			throw new RuntimeException("Start-up checks did not finish in time");
		}

		response = request(portal.monitoringClient(), URL_HEALTH_READY);

		assertThat(response.statusCode())
			.as("Ready response status after checks")
			.isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
	}

	/**
	 * Verify requesting contents leads to "500 Internal Server Error" response.
	 */
	@Test
	public void testLoadStartpage() {
		HttpClientResponse response = request(portal.portalClient(), URL_STARTPAGE);

		assertThat(response.statusCode())
			.as("Content response status")
			.isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
	}
}
