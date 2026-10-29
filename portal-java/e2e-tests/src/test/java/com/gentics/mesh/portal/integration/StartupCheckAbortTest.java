package com.gentics.mesh.portal.integration;

import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.URISyntaxException;
import java.util.function.Supplier;

import org.junit.Test;

import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.StartupCheckMode;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;

@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	defaultLanguage = "de",
	configProvider = StartupCheckAbortTest.Config.class,
	testData = TestData.REF,
	startupChecks = TestContextStartupCheckMode.SHOULD_ABORT)
public class StartupCheckAbortTest extends AbstractEndToEndTest {

	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();

			String queryPath;

			try {
				queryPath = new File(getClass().getResource("/invalidQueries").toURI())
					.getAbsolutePath();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}

			portalConfig.setQueryPath(queryPath);

			portalConfig.getMonitoringOptions().setStartupCheckMode(StartupCheckMode.ABORT);

			return portalConfig;
		}
	}

	@Test
	public void test() {
		// The portal start-up is be aborted, and no client should exist.
		assertNull(portal.portalClient());
	}
}
