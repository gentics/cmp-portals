package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.authentication.PortalUser;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.rest.client.MeshRestClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import okhttp3.OkHttpClient;

import javax.inject.Inject;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Handler that injects an instance of {@link MeshRestClient} (as user client)
 * into the routing context.
 *
 * <p>
 *     The user client instance can be fetched via
 *     {@link MeshRestClientProvider#get(RoutingContext)}.
 * </p>
 *
 * <p>
 *     By default, the user client will make anonymous requests to Mesh. If the
 *     {@link AuthenticationBaseHandler authentication handler} creates a
 *     user which provides a {@link PortalUser#meshAccessToken() Mesh access token},
 *     that token will be set in the user client as the API key.
 * </p>
 */
public class UserClientHandler extends AbstractBaseHandler {

	private final Logger log = LoggerFactory.getLogger(UserClientHandler.class);

	private final PortalConfig config;
	private final OkHttpClient okClient;

	@Inject
	public UserClientHandler(PortalConfig config, OkHttpClient okClient) {
		this.config = config;
		this.okClient = okClient;
	}

	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);

		// create and set user client
		MeshOptions meshOptions = config.getMeshOptions();

		MeshRestClientConfig clientConfig = MeshRestClientConfig.newConfig()
			.setHost(meshOptions.getHost())
			.setPort(meshOptions.getPort())
			.setSsl(meshOptions.isSsl())
			.setProtocolVersion(meshOptions.getProtocolVersion())
			.build();

		MeshRestClient meshClient = MeshRestClient.create(clientConfig, okClient);
		prc.setUserClient(meshClient);

		// Check if access token is already set for user (in cases, were the
		// user was already set into the routing context before).
		User user = rc.user();
		String accessToken = null;

		if (user instanceof PortalUser) {
			accessToken = ((PortalUser) user).meshAccessToken();

			if (accessToken != null) {
				meshClient.setAPIKey(accessToken);
			}
		} else if (user != null && log.isWarnEnabled()) {
			log.warn("Unexpected User type: {}", user.getClass().getName());
		}

		if (log.isTraceEnabled()) {
			log.trace("API token for user client: {}", accessToken == null ? "(none)" : accessToken);
		} else if (log.isDebugEnabled()) {
			log.debug("API token for user client available: {}", accessToken == null ? "no" : "yes");
		}

		prc.next();
	}

	@Override
	public void addRoutes(Router router) {
		/*
		 Doing this during AUTHENTICATION_ROUTES is not safe, because the
		 Keycloak authentication handler will register routes only after
		 the discovery is complete. So we cannot rely on the order in which
		 addRoutes() methods of handlers are called, because the routes for
		 Keycloak handler will be added after the routes in the
		 BoostrapInitializer.
		*/
		router.route()
			.order(RouteOrder.BEFORE_CACHING_ROUTES.getOrder())
			.handler(this);
	}
}
