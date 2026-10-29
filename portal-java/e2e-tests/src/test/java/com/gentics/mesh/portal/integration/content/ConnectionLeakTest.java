package com.gentics.mesh.portal.integration.content;

import com.gentics.mesh.portal.content.RedirectHandler;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.gentics.mesh.FieldUtil;
import com.gentics.mesh.core.rest.node.FieldMap;
import com.gentics.mesh.core.rest.node.FieldMapImpl;
import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.project.ProjectCreateRequest;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.core.rest.schema.SchemaListResponse;
import com.gentics.mesh.core.rest.schema.impl.SchemaCreateRequest;
import com.gentics.mesh.core.rest.schema.impl.SchemaResponse;
import com.gentics.mesh.portal.DataProviderVerticle;
import com.gentics.mesh.portal.DefaultPortalConfig;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CustomRedirectOptions;
import com.gentics.mesh.portal.config.option.DataProviderOptions;
import com.gentics.mesh.portal.custom.RedirectHelperImpl;
import com.gentics.mesh.portal.server.PortalTestContext;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.test.ConnectionVerifier;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Test for various possible connection leaks
 */
@PortalTestSetting(meshImage = "docker.gentics.com/gentics/mesh-hsqldb", configProvider = ConnectionLeakTest.Config.class)
public class ConnectionLeakTest extends AbstractEndToEndTest {
	/**
	 * Test client
	 */
	protected static OkHttpClient testClient;

	/**
	 * Configuration supplier, which sets up the portal configuration for the test
	 */
	public static class Config implements Supplier<PortalConfig> {
		@Override
		public PortalConfig get() {
			PortalConfig portalConfig = new DefaultPortalConfig().get();

			// configure the redirect source as binary
			CustomRedirectOptions customRedirectOptions = new CustomRedirectOptions();
			customRedirectOptions.setReload(false);
			customRedirectOptions.setSource("/Svg_example4.svg");
			customRedirectOptions.setHelperClass(RedirectHelperImpl.class.getName());
			customRedirectOptions.setHelperConfig(Collections.emptyMap());
			portalConfig.getRedirectOptions().setCustom(customRedirectOptions);

			// configure a data provider URL to a binary
			DataProviderOptions dataProviderOptions = portalConfig.getDataProviderOptions();
			dataProviderOptions.getMeshData().put("bla", Arrays.asList("/Svg_example4.svg"));

			return portalConfig;
		}
	}

	/**
	 * Connection verifier rule
	 */
	@Rule
	public ConnectionVerifier connectionVerifier = new ConnectionVerifier(portal.portalServerComponent().runner().getOkHttpClient());

	/**
	 * Setup the test data
	 */
	@BeforeClass
	public static void setupOnce() throws IOException, URISyntaxException {
		testClient = new OkHttpClient.Builder().callTimeout(1, TimeUnit.HOURS).readTimeout(1, TimeUnit.HOURS).build();

		MeshRestClient meshClient = portal.meshServer().client();

		// create demo node
		ProjectResponse project = meshClient
				.createProject(new ProjectCreateRequest().setName("demo").setSchemaRef("folder")).blockingGet();

		// create category schema and assign to demo node
		SchemaCreateRequest schemaCreateRequest = new SchemaCreateRequest();
		schemaCreateRequest.setName("category");
		schemaCreateRequest.addField(FieldUtil.createStringFieldSchema("name"));
		schemaCreateRequest.addField(FieldUtil.createStringFieldSchema("slug"));
		SchemaResponse schemaResponse = meshClient.createSchema(schemaCreateRequest).blockingGet();
		meshClient.assignSchemaToProject("demo", schemaResponse.getUuid()).blockingAwait();

		// assign binary_content schema to demo node
		SchemaListResponse schemas = meshClient.findSchemas().blockingGet();
		SchemaResponse binaryContentSchema = schemas.getData().stream()
				.filter(s -> StringUtils.equals(s.getName(), "binary_content")).findFirst().orElseThrow();
		meshClient.assignSchemaToProject("demo", binaryContentSchema.getUuid()).blockingAwait();

		NodeCreateRequest createBinary = new NodeCreateRequest().setParentNode(project.getRootNode())
				.setSchemaName("binary_content").setLanguage("en");
		NodeResponse binaryNode = meshClient.createNode(project.getName(), createBinary).blockingGet();
		File binaryFile = new File(PortalTestContext.class.getResource("/setup/REF/binaries/Svg_example4.svg").toURI());
		meshClient.updateNodeBinaryField(project.getName(), binaryNode.getUuid(),
			"en", "0.1", "binary", Files.newInputStream(binaryFile.toPath()),
			binaryFile.length(), "Svg_example4.svg", "image/svg+xml").blockingAwait();
		meshClient.publishNode(project.getName(), binaryNode.getUuid()).blockingAwait();

		// create and publish a node of schema "category", which uses a slug with a "binary" extension
		NodeCreateRequest create = new NodeCreateRequest().setParentNode(project.getRootNode()).setSchemaName("category").setLanguage("en");
		FieldMap fields = new FieldMapImpl();
		fields.put("name", FieldUtil.createStringField("Bla Category"));
		fields.put("slug", FieldUtil.createStringField("bla.png"));
		create.setFields(fields);
		NodeResponse node = meshClient.createNode(project.getName(), create).blockingGet();
		meshClient.publishNode("demo", node.getUuid()).blockingAwait();
	}

	/**
	 * Test loading a node, which is not a binary, over a webroot path that ends with .png
	 * @throws InterruptedException
	 */
	@Test
	public void testNonBinaryAsBinary() throws IOException, InterruptedException {
		getFromPortal("/bla.png");
	}

	/**
	 * Test loading a binary
	 * @throws InterruptedException
	 */
	@Test
	public void testBinary() throws InterruptedException {
		getFromPortal("/images/ford-gt.jpg");
	}

	/**
	 * Test loading a non-existing binary
	 * @throws InterruptedException
	 */
	@Test
	public void testBinaryNotFound() throws InterruptedException {
		getFromPortal("/images/does-not-exist.png");
	}

	/**
	 * Test loading a non-binary
	 * @throws InterruptedException
	 */
	@Test
	public void testNonBinary() throws InterruptedException {
		getFromPortal("/automobiles");
	}

	/**
	 * Test loading a non-existing non-binary
	 * @throws InterruptedException
	 */
	@Test
	public void testNonBinaryNotFound() throws InterruptedException {
		getFromPortal("/computers");
	}

	/**
	 * Test refreshing the redirects from the source, which is binary
	 */
	@Test
	public void testRedirectHandlerRefresh() {
		RedirectHandler redirectHandler = portal.portalServerComponent().runner().getRedirectHandler();
		redirectHandler.refreshNow(null);
	}

	/**
	 * Test refreshing the data, which is binary
	 */
	@Test
	public void testDataProviderRefresh() {
		DataProviderVerticle dataProvider = portal.portalServerComponent().runner().getDataProvider();
		dataProvider.refreshNow();
	}

	/**
	 * Make a get request to the given path
	 * @param path path
	 * @throws InterruptedException
	 */
	protected void getFromPortal(String path) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		testClient
				.newCall(new Request.Builder().get()
						.url("http://localhost:" + portal.portalPort() + path).build())
				.enqueue(new Callback() {
					@Override
					public void onResponse(Call call, Response response) throws IOException {
						response.close();
						latch.countDown();
					}

					@Override
					public void onFailure(Call call, IOException e) {

					}
				});

		latch.await(20, TimeUnit.SECONDS);
	}
}
