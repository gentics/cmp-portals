package com.gentics.mesh.portal.api;

/**
 * Events used in the portal.
 */
public enum PortalEvents {
	/**
	 * A verticle with a HTTP server has been deployed.
	 *
	 * <p>
	 *     The event message is a {@link io.vertx.core.json.JsonObject} with the following fields:
	 *     <ul>
	 *         <li>id: the deployment ID of the deployed verticle</li>
	 *         <li>verticle: the classname of the deployed verticle</li>
	 *         <li>port: the actual port the verticles server listens on</li>
	 *     </ul>
	 * </p>
	 */
	SERVER_VERTICLE_DEPLOYED("mesh.portal.server_verticle.deployed"),

	/**
	 * The files of the configured portal implementation have been updated from Mesh.
	 *
	 * <p>
	 *     The event message is the keyword of the portal implementation. Receivers
	 *     should clear caches containing data loaded from templates, queries or i18n files.
	 * </p>
	 */
	PORTAL_IMPLEMENTATION_UPDATED("mesh.portal.implementation.updated");

	private final String address;

	PortalEvents(String address) {
		this.address = address;
	}

	public String address() {
		return address;
	}
}
