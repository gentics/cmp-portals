package com.gentics.mesh.portal.server;

import com.gentics.mesh.portal.DataProviderVerticle;
import com.gentics.mesh.portal.authentication.LogoutAllHandler;
import com.gentics.mesh.portal.authentication.UserClientHandler;
import com.gentics.mesh.portal.authentication.oauth2.KeycloakAuthenticationHandler;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.forms.FormsHandler;
import com.gentics.mesh.portal.monitoring.MonitoringServiceVerticle;
import com.gentics.mesh.portal.search.SearchHandler;
import com.gentics.mesh.portal.server.core.AbstractBootstrapInitializer;
import com.gentics.mesh.portal.server.core.HandlerProvider;
import com.gentics.mesh.portal.server.core.PortalHttpVerticle;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

public class BasicBootstrapInitializer extends AbstractBootstrapInitializer {

	private static final Logger log = LoggerFactory.getLogger(BasicBootstrapInitializer.class);

	@Inject
	public DataProviderVerticle dataProvider;

	@Inject
	public MonitoringServiceVerticle monitoringService;

	@Inject
	public KeycloakAuthenticationHandler authenticationHandler;

	@Inject
	public UserClientHandler userClientHandler;

	@Inject
	public LogoutAllHandler logoutAllHandler;

	@Inject
	public FormsHandler formsHandler;

	@Inject
	public SearchHandler searchHandler;

	@Inject
	public BasicBootstrapInitializer(
			PortalConfig config,
			Vertx vertx,
			Provider<PortalHttpVerticle> verticleProvider,
			MeshRestClient meshClient,
			HandlerProvider handlerProvider) {
		super(config, vertx, verticleProvider, meshClient, handlerProvider);
	}

	@Override
	public void start() throws Exception {
		log.info("Starting Portal | java server");

		vertx.deployVerticle(dataProvider, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).andThen(deploymentHandler);
		vertx.deployVerticle(monitoringService).andThen(deploymentHandler);

		deployHttpVerticles(router -> {
			authenticationHandler.addRoutes(router);
			userClientHandler.addRoutes(router);
			logoutAllHandler.addRoutes(router);
			formsHandler.addRoutes(router);
			searchHandler.addRoutes(router);

			router.get("/api/protected")
				.handler(rc -> rc.response().end("+OK: All clear"));
		});
	}
}
