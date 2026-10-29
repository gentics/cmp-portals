package com.gentics.mesh.portal.integration;

import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapParser;
import crawlercommons.sitemaps.SiteMapURL;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.reactivex.core.http.HttpClientResponse;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.ext.web.multipart.MultipartForm;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@PortalTestSetting(
	meshImage = "docker.gentics.com/gentics/mesh-hsqldb",
	defaultLanguage = "de",
	testData = TestData.REF,
	startupChecks = TestContextStartupCheckMode.SHOULD_START)
public class PortalMeshIntegrationTest extends AbstractEndToEndTest {

	private static final String STARTPAGE_URL = "/start.de.html";
	private static final String BINARY_URL = "/products/Svg_example4.svg";

	@Test
	public void testLoadContent() {
		HttpClientResponse response = request(portal.portalClient(), STARTPAGE_URL);

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());
	}

	@Test
	public void testPageNotFound() {
		HttpClientResponse response = request(portal.portalClient(), "/non-existant/page.html");

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.NOT_FOUND.code());
	}

	@Test
	public void testFileNotFound() {
		HttpClientResponse response = request(portal.portalClient(), "/non-existant/image.jpg");

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.NOT_FOUND.code());
	}

	@Test
	public void testLoadBinary() {
		HttpClientResponse response = request(portal.portalClient(), BINARY_URL);

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());
	}

	@Test
	public void testSitemap() throws Exception {
		URL sitemapUrl = new URL("http", "localhost", portal.portalPort(), "/sitemap.xml");
		SiteMap siteMap = (SiteMap) new SiteMapParser(false).parseSiteMap(sitemapUrl);

		Condition<SiteMapURL> startPage = new Condition<>(url ->
			url.getLastModified() != null &&
			url.getUrl().getPath().equals(STARTPAGE_URL),
		"Is start page");

		assertThat(siteMap.getSiteMapUrls()).haveExactly(1, startPage);
	}

	@Test
	public void testProxyHandler() {
		WebClient client = WebClient.wrap(portal.portalClient());
		String token = portal.meshServer().client().getAuthentication().getToken();
		HttpResponse<String> response = client.post("/api/proxytest/nodes/a22f189738be11e8904c005056bd2479/binary/binarycontent")
			.as(BodyCodec.string())
			.bearerTokenAuthentication(token)
			.rxSendMultipartForm(MultipartForm.create()
				.attribute("language", "en")
				.attribute("version", "draft")
				.binaryFileUpload("binary", "Svg_example4.svg", getResourcePath("/setup/REF/binaries/Svg_example4.updated.svg"), "image/svg+xml"))
			.blockingGet();

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(JsonUtil.readValue(response.body(), NodeResponse.class)).isNotNull();
	}
}
