package com.gentics.mesh.portal.client;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import javax.inject.Inject;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

/**
 * Provider that can return either the admin client or the user client (if available)
 */
public class MeshRestClientProvider {
	private final Logger log = LoggerFactory.getLogger(MeshRestClientProvider.class);

	private final MeshRestClient adminClient;
	private final MeshRestClient anonymousClient;

	@Inject
	public MeshRestClientProvider(MeshRestClient adminClient, PortalConfig config) {
		MeshOptions options = config.getMeshOptions();

		MeshRestClientConfig meshRestClientConfig = MeshRestClientConfig.newConfig()
				.setHost(options.getHost())
				.setPort(options.getPort())
				.setSsl(options.isSsl())
				.setProtocolVersion(options.getProtocolVersion())
				.build();

		this.adminClient = adminClient;
		this.anonymousClient = MeshRestClient.create(meshRestClientConfig);
	}

	/**
	 * Return the admin client
	 * 
	 * @return admin client
	 */
	public MeshRestClient get() {
		return get(null);
	}

	/**
	 * Return the user client, if it was set in the routing context, or the
	 * admin client otherwise
	 * 
	 * @param rc
	 *            routing context
	 * @return user client, if available, or admin client otherwise
	 */
	public MeshRestClient get(RoutingContext rc) {
		if (rc == null) {
			log.trace("Providing admin client");
			return adminClient;
		} else {
			PortalRoutingContext prc = wrap(rc);
			MeshRestClient userClient = prc.getUserClient();
			if (userClient != null) {
				log.trace("Providing user client.");
				return userClient;
			} else {
				log.trace("User client not found, providing admin client");
				return adminClient;
			}
		}
	}

	/**
	 * Provides the client that is used if an anonymous user performs a request.
	 * @param rc
	 * @return
	 */
	public MeshRestClient getDefaultClient(RoutingContext rc) {
		if (rc == null) {
			log.trace("Providing admin client");
			return adminClient;
		} else {
			PortalRoutingContext prc = wrap(rc);
			MeshRestClient userClient = prc.getUserClient();
			if (userClient != null) {
				log.trace("Providing anonymous client.");
				return anonymousClient;
			} else {
				log.trace("User client not found, providing admin client");
				return adminClient;
			}
		}
	}
}
