package com.gentics.mesh.portal.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.field.impl.StringFieldImpl;
import com.gentics.mesh.core.rest.schema.impl.BinaryFieldSchemaImpl;
import com.gentics.mesh.core.rest.schema.impl.SchemaCreateRequest;
import com.gentics.mesh.core.rest.schema.impl.SchemaReferenceImpl;
import com.gentics.mesh.core.rest.schema.impl.StringFieldSchemaImpl;
import com.gentics.mesh.importer.helper.ImportUtils;
import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.ResponseWithBody;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.api.PortalEvents;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.implementation.PortalImplementationService;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshWebsocket;

import io.vertx.core.http.HttpMethod;

/**
 * Test loading the portal implementation from Mesh on startup and during runtime.
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", defaultLanguage = "de", testData = TestData.REF, configProvider = PortalImplementationTest.Config.class)
public class PortalImplementationTest extends AbstractEndToEndTest {

	public static final String RUNTIME_KEYWORD = "runtime-impl";

	public static final String STARTUP_KEYWORD = "startup-impl";

	public static final Path BASE_DIR = Paths.get("target", "portal-implementation-test");

	private static final long TIMEOUT_MS = 30_000;

	private static final Queue<String> updateEvents = new ConcurrentLinkedQueue<>();

	/**
	 * Portal configuration using the portal implementation {@link #RUNTIME_KEYWORD} and separate directories, so
	 * that no checked in test resources are overwritten.
	 */
	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			return configure(new DefaultPortalConfig().get(), RUNTIME_KEYWORD);
		}
	}

	private static PortalConfig configure(PortalConfig config, String keyword) {
		Path dir = BASE_DIR.resolve(keyword);

		config.setPortalImplementation(keyword);
		config.setTemplatesPath(dir.resolve("templates").toString());
		config.setQueryPath(dir.resolve("queries").toString());
		config.setI18nPath(dir.resolve("i18n").toString());
		config.getStaticHandlerOptions().setPath(dir.resolve("static").toString());
		config.getStaticHandlerOptions().setImplementationPath(dir.resolve("implementation-static").toString());

		return config;
	}

	@BeforeClass
	public static void setupSchema() {
		MeshRestClient client = portal.meshServer().client();
		String project = TestData.REF.projectName();
		SchemaCreateRequest schema = new SchemaCreateRequest();

		schema.setName(TestData.REF.schemaPrefix() + "_implementation");
		schema.setDisplayField("name");
		schema.setContainer(false);
		schema.addField(new StringFieldSchemaImpl().setName("keyword"));
		schema.addField(new StringFieldSchemaImpl().setName("name"));
		schema.addField(new StringFieldSchemaImpl().setName("description"));
		schema.addField(new BinaryFieldSchemaImpl().setName("package"));

		ImportUtils.createSchemas(client, List.of(schema), project).blockingAwait();

		portal.portalServerComponent().vertx().eventBus()
			.<String>consumer(PortalEvents.PORTAL_IMPLEMENTATION_UPDATED.address(), message -> {
				// Ignore events of the service started in testStartup()
				if (RUNTIME_KEYWORD.equals(message.body())) {
					updateEvents.add(message.body());
				}
			});
	}

	/**
	 * Test that a portal implementation existing on startup is extracted into the configured folders, and that
	 * folders which are not mapped are ignored.
	 */
	@Test
	public void testStartup() throws Exception {
		MeshRestClient client = portal.portalServerComponent().meshRestClient();

		createImplementation(STARTUP_KEYWORD, zip(
			"templates/page.hbs", "startup page",
			"queries/loadByPath.graphql", "{ startup }",
			"i18n/translations.yml", "key: startup",
			"static/main.css", "body {}",
			"other/ignored.txt", "ignored"));

		PortalConfig config = configure(new DefaultPortalConfig().get(), STARTUP_KEYWORD);

		config.setMeshProject(TestData.REF.projectName());
		config.setMeshSchemaPrefix(TestData.REF.schemaPrefix());

		Path dir = BASE_DIR.resolve(STARTUP_KEYWORD);
		FileUtils.deleteDirectory(dir.toFile());
		Files.createDirectories(dir.resolve("templates"));
		Files.writeString(dir.resolve("templates/local.hbs"), "local file");
		Files.createDirectories(dir.resolve("static"));
		Files.writeString(dir.resolve("static/local.css"), "local static file");

		MeshWebsocket websocket = client.eventbus();

		try {
			new PortalImplementationService(config, client, websocket, portal.portalServerComponent().vertx()).start();
		} finally {
			websocket.close();
		}

		assertThat(dir.resolve("templates/page.hbs")).hasContent("startup page");
		assertThat(dir.resolve("templates/local.hbs")).as("Replaced local file").doesNotExist();
		assertThat(dir.resolve("queries/loadByPath.graphql")).hasContent("{ startup }");
		assertThat(dir.resolve("i18n/translations.yml")).hasContent("key: startup");
		assertThat(dir.resolve("implementation-static/main.css")).hasContent("body {}");
		assertThat(dir.resolve("static/local.css")).as("Kept static file of the instance").hasContent("local static file");
		assertThat(dir.resolve("static/main.css")).as("Static file of the implementation in the instance folder").doesNotExist();
		assertThat(dir.resolve("other")).as("Ignored folder").doesNotExist();
		assertThat(BASE_DIR.resolve("other")).as("Ignored folder").doesNotExist();
	}

	/**
	 * Test that the running portal detects a published portal implementation and later modifications of it, and
	 * publishes the update event. Also test that the static files of the portal instance and of the portal
	 * implementation are both served, and that the portal implementation wins for the same path.
	 */
	@Test
	public void testRuntimeModification() throws Exception {
		Path templates = BASE_DIR.resolve(RUNTIME_KEYWORD).resolve("templates");
		Path instanceStatic = Files.createDirectories(BASE_DIR.resolve(RUNTIME_KEYWORD).resolve("static"));

		Files.writeString(instanceStatic.resolve("instance.css"), "instance");
		Files.writeString(instanceStatic.resolve("shared.css"), "instance shared");
		NodeResponse node = createImplementation(RUNTIME_KEYWORD, zip("templates/page.hbs", "version 1"));

		waitForContent(templates.resolve("page.hbs"), "version 1");
		waitForEvents(1);

		updatePackage(node, zip("templates/page.hbs", "version 2", "templates/new.hbs", "new",
			"static/shared.css", "implementation shared", "static/implementation.css", "implementation"));

		waitForContent(templates.resolve("page.hbs"), "version 2");
		assertThat(templates.resolve("new.hbs")).hasContent("new");
		waitForEvents(2);

		assertStatic("/static/instance.css", "instance");
		assertStatic("/static/implementation.css", "implementation");
		assertStatic("/static/shared.css", "implementation shared");
		assertThat(instanceStatic.resolve("shared.css")).as("Kept static file of the instance").hasContent("instance shared");
	}

	/**
	 * Assert that the portal serves the given static file with the expected content.
	 */
	private void assertStatic(String uri, String expected) {
		ResponseWithBody response = getResponseWithBody(portal.portalClient().rxRequest(HttpMethod.GET, uri).blockingGet());

		assertThat(response.getResponse().statusCode()).as("Status of " + uri).isEqualTo(200);
		assertThat(response.getBody()).as("Body of " + uri).isEqualTo(expected);
	}

	/**
	 * Create and publish a portal implementation node as child of the project root node.
	 */
	private NodeResponse createImplementation(String keyword, byte[] zip) {
		MeshRestClient client = portal.meshServer().client();
		String project = TestData.REF.projectName();
		String rootUuid = client.findProjectByName(project).blockingGet().getRootNode().getUuid();
		NodeCreateRequest request = new NodeCreateRequest();

		request.setParentNodeUuid(rootUuid);
		request.setSchema(new SchemaReferenceImpl().setName(TestData.REF.schemaPrefix() + "_implementation"));
		request.setLanguage("en");
		request.getFields().put("keyword", new StringFieldImpl().setString(keyword));
		request.getFields().put("name", new StringFieldImpl().setString("Implementation " + keyword));
		request.getFields().put("description", new StringFieldImpl().setString("Test implementation"));

		NodeResponse node = client.createNode(project, request).blockingGet();

		return updatePackage(node, zip);
	}

	/**
	 * Upload a new package to the node and publish it.
	 */
	private NodeResponse updatePackage(NodeResponse node, byte[] zip) {
		MeshRestClient client = portal.meshServer().client();
		String project = TestData.REF.projectName();
		NodeResponse current = client.findNodeByUuid(project, node.getUuid()).blockingGet();
		NodeResponse updated = client.updateNodeBinaryField(project, node.getUuid(), "en", current.getVersion(), "package",
			new ByteArrayInputStream(zip), zip.length, "implementation.zip", "application/zip").blockingGet();

		client.publishNode(project, node.getUuid()).blockingGet();

		return updated;
	}

	private void waitForContent(Path file, String expected) throws Exception {
		long end = System.currentTimeMillis() + TIMEOUT_MS;

		while (System.currentTimeMillis() < end) {
			if (Files.isRegularFile(file) && expected.equals(Files.readString(file))) {
				return;
			}

			Thread.sleep(200);
		}

		assertThat(file).as("File after %d ms", TIMEOUT_MS).hasContent(expected);
	}

	private void waitForEvents(int count) throws Exception {
		long end = System.currentTimeMillis() + TIMEOUT_MS;

		while (System.currentTimeMillis() < end && updateEvents.size() < count) {
			Thread.sleep(200);
		}

		assertThat(updateEvents).as("Update events").hasSize(count);
	}

	private byte[] zip(String... namesAndContents) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (int i = 0; i < namesAndContents.length; i += 2) {
				zip.putNextEntry(new ZipEntry(namesAndContents[i]));
				zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}

		return out.toByteArray();
	}
}
