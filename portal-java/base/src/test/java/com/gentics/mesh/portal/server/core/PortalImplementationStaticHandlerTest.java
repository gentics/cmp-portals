package com.gentics.mesh.portal.server.core;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.server.dagger.PortalModule;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

/**
 * Test cases for serving the static files of the portal implementation together with the static files of the portal
 * instance, using the handlers like the {@link PortalHttpVerticle}.
 */
public class PortalImplementationStaticHandlerTest {

	/** The static handler only accepts relative paths. */
	private static final Path BASE_DIR = Paths.get("target", "implementation-static-test");

	private Path instanceDir;

	private Path implementationDir;

	private Vertx vertx;

	private HttpServer server;

	private HttpClient client;

	private PortalImplementationStaticHandler implementationHandler;

	@Before
	public void setup() throws IOException {
		Path dir = BASE_DIR.resolve(UUID.randomUUID().toString());

		// Not using the paths returned by createDirectories(), which may be absolute
		instanceDir = dir.resolve("static");
		implementationDir = dir.resolve("implementation");
		Files.createDirectories(instanceDir);
		Files.createDirectories(implementationDir);

		PortalConfig config = new PortalConfig();

		config.getStaticHandlerOptions().setPath(instanceDir.toString());
		config.getStaticHandlerOptions().setImplementationPath(implementationDir.toString());
		config.getStaticHandlerOptions().setCaching(true);

		vertx = Vertx.vertx();
		implementationHandler = new PortalImplementationStaticHandler(config);

		Router router = Router.router(vertx);

		router.route("/static/*")
			.handler(implementationHandler)
			.handler(PortalModule.staticHandler(config));

		server = vertx.createHttpServer().requestHandler(router).listen(0).await();
		client = vertx.createHttpClient();
	}

	@After
	public void tearDown() throws Exception {
		vertx.close().await();
		FileUtils.deleteDirectory(BASE_DIR.toFile());
	}

	/**
	 * Test that files existing only in the static folder of the portal instance are served.
	 */
	@Test
	public void testInstanceFileServed() throws IOException {
		Files.writeString(instanceDir.resolve("instance.css"), "instance");

		assertResponse("/static/instance.css", 200, "instance");
	}

	/**
	 * Test that files existing only in the static folder of the portal implementation are served, also in subfolders.
	 */
	@Test
	public void testImplementationFileServed() throws IOException {
		Files.writeString(Files.createDirectories(implementationDir.resolve("css")).resolve("impl.css"), "implementation");

		assertResponse("/static/css/impl.css", 200, "implementation");
	}

	/**
	 * Test that the file of the portal implementation is served, when both folders contain the same path.
	 */
	@Test
	public void testImplementationWins() throws IOException {
		Files.writeString(instanceDir.resolve("shared.css"), "instance");
		Files.writeString(implementationDir.resolve("shared.css"), "implementation");

		assertResponse("/static/shared.css", 200, "implementation");
	}

	/**
	 * Test that files existing in neither folder are not found.
	 */
	@Test
	public void testMissingFileNotFound() {
		assertResponse("/static/missing.css", 404, null);
	}

	/**
	 * Test that a file added to the portal implementation is served after rebuilding the handler, although the
	 * missing file was cached before.
	 */
	@Test
	public void testRebuildAfterUpdate() throws IOException {
		Files.writeString(instanceDir.resolve("shared.css"), "instance");

		assertResponse("/static/shared.css", 200, "instance");

		Files.writeString(implementationDir.resolve("shared.css"), "implementation");

		assertResponse("/static/shared.css", 200, "instance");

		implementationHandler.rebuild();

		assertResponse("/static/shared.css", 200, "implementation");
	}

	private void assertResponse(String uri, int expectedStatus, String expectedBody) {
		// Read the body in the same chain, so that no data arrives before the body handler is set
		Map.Entry<Integer, String> response = client.request(HttpMethod.GET, server.actualPort(), "localhost", uri)
			.compose(HttpClientRequest::send)
			.compose(rs -> rs.body().map(body -> Map.entry(rs.statusCode(), body.toString())))
			.await();
		String body = response.getValue();

		assertEquals("Status of " + uri, expectedStatus, (int) response.getKey());

		if (expectedBody != null) {
			assertEquals("Body of " + uri, expectedBody, body);
		}
	}
}
