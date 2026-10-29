package com.gentics.mesh.portal.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provider for the Gentics Portal | java version information.
 */
public class PortalVersion {

	private static final Logger log = LoggerFactory.getLogger(PortalVersion.class);
	private static final AtomicReference<BuildInfo> buildInfo = new AtomicReference<>(null);

	/**
	 * Get the build information.
	 *
	 * <p>
	 *     The information is initially loaded from the
	 *     <em>/gpj.build.properties</em> file in the resources.
	 * </p>
	 *
	 * @return The loaded build information
	 */
	public static BuildInfo buildInfo() {
		if (buildInfo.get() == null) {
			Properties buildProperties = new Properties();

			try {
				buildProperties.load(PortalVersion.class.getResourceAsStream("/gpj.build.properties"));
				buildInfo.set(new BuildInfo(buildProperties));
			} catch (Exception e) {
				log.error("Could not load build information: {}", e.getMessage(), e);
				buildInfo.set(new BuildInfo("unknown", "unknown"));
			}
		}

		return buildInfo.get();
	}

	/**
	 * Get just the version from the build information.
	 *
	 * @see #buildInfo()
	 * @return The Gentics Portal | java version
	 */
	public static String plainVersion() {
		return buildInfo().getVersion();
	}
}
