package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.server.PortalTestContext;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.google.common.net.HttpHeaders;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.Completable;
import io.vertx.reactivex.core.http.HttpClientResponse;

@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF)
public class CachingHeadersTest extends AbstractEndToEndTest {

	private final String BINARY_PATH = "/products/Svg_example4.svg";

	@Test
	public void testPageEtag() {
		NodeUpdateRequest updateRequest = loadRestModel("/setup/REF/nodes-level-0/start.de.json", NodeUpdateRequest.class);
		updateRequest.getFields().putString("creator", "Philipp Gürtler");
		Completable changeRequest = portal.meshServer().client().updateNode("Reference", "b3efa88f641011e88438005056a9ea60", updateRequest)
			.toSingle().flatMapCompletable(this::publishNode);
		testEtag("/start.en.html", changeRequest);
	}

	@Test
	public void testBinaryEtag() throws Exception {
		File binaryFile = new File(PortalTestContext.class.getResource("/setup/REF/binaries/Svg_example4.updated.svg").toURI());
		MeshRestClient meshRestClient = portal.meshServer().client();
		Completable updateRequest = meshRestClient.updateNodeBinaryField("Reference",
			"a22f189738be11e8904c005056bd2479",
			"en",
			"draft",
			"binarycontent",
			new FileInputStream(binaryFile),
			binaryFile.length(),
			"Svg_example4.svg",
			"image/svg+xml"
		).toSingle().flatMapCompletable(this::publishNode);
		testEtag(BINARY_PATH, updateRequest);
	}

	@Test
	public void testBinaryMaxAge() {
		HttpClientResponse response = request(portal.portalClient(), BINARY_PATH);
		String maxAge = String.format("max-age=%d", portal.portalConfig().getCachingOptions().getBinaryMaxAge());

		assertThat(response.statusCode())
			.as("Response status")
			.isEqualTo(HttpResponseStatus.OK.code());

		assertThat(response.headers().get(HttpHeaders.CACHE_CONTROL))
			.as("Cache-Control header")
			.isEqualTo(maxAge);
	}

	private Completable publishNode(NodeResponse response) {
		return portal.meshServer().client().publishNode("Reference", response.getUuid()).toCompletable();
	}

	private void testEtag(String path, Completable changeRequest) {
		String etag = getEtag(path, 200);
		expectNotChanged(path, etag);

		changeRequest.blockingAwait();

		// TODO: This test could be glitchy because it is possible that the cache is not cleared before the next request.

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		etag = getEtag(path, 200, etag);
		expectNotChanged(path, etag);
	}

	private void expectNotChanged(String path, String etag) {
		HttpClientResponse response = request(portal.portalClient(), path, "If-None-Match", etag);

		assertThat(response.statusCode()).isEqualTo(304);
	}

	private String getEtag(String path, int expectedStatus) {
		return getEtag(path, expectedStatus, null);
	}

	private String getEtag(String path, int expectedStatus, String previousEtag) {
		HttpClientResponse response = previousEtag == null
			? request(portal.portalClient(), path)
			: request(portal.portalClient(), path, "If-None-Match", previousEtag);

		assertThat(response.statusCode()).isEqualTo(expectedStatus);

		String etag = response.getHeader("etag");

		assertThat(etag).isNotNull();
		return etag;
	}
}
