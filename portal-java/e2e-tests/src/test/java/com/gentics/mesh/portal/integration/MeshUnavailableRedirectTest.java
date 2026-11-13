package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.gentics.mesh.core.rest.branch.BranchCreateRequest;
import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.config.MeshAuthenticationMode;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.AuthenticationOptions;
import com.gentics.mesh.portal.config.option.DataProviderOptions;
import com.gentics.mesh.portal.config.option.EncryptionMode;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.rest.client.MeshRestClient;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpMethod;

/**
 * Test that the portal recovers after Mesh was temporarily unavailable, when status pages are configured (SUP-20246).
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF, meshProxy = true, configProvider = MeshUnavailableRedirectTest.Config.class)
public class MeshUnavailableRedirectTest extends AbstractEndToEndTest {

	public static final String URL_STATUS_PAGE = "/settings/Error_500.de.html";
	public static final String URL_PAGE = "/products/Produkte.de.html";
	public static final String PROJECT_NAME = TestData.REF.projectName();

	/**
	 * Configuration of the system where the problem was reported, as far as it can be used in this test.
	 *
	 * <p>
	 *     Not used: required Mesh plugins (not available in the test Mesh), Mesh connection settings and API keys
	 *     (set by the test context), as well as template, query, i18n and static paths (test files are used).
	 * </p>
	 */
	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();

			portalConfig.setStartPage(URL_PAGE);

			MeshOptions meshOptions = portalConfig.getMeshOptions();

			meshOptions.setProxyHeaderWhitelist(new HashSet<>(Arrays.asList("X-User-Context")));
			meshOptions.setAuthenticationMode(MeshAuthenticationMode.HEADER);
			meshOptions.setCookieForwardWhitelist(new HashSet<>(Arrays.asList("google-recaptcha-cookie", "ANDP_MT_MAPPING")));

			portalConfig.getCachingOptions().setEnabled(true);
			portalConfig.getCachingOptions().setExpireTime(3);
			portalConfig.getCachingOptions().setCustomParameters(Arrays.asList("preferred_username", "cookie_acceptCookies"));

			Map<String, String> statusPages = portalConfig.getRedirectOptions().getStatusPages();

			statusPages.put("401", "/settings/Error_401.de.html");
			statusPages.put("403", "/settings/Error_403.de.html");
			statusPages.put("404", "/settings/Error_404.de.html");
			statusPages.put("500", "/settings/Error_500.de.html");

			portalConfig.getNavigationOptions().setExpireTime(15);
			portalConfig.getNavigationOptions().setSortOrderField("navsortorder");

			DataProviderOptions dataProviderOptions = portalConfig.getDataProviderOptions();
			Map<String, List<String>> meshData = dataProviderOptions.getMeshData();

			dataProviderOptions.setRefreshTime(60);
			dataProviderOptions.setRetryOnError(true);
			meshData.put("translations_de", Arrays.asList("/settings/Translation.de.html"));
			meshData.put("translations_en", Arrays.asList("/settings/Translation.en.html"));
			meshData.put("config_en", Arrays.asList("/settings/Configuration.html"));
			meshData.put("config_de", Arrays.asList("/settings/Configuration.html"));

			AuthenticationOptions authOptions = portalConfig.getAuthOptions();

			authOptions.setLoginEndpoint("/login");
			authOptions.setLogoutEndpoint("/logout");
			authOptions.setLogoutRedirect("/logoutRedirect");
			authOptions.setUseUnauthorizedRedirect(true);
			authOptions.setUseSessionCookies(false);

			SecurityOptions securityOptions = portalConfig.getSecurityOptions();

			securityOptions.setEncryptCookies(false);
			securityOptions.setEncryptionMode(EncryptionMode.PASSWORD_ONLY_KEY);
			securityOptions.setEncryptionPassword("test-encryption-password");
			securityOptions.setCookieSameSitePolicy(CookieSameSite.NONE);
			securityOptions.getAdditionalResponseHeaders().put("X-Frame-Options", "sameorigin");
			securityOptions.getAdditionalResponseHeaders().put("X-XSS-Protection", "1");
			securityOptions.getAdditionalResponseHeaders().put("X-Content-Type-Options", "nosniff");

			return portalConfig;
		}
	}

	/**
	 * Test that a page is delivered with <em>503 Service Unavailable</em> while Mesh is unavailable, and normally
	 * again as soon as Mesh is available again.
	 * @throws InterruptedException 
	 */
	@Test
	public void testRecoveryAfterMeshUnavailable() throws InterruptedException {
		assertThat(get(URL_PAGE).getResponse().statusCode())
			.as("Response status before Mesh is unavailable")
			.isEqualTo(HttpResponseStatus.OK.code());

		portal.meshProxy().reset();
		portal.meshProxy()
			.when(HttpRequest.request())
			.respond(HttpResponse.response().withStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code()));

		Thread.sleep(portal.portalConfig().getCachingOptions().getExpireTime() * 1000);

		for (int i = 0; i < 5; i++) {
			assertThat(get(URL_PAGE).getResponse().statusCode())
				.as("Response status while Mesh is unavailable")
				.isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE.code());

			Thread.sleep(1000);
		}

		// A branch event from Mesh (via the websocket, which bypasses the proxy) makes the portal reload the
		// branches, while Mesh is still unavailable for the portal.
		MeshRestClient meshClient = portal.meshServer().client();

		meshClient.createBranch(PROJECT_NAME, new BranchCreateRequest()
			.setName("SUP-20246")
			.setLatest(false))
			.blockingAwait();

		awaitBranchesRequest();

		portal.forwardToMesh();

		assertThat(get(URL_PAGE).getResponse().statusCode())
			.as("Response status after Mesh is available again")
			.isEqualTo(HttpResponseStatus.OK.code());

		assertThat(get(URL_STATUS_PAGE).getResponse().statusCode())
			.as("Status page response status after Mesh is available again")
			.isEqualTo(HttpResponseStatus.OK.code());
	}

	/**
	 * Wait until the portal requested the branches via the Mesh proxy.
	 */
	private void awaitBranchesRequest() throws InterruptedException {
		HttpRequest branchesRequest = HttpRequest.request("/api/v1/" + PROJECT_NAME + "/branches").withMethod("GET");

		for (int i = 0; i < 30; i++) {
			if (portal.meshProxy().retrieveRecordedRequests(branchesRequest).length > 0) {
				return;
			}

			Thread.sleep(1000);
		}

		fail("The portal did not reload the branches after the branch was updated in Mesh");
	}

	private ResponseWithBody get(String url) {
		return getResponseWithBody(portal.portalClient().rxRequest(HttpMethod.GET, url).blockingGet());
	}
}
