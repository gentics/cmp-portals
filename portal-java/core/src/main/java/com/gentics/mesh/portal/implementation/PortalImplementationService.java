package com.gentics.mesh.portal.implementation;

import static com.gentics.mesh.core.rest.MeshEvent.NODE_DELETED;
import static com.gentics.mesh.core.rest.MeshEvent.NODE_MOVED;
import static com.gentics.mesh.core.rest.MeshEvent.NODE_PUBLISHED;
import static com.gentics.mesh.core.rest.MeshEvent.NODE_UNPUBLISHED;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.graphql.GraphQLError;
import com.gentics.mesh.core.rest.graphql.GraphQLRequest;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.parameter.client.VersioningParametersImpl;
import com.gentics.mesh.portal.api.PortalEvents;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.queries.QueryStoreImpl;
import com.gentics.mesh.rest.client.EventbusEvent;
import com.gentics.mesh.rest.client.MeshBinaryResponse;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientUtil;
import com.gentics.mesh.rest.client.MeshWebsocket;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Loads the files of the {@link PortalConfig#getPortalImplementation() configured portal implementation} from Mesh.
 *
 * <p>
 *     The portal implementation is a published node of the schema {@link PortalConfig#getImplementationSchema()},
 *     which is a direct child of the root node of the configured Mesh project (default branch), and has the configured
 *     keyword in its field {@value #FIELD_KEYWORD}. Its binary field {@value #FIELD_PACKAGE} contains a zip file, which
 *     is extracted by the {@link PortalImplementationExtractor}.
 * </p>
 *
 * <p>
 *     After the initial synchronization on startup, Mesh events for nodes of the implementation schema (and
 *     reconnections to the Mesh eventbus) trigger another synchronization. The package is only extracted again, when
 *     its checksum changed. After an update, the query caches are invalidated and
 *     {@link PortalEvents#PORTAL_IMPLEMENTATION_UPDATED} is published.
 * </p>
 */
@Singleton
public class PortalImplementationService {

	private static final Logger log = LoggerFactory.getLogger(PortalImplementationService.class);

	public static final String FIELD_KEYWORD = "keyword";
	public static final String FIELD_PACKAGE = "package";

	public static final String FOLDER_TEMPLATES = "templates";
	public static final String FOLDER_QUERIES = "queries";
	public static final String FOLDER_I18N = "i18n";
	public static final String FOLDER_STATIC = "static";

	/** Placeholder for the implementation schema name in {@link #IMPLEMENTATION_QUERY}. */
	private static final String SCHEMA_PLACEHOLDER = "%implementationSchema%";

	/** GraphQL query loading the children of the root node with the implementation schema and the given keyword. */
	static final String IMPLEMENTATION_QUERY = String.join("\n",
		"query ($keyword: String) {",
		"  rootNode {",
		"    children(filter: {schema: {is: %implementationSchema%}, fields: {%implementationSchema%: {keyword: {equals: $keyword}}}}) {",
		"      elements {",
		"        uuid",
		"        language",
		"        fields { ... on %implementationSchema% { package { binaryUuid fileName sha512sum } } }",
		"      }",
		"    }",
		"  }",
		"}");

	private static final MeshEvent[] EVENTS = { NODE_PUBLISHED, NODE_UNPUBLISHED, NODE_MOVED, NODE_DELETED };

	private final PortalConfig config;
	private final MeshRestClient meshClient;
	private final MeshWebsocket eventbus;
	private final Vertx vertx;
	private final PortalImplementationExtractor extractor = new PortalImplementationExtractor();

	/** Checksum of the last extracted package. */
	private String appliedChecksum;

	private boolean started = false;

	@Inject
	public PortalImplementationService(PortalConfig config, MeshRestClient meshClient, MeshWebsocket eventbus, Vertx vertx) {
		this.config = config;
		this.meshClient = meshClient;
		this.eventbus = eventbus;
		this.vertx = vertx;
	}

	/**
	 * Check whether a portal implementation is configured.
	 *
	 * @return {@code true} when a portal implementation keyword is configured
	 */
	public boolean isEnabled() {
		return StringUtils.isNotBlank(config.getPortalImplementation());
	}

	/**
	 * Synchronize the portal implementation (blocking) and start listening to changes in Mesh. Does nothing when no
	 * portal implementation is configured, or when it was already started.
	 */
	public synchronized void start() {
		if (!isEnabled() || started) {
			return;
		}

		started = true;

		log.info("Loading portal implementation {} from Mesh project {}", config.getPortalImplementation(), config.getMeshProject());

		try {
			synchronize().blockingAwait();
		} catch (Exception e) {
			log.error("Could not load portal implementation {}, using local files: {}", config.getPortalImplementation(), e.getMessage(), e);
		}

		eventbus.registerEvents(EVENTS);
		Observable.merge(
			eventbus.connections(),
			eventbus.events()
				.filter(MeshRestClientUtil.isOneOf(EVENTS))
				.filter(this::isImplementationEvent)
		).concatMapCompletable(ignore -> synchronize()
			.subscribeOn(Schedulers.io())
			.doOnError(e -> log.error("Could not update portal implementation {}: {}", config.getPortalImplementation(), e.getMessage(), e))
			.onErrorComplete()
		).subscribe();
	}

	/**
	 * Load the portal implementation from Mesh and extract its package, if it changed since the last synchronization.
	 *
	 * @return completable
	 */
	public Completable synchronize() {
		return Completable.fromAction(this::doSynchronize);
	}

	/**
	 * Check whether the event concerns a node of the implementation schema in the configured project.
	 */
	private boolean isImplementationEvent(EventbusEvent event) {
		JsonNode body = event.getBodyAsJson();

		if (body == null) {
			return false;
		}

		return config.getMeshProject().equals(body.path("project").path("name").asText())
			&& config.getImplementationSchema().equals(body.path("schema").path("name").asText());
	}

	private synchronized void doSynchronize() throws IOException {
		String project = config.getMeshProject();
		String keyword = config.getPortalImplementation();
		Optional<JsonObject> implementation = findImplementation(project, keyword);

		if (!implementation.isPresent()) {
			log.warn("Portal implementation {} (schema {}) not found in Mesh project {}, using local files", keyword, config.getImplementationSchema(), project);
			return;
		}

		JsonObject node = implementation.get();
		String uuid = node.getString("uuid");
		JsonObject packageField = Optional.ofNullable(node.getJsonObject("fields"))
			.map(fields -> fields.getJsonObject(FIELD_PACKAGE))
			.orElse(null);

		if (packageField == null || StringUtils.isBlank(packageField.getString("binaryUuid"))) {
			log.warn("Portal implementation {} ({}) has no package, using local files", keyword, uuid);
			return;
		}

		String checksum = packageField.getString("sha512sum");

		if (checksum != null && checksum.equals(appliedChecksum)) {
			log.debug("Portal implementation {} is unchanged", keyword);
			return;
		}

		log.info("Downloading package {} of portal implementation {} ({})", packageField.getString("fileName"), keyword, uuid);

		try (MeshBinaryResponse download = meshClient.downloadBinaryField(project, uuid, node.getString("language"), FIELD_PACKAGE,
				new VersioningParametersImpl().published()).blockingGet()) {
			extractor.extract(download.getStream(), getTargets());
		}

		appliedChecksum = checksum;
		QueryStoreImpl.invalidateAllCaches();
		vertx.eventBus().publish(PortalEvents.PORTAL_IMPLEMENTATION_UPDATED.address(), keyword);

		log.info("Portal implementation {} was updated", keyword);
	}

	/**
	 * Find the published implementation node with the given keyword among the children of the project root node,
	 * using a GraphQL query filtering by schema name and keyword.
	 *
	 * @return the node as returned by {@link #IMPLEMENTATION_QUERY}
	 * @throws IOException when the query returned errors
	 */
	private Optional<JsonObject> findImplementation(String project, String keyword) throws IOException {
		// The schema filter takes an enum value and the fragment a type name, so the schema name cannot be a variable.
		GraphQLRequest request = new GraphQLRequest()
			.setQuery(IMPLEMENTATION_QUERY.replace(SCHEMA_PLACEHOLDER, config.getImplementationSchema()))
			.setVariables(new JsonObject().put("keyword", keyword));
		GraphQLResponse response = meshClient.graphql(project, request, new VersioningParametersImpl().published()).blockingGet();

		if (response.getErrors() != null && !response.getErrors().isEmpty()) {
			throw new IOException(String.format("Could not load portal implementation %s: %s", keyword,
				response.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining(", "))));
		}

		JsonArray matches = Optional.ofNullable(response.getData())
			.map(data -> data.getJsonObject("rootNode"))
			.map(rootNode -> rootNode.getJsonObject("children"))
			.map(children -> children.getJsonArray("elements"))
			.orElseGet(JsonArray::new);

		if (matches.isEmpty()) {
			return Optional.empty();
		}

		JsonObject first = matches.getJsonObject(0);

		if (matches.size() > 1) {
			log.warn("Found {} portal implementations with keyword {}, using {}", matches.size(), keyword, first.getString("uuid"));
		}

		return Optional.of(first);
	}

	/**
	 * Get the target folders for the folders of the package.
	 *
	 * @return map of package folder names to the configured paths
	 */
	Map<String, Path> getTargets() {
		Map<String, Path> targets = new LinkedHashMap<>();

		targets.put(FOLDER_TEMPLATES, Paths.get(config.getTemplatesPath()));
		targets.put(FOLDER_QUERIES, Paths.get(config.getQueryPath()));
		targets.put(FOLDER_I18N, Paths.get(config.getI18nPath()));
		// Not static.path, so that the static files of the portal instance are kept (both are served)
		targets.put(FOLDER_STATIC, Paths.get(config.getStaticHandlerOptions().getImplementationPath()));

		return targets;
	}
}
