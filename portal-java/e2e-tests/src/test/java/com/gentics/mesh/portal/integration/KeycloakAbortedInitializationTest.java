package com.gentics.mesh.portal.integration;

import static org.junit.Assert.assertFalse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.gentics.testcontainers.KeycloakContainer;
import org.junit.Test;

import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.server.TestBootstrapInitializer;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "en", testData = TestData.REF, 
	keycloakConfig = "/keycloak/realm.json", startKeycloak = false, keycloakInitRetryPeriodSeconds = 0)
public class KeycloakAbortedInitializationTest extends AbstractEndToEndTest {

	@Test
	public void testKeycloakStartAborted() throws InterruptedException {
		PortalTestSetting testSetting = getClass().getAnnotation(PortalTestSetting.class);
		KeycloakContainer keycloak = portal.keycloak();
		keycloak.start();
		portal.portalConfig().getAuthOptions().getKeycloakOptions().setRealmUrl("http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "" + "/auth/realms/" + testSetting.keycloakRealmName());
		keycloak.waitStartup();
		CountDownLatch waitForReady = new CountDownLatch(1);
		Executors.newFixedThreadPool(1).execute(() -> {
			while (!((TestBootstrapInitializer) portal.portalServerComponent().boot()).getKeycloakAuthenticationHandler().isReady()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					throw new IllegalStateException(e);
				}
			}
			waitForReady.countDown();
		});
		assertFalse("KeycloakAuthenticationHandler should be not ready", waitForReady.await(30,TimeUnit.SECONDS));
		assertFalse("Portal | Java should not be running", ((TestBootstrapInitializer) portal.portalServerComponent().boot()).isRunning());
	}
}
