package com.gentics.testcontainers;

import java.time.Duration;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;
import org.testcontainers.utility.MountableFile;

/**
 * Docker test container for Keycloak.
 */
public class KeycloakContainer extends GenericContainer<KeycloakContainer> {

	// Oldest version with compatible docker image.
	// private static final String VERSION = "9.0.3";
	private static final String DEFAULT_VERSION = "25";

	private static final Map<String, String> latestMajorVersions = new HashMap<>() {{
		put("25", "25.0.2");
		put("24", "24.0.5");
		put("23", "23.0.7");
		put("22", "22.0.5");
		put("21", "21.1.2");
		put("20", "20.0.5");
		put("19", "19.0.3");
		put("18", "18.0.2");
		put("17", "17.0.1");
		put("16", "16.1.1");
		put("15", "15.1.1");
		put("14", "14.0.0");
		put("13", "13.0.1");
		put("12", "12.0.4");
		put("11", "11.0.3");
		put("10", "10.0.2");
		put("9", "9.0.3");
	}};

	private static final Logger log = LoggerFactory.getLogger(KeycloakContainer.class);

	private Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);

	private final int majorVersion;
	private final String classPathToConfig;
	private final List<String> startArguments;
	private String realmBaseUrl;

	/**
	 * Create Keycloak container with the given specifications.
	 *
	 * <p>
	 *     <em>NOTE</em> that for most scenarios, specifying just the realm configuration and Keycloak version should
	 *     be sufficient.
	 * </p>
	 *
	 * @param classPathToConfig The path to the .json file containing the test realm which should be imported on start-up.
	 * @param containerName The name of the docker image (this can include the domain like {@code "quay.io/keycloak/keycloak"}.
	 * @param keycloakVersion The Keycloak version to use (must be a valid tag for the {@code containerName}).
	 * @param startArguments The arguments for starting the container.
	 */
	public KeycloakContainer(String classPathToConfig, String containerName, String keycloakVersion, List<String> startArguments) {
		super(String.format("%s:%s", containerName, sanitizeVersion(keycloakVersion)));

		this.majorVersion = getMajorVersion(sanitizeVersion(keycloakVersion));
		this.classPathToConfig = classPathToConfig;
		this.startArguments = startArguments;
	}

	/**
	 * Creates a Keycloak container with the given realm configuration and Keycloak version.
	 *
	 * <p>
	 *     The image name defaults to {@code "quay.io/keycloak/keycloak"} and the start arguments will be determined
	 *     automatically for the given version.
	 * </p>
	 *
	 * @see #getStartArguments(int)
	 *
	 * @param classPathToConfig The path to the .json file containing the test realm which should be imported on start-up.
	 * @param keycloakVersion The Keycloak version to use.
	 */
	public KeycloakContainer(String classPathToConfig, String keycloakVersion) {
		this(classPathToConfig, "quay.io/keycloak/keycloak", keycloakVersion, getStartArguments(getMajorVersion(keycloakVersion)));
	}

	/**
	 * Creates a Keycloak container with the given realm configuration.
	 *
	 * <p>
	 *     The image name defaults to {@code "quay.io/keycloak/keycloak"}, the default version is 25.0.2 and the start
	 *     arguments will be determined automatically.
	 * </p>
	 *
	 * @param classPathToConfig The path to the .json file containing the test realm which should be imported on start-up.
	 */
	public KeycloakContainer(String classPathToConfig) {
		this(classPathToConfig, DEFAULT_VERSION);
	}

	/**
	 * Extract the major version from the given version string.
	 *
	 * @param version The complete version string.
	 * @return The major version from the given version string.
	 */
	protected static int getMajorVersion(String version) {
		String[] versionParts = version.split("\\.");

		return Integer.parseInt(versionParts[0]);
	}

	/**
	 * Determines the latest concrete version for a given major version.
	 *
	 * <p>
	 *     <em>NOTE:</em> when {@code version} contains more than just the major version (e.g. contains at least one
	 *     {@code '.'}), this method will just return the passed {@code version}.
	 * </p>
	 *
	 * @param version The major version to get a concrete version number for.
	 * @return A concrete Keycloak version for the given major version.
	 */
	protected static String sanitizeVersion(String version) {
		String[] versionParts = version.split("\\.");

		if (versionParts.length == 1 && latestMajorVersions.containsKey(versionParts[0])) {
			return latestMajorVersions.get(versionParts[0]);
		}

		return version;
	}

	/**
	 * Get the correct start arguments for the given major version.
	 *
	 * @param majorVersion The major version to get start arguments for.
	 * @return The list of start arguments for the specified major version.
	 */
	protected static List<String> getStartArguments(int majorVersion) {
		return majorVersion < 17 ? List.of("-b", "0.0.0.0") : List.of("--verbose", "start-dev");
	}

	/**
	 * Get the path to the .json file containing the test realm to be imported.
	 *
	 * @param majorVersion The major version to get the import path for.
	 * @return The path to the .json file containing the test realm to be imported.
	 */
	protected static String getImportPath(int majorVersion) {
		return majorVersion < 18 ? "/realm/realm.json" : "/opt/keycloak/data/import/realm.json";
	}

	/**
	 * Get the base URL for a accessing realms.
	 *
	 * <p>
	 *     The returned string will contain the correct host and port and, depending on the Keycloak version, the
	 *     {@code /auth} prefix. The realm name itself must be appended to the resulting base URL.
	 * </p>
	 *
	 * @return The base URL for a accessing realms.
	 */
	public String getRealmBaseUrl() {
		if (realmBaseUrl == null) {

			StringBuilder builder = new StringBuilder("http://")
					.append(getHost())
					.append(":")
					.append(getMappedPort(8080));

			if (majorVersion < 17) {
				builder.append("/auth");
			}

			builder.append("/realms");

			realmBaseUrl = builder.toString();
		}

		return realmBaseUrl;
	}

	@Override
	protected void configure() {
		addEnv("KEYCLOAK_USER", "admin");
		addEnv("KEYCLOAK_PASSWORD", "admin");

		withExposedPorts(8080);
		withLogConsumer(logConsumer);
		withStartupTimeout(Duration.ofSeconds(120));
		setStartupAttempts(1);

		List<String> args = new ArrayList<>();
		startArguments.stream().forEach(args::add);
		args.add("-Dkeycloak.migration.usersExportStrategy=SAME_FILE");
		args.add("-Dkeycloak.migration.strategy=OVERWRITE_EXISTING");

		if (StringUtils.isNotEmpty(classPathToConfig)) {
			String importPath = getImportPath(majorVersion);

			withCopyFileToContainer(MountableFile.forClasspathResource(classPathToConfig), importPath);

			if (majorVersion < 9) {
				args.add("-Dkeycloak.migration.action=import");
				args.add("-Dkeycloak.migration.provider=singleFile");
				args.add(String.format("-Dkeycloak.migration.file=%s", importPath));
			} else if (majorVersion < 18) {
				args.add(String.format("-Dkeycloak.import=%s", importPath));
			} else {
				args.add("--import-realm");
			}
		}

		withCommand(args.stream().toArray(String[]::new));
	}

	/**
	 * Wait until the Keycloak port is reachable.
	 *
	 * @return
	 */
	public KeycloakContainer waitStartup() {
		waitingFor(Wait.forListeningPort());

		return this;
	}

	/**
	 * Get the host for the started Keycloak container.
	 * @return
	 */
	public String getHost() {
		String containerHost = System.getenv("CONTAINER_HOST");

		if (containerHost != null) {
			return containerHost;
		}

		return "localhost";
	}
}
