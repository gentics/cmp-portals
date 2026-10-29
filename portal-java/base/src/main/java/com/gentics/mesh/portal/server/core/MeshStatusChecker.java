package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.core.rest.MeshServerInfoModel;
import com.gentics.mesh.core.rest.plugin.PluginListResponse;
import com.gentics.mesh.core.rest.plugin.PluginStatus;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.server.dagger.BaseComponent;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Check if Mesh is reachable, able to answer requests and all plugins have
 * been registered properly.
 *
 * <p>
 *     The {@link #check()} method will wait indefinitely until Mesh is
 *     ready <em>except</em> when it responds with <em>401 Unauthorized</em>
 *     or <em>403 Forbidden</em>, in which case the portal start up is aborted
 *     immediately because it cannot recover from that without changing the Mesh
 *     API key in the configuration.
 * </p>
 *
 * <p>
 *     When {@link MeshOptions#getRequiredPlugins() required plugins} are
 *     configured, the check will wait for all of them to become registered,
 *     otherwise it will just wait until all found plugins are properly
 *     registered.
 * </p>
 *
 * <p>
 *     This check will also set the {@link MeshOptions#isWebrootFieldAvailable()}
 *     setting, depending on the Mesh version.
 * </p>
 */
public class MeshStatusChecker implements PortalFunctionStartupChecker {

	private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";
	private static final int WEBROOT_FIELD_MIN_MESH_VERSION_MAJOR = 1;
	private static final int WEBROOT_FIELD_MIN_MESH_VERSION_MINOR = 6;
	private static final int WEBROOT_FIELD_MIN_MESH_VERSION_PATCH = 9;
	private static final int WAIT_TIME_MILLIS = 5000;

	private static final Logger log = LoggerFactory.getLogger(MeshStatusChecker.class);

	private final Vertx vertx;
	private final PortalConfig config;
	private final MeshRestClient meshClient;
	private final Set<String> requiredPlugins;

	public MeshStatusChecker(BaseComponent context) {
		this.vertx = context.vertx();
		this.config = context.portalConfig();
		this.meshClient = context.meshRestClient();
		this.requiredPlugins = new HashSet<>(context.portalConfig().getMeshOptions().getRequiredPlugins());
	}

	/**
	 * Wait for Mesh to be ready.
	 *
	 * <p>
	 *     The method will poll the plugin status until all present plugins are
	 *     in the state {@code REGISTERED}, and the
	 *     {@link MeshOptions#getRequiredPlugins() required} plugins are
	 *     present.
	 * </p>
	 *
	 * <p>
	 *     When no required plugins are configured, an empty plugin list
	 *     response from Mesh <em>could</em> mean, that Mesh does not now about
	 *     the plugins it has to deploy yet. In this case, the check is repeated
	 *     after {@link #WAIT_TIME_MILLIS} milliseconds.
	 * </p>
	 *
	 * <p>
	 *     When the configured Mesh API key is not valid or not an API key for
	 *     the admin user, the method will initiate a shutdown and return
	 *     {@code false}.
	 * </p>
	 *
	 * @see #getMeshStatus()
	 * @return {@code true} when Mesh and all required plugins are ready,
	 * 		{@code false} when the configured Mesh API key is not a valid key
	 * 		for the admin user
	 */
	@Override
	public boolean check() {
		log.info("Waiting for Mesh to be ready");

		if (!requiredPlugins.isEmpty() && log.isInfoEnabled()) {
			log.info("Required plugins: [{}]", String.join(", ", requiredPlugins));
		}

		MeshStatus status;
		int count = 1;

		while ((status = getMeshStatus()) == MeshStatus.NOT_READY) {
			log.info("Waiting for Mesh try #{}: {}", count++, status);

			try {
				Thread.sleep(WAIT_TIME_MILLIS);
			} catch (InterruptedException e) {
				// It does not matter when the sleep gets interrupted.
			}
		}

		if (status == MeshStatus.FORBIDDEN) {
			log.error("FATAL: Aborting portal start up");
			vertx.close().andThen(rs -> System.exit(1));

			return false;
		}

		if (status == MeshStatus.UNKNOWN) {
			// When there are no required plugins, the first plugin response
			// from Mesh might be empty, because it did not yet load the list
			// of plugins. So we check again after waiting for a bit.
			//
			// NOTE that it is still possible that the timing is unlucky and
			// we get an empty response again despite the fact that there are
			// plugins that will be loaded. It is always better to specify the
			// required plugins in the configuration to prevent this.
			log.info("Rechecking Mesh status to check plugins");

			do {
				try {
					Thread.sleep(WAIT_TIME_MILLIS);
				} catch (InterruptedException e) {
					// It does not matter when the sleep gets interrupted.
				}
			} while (getMeshStatus() == MeshStatus.NOT_READY);
		}

		log.info("Mesh is ready, continuing with portal startup");

		return true;
	}

	/**
	 * Test if Mesh is ready by querying the status of the plugins.
	 *
	 * <p>
	 *     When Mesh is not reachable this will result in
	 *     {@link MeshStatus#NOT_READY}.
	 * </p>
	 *
	 * <p>
	 *     When Mesh is reachable, but returns an empty list of plugins the
	 *     result is {@link MeshStatus#UNKNOWN}, if and only if the set of
	 *     required plugins is also empty.
	 * </p>
	 *
	 * <p>
	 *     When Mesh is reachable and returns a non-empty list of plugins, or
	 *     the set of required plugins is not empty, the result will be
	 *     {@link MeshStatus#READY}, if all required plugins are in the response
	 *     and all plugins have the state {@link PluginStatus#REGISTERED}.
	 *     Otherwise the result is {@link MeshStatus#NOT_READY}.
	 * </p>
	 *
	 * @see #loadPluginInfo(Map)
	 * @see #checkPlugins(Map)
	 * @return The respective status of Mesh and the plugins
	 */
	private MeshStatus getMeshStatus() {
		Map<PluginStatus, Collection<String>> plugins = new HashMap<>();
		MeshStatus status = loadMeshVersion();

		if (status != MeshStatus.UNKNOWN) {
			return status;
		}

		status = loadPluginInfo(plugins);

		if (status != MeshStatus.UNKNOWN) {
			return status;
		}

		if (plugins.isEmpty() && requiredPlugins.isEmpty()) {
			log.debug("No plugins found");

			return MeshStatus.UNKNOWN;
		}

		return checkPlugins(plugins);
	}

	/**
	 * Load the Mesh version via the API info endpoint.
	 *
	 * <p>
	 *     The endpoint is deprecated in the Mesh REST client, but the
	 *     alternative of using the Mesh monitoring client would require
	 *     more setup in the configuration, and require monitoring to be
	 *     enabled for Mesh.
	 * </p>
	 *
	 * @return The status {@code UNKNOWN} when the version could be determined,
	 * 		and {@code NOT_READY} or {@code FORBIDDEN} when Mesh could not be
	 * 		reached or the configured API key is invalid respectively
	 */
	private MeshStatus loadMeshVersion() {
		log.debug("Loading Mesh version info");

		MeshResponse<MeshServerInfoModel> response;

		try {
			response = meshClient.getApiInfo().getResponse().blockingGet();
		} catch (Exception e) {
			// Not logged as error, since this can happen during start-up when Mesh is not yet ready.
			log.debug("Could not reach Mesh: {}", e.getMessage());

			return MeshStatus.NOT_READY;
		}

		if (response.getStatusCode() != HttpResponseStatus.OK.code()) {
			log.error("FATAL: Invalid Mesh API key");

			return MeshStatus.FORBIDDEN;
		}

		boolean webrootFieldAvailable = Optional.ofNullable(response.getBody())
			.map(MeshServerInfoModel::getMeshVersion)
			.filter(this::checkMinimumMeshVersion)
			.isPresent();

		config.getMeshOptions().setWebrootFieldAvailable(webrootFieldAvailable);

		return MeshStatus.UNKNOWN;
	}

	/**
	 * Load the plugin information from Mesh and check the response status.
	 *
	 * <p>
	 *     When the configured Mesh API key is not a valid key for the admin
	 *     user, the returned status will be {@link MeshStatus#FORBIDDEN}.
	 * </p>
	 *
	 * <p>
	 *     When the plugin information could not be loaded for any other reason
	 *     the result will be {@link MeshStatus#NOT_READY}.
	 * </p>
	 *
	 * <p>
	 *     Otherwise the loaded plugins are placed in the provided
	 *     {@code plugins} map, and the returned status will be
	 *     {@link MeshStatus#UNKNOWN} (since the plugins still have to be
	 *     checked).
	 * </p>
	 *
	 * @param plugins The map to put the loaded plugins into
	 * @return {@code MeshStatus.FORBIDDEN}, {@code MeshStatus.NOT_READY} or
	 * 		{@code MeshStatus.UNKNOWN} when the API key is not valid, the plugin
	 * 		list could not be loaded, or the plugins were loaded respectively
	 */
	private MeshStatus loadPluginInfo(Map<PluginStatus, Collection<String>> plugins) {
		log.debug("Loading plugin information");

		MeshResponse<PluginListResponse> pluginResponse;

		try {
			pluginResponse = meshClient.findPlugins()
				.getResponse()
				.blockingGet();
		} catch (Exception e) {
			log.debug("Could not reach Mesh: {}", e.getMessage());

			return MeshStatus.NOT_READY;
		}

		int statusCode = pluginResponse.getStatusCode();

		if (statusCode == HttpResponseStatus.UNAUTHORIZED.code()) {
			log.error("FATAL: Invalid Mesh API key");

			return MeshStatus.FORBIDDEN;
		}

		if (statusCode == HttpResponseStatus.FORBIDDEN.code()) {
			log.error("FATAL: Mesh API key is not for admin user");

			return MeshStatus.FORBIDDEN;
		}

		if (statusCode != HttpResponseStatus.OK.code()) {
			log.warn("Could not load plugins (response status: {})", statusCode);

			return MeshStatus.NOT_READY;
		}

		pluginResponse.getBody().getData().forEach(plugin ->
			plugins.computeIfAbsent(plugin.getStatus(), ignored -> new ArrayList<>()).add(plugin.getId()));

		return MeshStatus.UNKNOWN;
	}

	/**
	 * Check if all {@link MeshOptions#getRequiredPlugins() required plugins}
	 * are available, and all plugins are properly registered.
	 *
	 * @param plugins The loaded plugins grouped by their current status
	 * @return {@code MeshStatus.READY} when all required plugins are available
	 * 		and all plugins are properly registered, and
	 * 		{@code MeshStatus.NOT_READY} otherwise
	 */
	private MeshStatus checkPlugins(Map<PluginStatus, Collection<String>> plugins) {
		MeshStatus status = MeshStatus.READY;

		if (plugins.containsKey(PluginStatus.FAILED)) {
			log.warn(
				"Failed plugins : [{}]",
				String.join(", ", plugins.get(PluginStatus.FAILED)));

			status = MeshStatus.NOT_READY;
			plugins.remove(PluginStatus.FAILED);
		}

		Collection<String> registered = plugins.containsKey(PluginStatus.REGISTERED)
			? plugins.remove(PluginStatus.REGISTERED)
			: Collections.emptySet();

		if (log.isDebugEnabled()) {
			log.debug("Registered plugins: [{}]", String.join(", ", registered));
		}

		Map<String, String> notReady = plugins.entrySet().parallelStream()
			.flatMap(entry -> entry.getValue().stream().map(plugin -> Pair.of(plugin, entry.getKey().name())))
			.collect(Collectors.toMap(Pair::getKey, Pair::getValue));

		requiredPlugins.stream()
			.filter(plugin -> !notReady.containsKey(plugin) && !registered.contains(plugin))
			.map(plugin -> {
				log.trace("Missing plugin: {}", plugin);

				return Pair.of(plugin, "MISSING");
			})
			.forEach(entry -> notReady.put(entry.getKey(), entry.getValue()));


		if (!notReady.isEmpty()) {
			log.debug(
				"Not yet registered plugins: [{}]",
				notReady.entrySet().stream()
					.map(e -> String.format("%s (%s)", e.getKey(), e.getValue()))
					.collect(Collectors.joining(", ")));

			status = MeshStatus.NOT_READY;
		}

		return status;
	}

	/**
	 * Compare the given string agains the minimum
	 * {@link #WEBROOT_FIELD_MIN_MESH_VERSION_MAJOR major},
	 * {@link #WEBROOT_FIELD_MIN_MESH_VERSION_MINOR minor}, and
	 * {@link #WEBROOT_FIELD_MIN_MESH_VERSION_PATCH patch} version of the
	 * minimum Mesh version for using webroot fields.
	 *
	 * <p>
	 *     This check will very likely need changes when Mesh 1.7.x or later
	 *     becomes viable for CMP, since early versions of 1.7.x do <em>not</em>
	 *     support webroot fields.
	 * </p>
	 *
	 * <p>
	 *     Note that when the actual version is a SNAPSHOT version, the patch
	 *     level has to be greater and not only <em>not</em> greater or equal
	 *     to the minimum version.
	 * </p>
	 *
	 * @param actual The actual Mesh version.
	 * @return {@code true} when the minimum version is met, and {@code false}
	 * 		otherwise
	 */
	private boolean checkMinimumMeshVersion(String actual) {
		if (actual == null) {
			log.warn("Cannot determine actual Mesh version");

			return false;
		}

		boolean snapshot = actual.endsWith(SNAPSHOT_SUFFIX);
		String[] parts = actual.split("\\.");
		int major, minor, patch;

		try {
			major = Integer.parseInt(parts[0]);
			minor = Integer.parseInt(parts[1]);
			patch = Integer.parseInt(snapshot ? StringUtils.removeEnd(parts[2], SNAPSHOT_SUFFIX) : parts[2]);
		} catch (Exception e) {
			log.error("Could not parse actual version: {}", actual, e);

			return false;
		}

		// The webrootfield endpoint was added, when Mesh 1.7.x was already in development. In the 1.7.x branch, the
		// feature was added in 1.7.7. The check at the end does not work for 1.7.x so we check the special case
		// explicitly.
		if (major == 1 && minor == 7) {
			return patch >= 7;
		}

		return major >= WEBROOT_FIELD_MIN_MESH_VERSION_MAJOR
			&& (major > WEBROOT_FIELD_MIN_MESH_VERSION_MAJOR || minor >= WEBROOT_FIELD_MIN_MESH_VERSION_MINOR)
			&& (major > WEBROOT_FIELD_MIN_MESH_VERSION_MAJOR || minor > WEBROOT_FIELD_MIN_MESH_VERSION_MINOR
				|| (snapshot && patch > WEBROOT_FIELD_MIN_MESH_VERSION_PATCH) || patch >= WEBROOT_FIELD_MIN_MESH_VERSION_PATCH);
	}
}
