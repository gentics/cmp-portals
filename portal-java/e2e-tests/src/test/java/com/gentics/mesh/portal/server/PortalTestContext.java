package com.gentics.mesh.portal.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.gentics.mesh.core.rest.user.UserAPITokenCreateRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.node.field.StringField;
import com.gentics.mesh.core.rest.project.ProjectCreateRequest;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.core.rest.schema.impl.SchemaCreateRequest;
import com.gentics.mesh.core.rest.user.NodeReference;
import com.gentics.mesh.core.rest.user.UserAPITokenResponse;
import com.gentics.mesh.importer.helper.ImportUtils;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.portal.Portal;
import com.gentics.mesh.portal.PortalTestSetting;
import com.gentics.mesh.portal.TestContextStartupCheckMode;
import com.gentics.mesh.portal.TestData;
import com.gentics.mesh.portal.api.PortalEvents;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.api.server.PortalState;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.StartupCheckMode;
import com.gentics.mesh.portal.config.option.KeycloakOptions;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.monitoring.MonitoringServiceVerticle;
import com.gentics.mesh.portal.server.core.MeshStatusChecker;
import com.gentics.mesh.portal.server.core.PortalFunctionStartupChecker;
import com.gentics.mesh.portal.server.core.PortalHttpVerticle;
import com.gentics.mesh.portal.server.core.QueryStoreChecker;
import com.gentics.mesh.portal.server.dagger.DaggerPortalServerComponent;
import com.gentics.mesh.portal.server.dagger.PortalServerComponent;
import com.gentics.mesh.portal.tests.AbstractEndToEndTest;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.test.docker.MeshContainer;
import com.gentics.testcontainers.KeycloakContainer;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpClient;

/**
 * Starts Mesh and Portal instance for tests.
 */
public class PortalTestContext extends TestWatcher {
	private static final Logger log = LoggerFactory.getLogger(PortalTestContext.class);

	private static final String HTTP_VERTICLE = PortalHttpVerticle.class.getName();
	private static final String MONITORING_VERTICLE = MonitoringServiceVerticle.class.getName();

	private static MeshContainer mesh;
	private static KeycloakContainer keycloak;

	private BootstrapInitializer boot;
	private HttpClient portalClient;
	private HttpClient monitoringClient;
	private HttpClient keycloakClient;
	private PortalConfig portalConfig;
	private int portalPort;
	private PortalServerComponent portalServerComponent;
	private CountDownLatch startupChecksLatch = new CountDownLatch(1);

	public PortalTestContext() {
	}

	@Override
	protected void starting(@NotNull Description description) {
		try {
			PortalTestSetting settings = getSettings(description);
			if (description.isSuite()) {
				setupOnce(settings);
				setup(settings);
			} else {
				setup(settings);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private void setup(PortalTestSetting settings) {
		portalServerComponent = configurePortal(settings);
		portalConfig = portalServerComponent.portalConfig();

		Map<String, Integer> verticlePorts = startPortal(portalServerComponent, settings.startupChecks(), settings.startupCheckDelay());

		if (verticlePorts.containsKey(HTTP_VERTICLE)) {
			portalPort = verticlePorts.get(HTTP_VERTICLE);
			portalClient = createHttpClient(portalPort, HTTP_VERTICLE);
			monitoringClient = createHttpClient(verticlePorts.get(MONITORING_VERTICLE), MONITORING_VERTICLE);
		}
	}

	private void setupOnce(PortalTestSetting settings) throws IOException {
		if (StringUtils.isNotBlank(settings.meshImage())) {
			System.out.println("Waiting for Mesh startup");
			String imageName = settings.meshImage() + ":" + MeshRestClient.getPlainVersion();
			mesh = new MeshContainer(imageName);
			if (StringUtils.isNotBlank(settings.pluginsDir())) {
				for (File file : new File(settings.pluginsDir()).listFiles()) {
					String fileName = file.getName();
					if (fileName.startsWith("mesh-") && fileName.endsWith("-plugin.jar")) {
						mesh = mesh.withPlugin(file, fileName);
					}
				}
			}
			// TODO remove this when mesh and plugins do not need a LICENSEKEY
			if (StringUtils.isNotBlank(System.getenv("LICENSEKEY"))) {
				mesh = mesh.withEnv("LICENSEKEY", System.getenv("LICENSEKEY"));
			}
			mesh.waitForStartup().start();
		}
		if (StringUtils.isNotBlank(settings.keycloakConfig())) {
			System.out.println("Waiting for Keycloak startup");
			keycloak = new KeycloakContainer(settings.keycloakConfig())
				.withEnv("KEYCLOAK_ADMIN", "admin")
				.withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin");

			if (!keycloak.isRunning() && settings.startKeycloak()) {
				keycloak.start();
			}
		}
		try {
			// Mesh Demo needs no special setup.
			if (settings.testData() != TestData.DEMO) {
				setupProjectData(settings.testData());
			}
		} catch (Exception e) {
			String msg = "Error during test data setup: " + e.getMessage();

			log.error(msg, e);

			throw new RuntimeException(msg, e);
		}

	}

	private void setupProjectData(TestData data) throws IOException {
		String projectName = data.projectName();
		MeshRestClient client = meshServer().client();
		String baseDir = "src/test/resources/setup/" + data.name();

		// Create Schemas
		List<SchemaCreateRequest> schemas = ImportUtils.loadSchemas(baseDir + "/schemas");

		// Create Project
		ProjectCreateRequest projectRequest = new ProjectCreateRequest();
		projectRequest.setName(projectName);
		projectRequest.setSchemaRef("folder");
		Single<ProjectResponse> project = client.createProject(projectRequest).toSingle()
			.doOnSubscribe(ignored -> log.info("Creating project {}", projectName))
			.doOnSuccess(rs -> log.info("Project {} created: {}", projectName, rs.getUuid()));

		// Create Structure
		List<NodeResponse> level0Nodes = ImportUtils.loadNodes(baseDir + "/nodes-level-0");
		List<NodeResponse> nodes = new ArrayList<>();

		// Adds all nodes from files "nodes-level-n" until a file cannot be found.
		for (int i = 1 ;; i++) {
			try {
				nodes.addAll(ImportUtils.loadNodes(baseDir + "/nodes-level-" + i));
			} catch (IOException e) {
				// Breaks the loop when a file could not be found
				break;
			}
		}

		List<NodeResponse> foldersWithStartPage = Stream.of(
			level0Nodes.stream(),
			nodes.stream()
		).flatMap(Function.identity())
			.filter(node -> node.getFields().getNodeField("startpage") != null)
			.collect(Collectors.toList());

		// Create Contents
		project.flatMapCompletable(p -> {
			Completable level0CreationOps = Observable.fromIterable(level0Nodes)
				.concatMapCompletable(node -> createNode(p, node));

			Completable creationOps = Observable.fromIterable(nodes).
				concatMapCompletable(node -> createNode(projectName, node));

			Completable startPageOps = Observable.fromIterable(foldersWithStartPage).concatMapCompletable(node -> {
				NodeUpdateRequest request = JsonUtil.readValue(node.toJson(), NodeUpdateRequest.class);

				return client.updateNode(projectName, node.getUuid(), request).toCompletable();
			});

			Completable uploadBinary;

			// TODO Generalize binaries for project setups.
			if (data == TestData.REF) {
				File binaryFile = new File(AbstractEndToEndTest.class.getResource("/setup/REF/binaries/Svg_example4.svg").toURI());

				uploadBinary = client.updateNodeBinaryField(projectName, "a22f189738be11e8904c005056bd2479",
					"en", "0.1", "binarycontent", Files.newInputStream(binaryFile.toPath()),
					binaryFile.length(), "Svg_example4.svg", "image/svg+xml").toCompletable();
			} else {
				uploadBinary = Completable.complete();
			}

			return ImportUtils.createSchemas(client, schemas, p.getName())
				.andThen(level0CreationOps)
				.andThen(creationOps)
				.andThen(startPageOps)
				.andThen(uploadBinary)
				.andThen(ImportUtils.publishNodes(client, p));
		}).blockingAwait();
	}

	private Completable createNode(String projectName, NodeResponse node) {
		return createNode(projectName, null, node);
	}

	private Completable createNode(ProjectResponse project, NodeResponse node) {
		return createNode(project.getName(), project.getRootNode(), node);
	}

	private Completable createNode(String projectName, NodeReference rootNode, NodeResponse node) {
		String schemaName = Optional.ofNullable(node.getSchema())
			.map(SchemaReference::getName)
			.orElse("NA");
		String nodeName = Optional.ofNullable(node.getFields())
			.map(fields -> fields.getStringField("name"))
			.map(StringField::getString)
			.orElse("NA");
		String nodeUuid = node.getUuid();
		NodeCreateRequest request = JsonUtil.readValue(node.toJson(), NodeCreateRequest.class);

		if (rootNode != null) {
			request.setParentNode(rootNode);
		}

		return meshServer().client().createNode(nodeUuid, projectName, request).toCompletable()
			.doOnSubscribe(ignored -> log.info("Creating node {}/{} ({})", schemaName, nodeName, nodeUuid))
			.doOnComplete(() -> log.info("Node {} successfully created", nodeUuid));
	}

	@Override
	protected void finished(@NotNull Description description) {
		try {
			PortalTestSetting settings = getSettings(description);
			if (description.isSuite()) {
				tearDownOnce();
				tearDown();
			} else {
				tearDown();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void tearDownOnce() {
		try {
			if (portalServerComponent != null && portalServerComponent.portalInfo() != null) {
				portalServerComponent.portalInfo().shutdown();
			}

			if (boot != null) {
				boot.stop();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void tearDown() {
		if (mesh != null && mesh.isRunning()) {
			mesh.stop();
		}
		if (keycloak != null && keycloak.isRunning()) {
			keycloak.stop();
		}
	}

	/**
	 * Set Features according to the method annotations
	 *
	 * @param description Test description
	 */
	protected PortalTestSetting getSettings(Description description) {
		Class<?> testClass = description.getTestClass();
		if (testClass != null) {
			return testClass.getAnnotation(PortalTestSetting.class);
		}
		return description.getAnnotation(PortalTestSetting.class);
	}

	/**
	 * Get a HTTP client for retrieving portal content.
	 *
	 * @return A HTTP client for retrieving portal content
	 */
	public HttpClient portalClient() {
		return portalClient;
	}

	/**
	 * Get the HTTP client for retrieving monitoring information.
	 *
	 * @return The HTTP client for retrieving monitoring information
	 */
	public HttpClient monitoringClient() {
		return monitoringClient;
	}

	/**
	 * Get the HTTP client for the configured Keycloak instance.
	 *
	 * @return The HTTP client for the configured Keycloak instance.
	 */
	public HttpClient keycloakClient() {
		if (keycloakClient == null) {
			keycloakClient = createHttpClient(keycloak.getMappedPort(8080), "keycloak");
		}

		return keycloakClient;
	}

	/**
	 * Set the necessary Mesh options in the portal configuration.
	 *
	 * @param testSettings Test settings
	 *
	 * @return A configured <code>PortalServerComponent</code>
	 */
	private PortalServerComponent configurePortal(PortalTestSetting testSettings) {
		try {
			System.out.println("Configuring portal");

			PortalConfig config = testSettings.configProvider().newInstance().get();
			TestData testData = testSettings.testData();

			// Apply the test settings
			if (testData == TestData.DEMO) {
				config.setMeshProject("demo");
			} else {
				config.setMeshProject(testData.projectName());

				if (StringUtils.isNotBlank(testData.schemaPrefix())) {
					config.setMeshSchemaPrefix(testData.schemaPrefix());
				}
			}
			if (keycloak != null) {
				String realmName = testSettings.keycloakRealmName();
				String clientId = testSettings.keycloakClientId();
				KeycloakOptions kco = config.getAuthOptions().getKeycloakOptions();

				kco.setRealmUrl("http://%s:%d/auth/realms/%s".formatted(keycloak.getHost(), keycloak.isRunning() ? keycloak.getMappedPort(8080) : 8080, realmName))
					.setClientId(clientId)
					.setRetryPeriodSeconds(testSettings.keycloakInitRetryPeriodSeconds());
			}
			PortalServerComponent portal = Portal.create(() -> DaggerPortalServerComponent.builder().configuration(config).build());
			if (mesh != null) {
				MeshRestClient meshClient = mesh.client();

				meshClient.setLogin("admin", "admin");

				UserAPITokenCreateRequest req = new UserAPITokenCreateRequest();
				req.setName("Example");
				String apiKey = meshClient.login().ignoreElement()
					.andThen(meshClient.me().toSingle())
					.flatMap(user -> meshClient.issueAPIToken(user.getUuid(), req).toSingle())
					.map(UserAPITokenResponse::getToken)
					.blockingGet();

				MeshOptions meshOptions = portal.portalConfig().getMeshOptions();

				meshOptions.setApiKey(apiKey);
				meshOptions.setHost(mesh.getHost());
				meshOptions.setPort(mesh.getPort());

				System.out.printf(
					"\n\n==========\n=== Mesh is running on %s:%d\n==========",
					mesh.getHost(),
					mesh.getPort());
			}
			return portal;
		} catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException(t);
		}
	}

	/**
	 * Start the portal and wait until all HTTP verticles as well as the monitoring service have been deployed.
	 *
	 * @param portal The portal to start
	 * @param testStartupMode Whether the portal starup checks should be performed and should succeed or are expected to
	 * fail
	 * @param startupCheckDelay How long to wait before performing the start-up checks
	 * @return A map from verticle class names to their actual server ports
	 */
	private Map<String, Integer> startPortal(PortalServerComponent portal, TestContextStartupCheckMode testStartupMode, int startupCheckDelay) {
		System.out.println("Starting portal");
		Subject<JsonObject> verticles = PublishSubject.create();

		// Load the portal implementation before the start-up checks, like PortalStarter does.
		portal.portalImplementation().start();

		portal.vertx().eventBus().consumer(
			PortalEvents.SERVER_VERTICLE_DEPLOYED.address(),
			event -> {
				JsonObject data = (JsonObject) event.body();

				System.out.println("Event data: " + data.encode());
				verticles.onNext(data);
			});

		boolean checksSucceeded = true;

		StartupCheckMode startupCheckMode = portalConfig.getMonitoringOptions().getStartupCheckMode();

		if (testStartupMode != TestContextStartupCheckMode.SKIP) {
			List<PortalFunctionStartupChecker> startupChecks = Arrays.asList(
							new QueryStoreChecker(portal),
							new MeshStatusChecker(portal));

			if ((startupCheckMode == StartupCheckMode.UNREADY && testStartupMode == TestContextStartupCheckMode.SHOULD_ABORT)) {
				throw new RuntimeException("Invalid test setup: start-up check mode UNREADY and test start-up mode SHOULD_ABORT cannot be used together");
			}

			if (startupCheckMode == StartupCheckMode.ABORT) {
				checksSucceeded = startupChecks.stream().allMatch(check -> check.check());
			} else if (startupCheckMode == StartupCheckMode.UNREADY) {
				Observable.fromIterable(startupChecks)
					.delay(startupCheckDelay, TimeUnit.SECONDS)
					.flatMapCompletable(PortalFunctionStartupChecker::checkAsync)
					.doOnError(error -> {
						log.error("Start-up check failed: {}", error.getMessage(), error);
						portal.portalInfo().setState(PortalState.STARTUP_CHECK_ERROR);
					})
					.doOnTerminate(startupChecksLatch::countDown)
					.subscribe();
			}

			boolean portalShouldStart = testStartupMode == TestContextStartupCheckMode.SHOULD_START;

			// Only abort the start-up when the configred start-up check mode is not UNREADY (in that case the portal
			// should start, but the ready endpoint must respond with 503 Service Unavailable).
			if (startupCheckMode == StartupCheckMode.ABORT) {
				if (!checksSucceeded && portalShouldStart) {
					throw new RuntimeException("Portal should have started but checks failed");
				} else if (checksSucceeded && !portalShouldStart) {
					throw new RuntimeException("Portal start should have been aborted");
				}
			}
		}

		if (!checksSucceeded) {
			portal.portalInfo().setState(PortalState.STARTUP_CHECK_ERROR);

			if (startupCheckMode == StartupCheckMode.ABORT) {
				System.out.println("Aborting portal start due to failed start-up checks");

				return Collections.emptyMap();
			}
		}

		boot = portal.boot();

		try {
			boot.start();
		} catch (Exception e) {
			System.err.println("Error while staring server");
			e.printStackTrace(System.err);
		}

		int numVerticles = portal.portalConfig().getVertxOptions().getEventPoolSize() + 1;

		System.out.println("Waiting for " + numVerticles + " verticles to start");

		return verticles
			// Event pool size is the number of HTTP verticles and the +1 is the monitoring verticle.
			.take(portal.portalConfig().getVertxOptions().getEventPoolSize() + 1)
			.map(o -> Pair.of(o.getString("verticle"), o.getInteger("port")))
			.to(RxUtils::map)
			.timeout(30, TimeUnit.SECONDS)
			.blockingGet();
	}

	/**
	 * Create a HTTP client for the specified verticle on the given port.
	 *
	 * @param port
	 *            The port the verticle server listens on
	 * @param verticle
	 *            The verticle to create a HTTP client for
	 * @return A HTTP client for the specified verticle on the given port
	 */
	private HttpClient createHttpClient(int port, String verticle) {
		if (port <= 0) {
			System.err.println("No port specified for " + verticle);

			return null;
		}

		HttpClientOptions options = new HttpClientOptions()
			.setDefaultHost("localhost")
			.setDefaultPort(port);

		System.out.println("Creating client for localhost:" + options.getDefaultPort() + ": " + verticle);

		return new io.vertx.reactivex.core.Vertx(Vertx.vertx()).createHttpClient(options);
	}

	public MeshContainer meshServer() {
		return mesh;
	}

	public KeycloakContainer keycloak() {
		return keycloak;
	}

	public BootstrapInitializer boot() {
		return boot;
	}

	public PortalConfig portalConfig() {
		return portalConfig;
	}

	public int portalPort() {
		return portalPort;
	}

	public CountDownLatch startupChecksLatch() {
		return startupChecksLatch;
	}

	public PortalServerComponent portalServerComponent() {
		return portalServerComponent;
	}
}
