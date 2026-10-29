package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.core.rest.branch.BranchListResponse;
import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.core.rest.branch.BranchUpdateRequest;
import com.gentics.mesh.parameter.client.PagingParametersImpl;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.reactivex.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;

import javax.inject.Provider;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Abstract implementation of a portal bootstrap initializer.
 */
public abstract class AbstractBootstrapInitializer implements BootstrapInitializer {

	private static final Logger log = LoggerFactory.getLogger(AbstractBootstrapInitializer.class);

	protected final Vertx vertx;
	protected final PortalConfig config;
	protected List<String> deploymentIds = new ArrayList<>();
	protected final Handler<AsyncResult<String>> deploymentHandler = event -> {
		if (!this.aborted && event.failed()) {
			abort(event.cause());
		}
		if (!event.failed()) {
			deploymentIds.add(event.result());
		}
	};

	private final Provider<PortalHttpVerticle> verticleProvider;

	private boolean aborted = false;

	private HandlerProvider handlerProvider;

	protected final MeshRestClient meshClient;

	public AbstractBootstrapInitializer(
		PortalConfig config,
		io.vertx.core.Vertx vertx,
		Provider<PortalHttpVerticle> verticleProvider,
		MeshRestClient meshClient,
		HandlerProvider handlerProvider) {
		this.config = config;
		this.vertx = vertx;
		this.verticleProvider = verticleProvider;
		this.meshClient = meshClient;
		this.handlerProvider = handlerProvider;

		meshClient
			.findBranches(config.getMeshProject(), new PagingParametersImpl())
			.toSingle()
			.map(BranchListResponse::getData)
			.subscribe(branches -> checkBranches(meshClient, branches));
	}

	/**
	 * Set the {@link BranchResponse#getHostname() hostname} and {@link BranchResponse#getSsl SSL information} of the given branch to the values from the
	 * {@link PortalConfig#getServerUrl()} portal configuration}.
	 *
	 * The values will only be set if they are currently empty in the branch. When they are already specified but differ from the portal configuration a warning
	 * will be logged.
	 *
	 * @param meshClient
	 *            The Mesh client to use
	 * @param branch
	 *            The branch to update
	 */
	private void updateBranchHost(MeshRestClient meshClient, BranchResponse branch) {
		URL serverUrl = config.getServerUrl();
		String serverHost = serverUrl.getHost();
		int serverPort = serverUrl.getPort();
		boolean serverSsl = serverUrl.getProtocol().equalsIgnoreCase("https");
		String branchHost = branch.getHostname();
		Boolean branchSsl = branch.getSsl();

		if (serverPort < 0) {
			serverPort = serverUrl.getDefaultPort();
		}

		if (branchHost == null) {
			branchHost = "";
		}

		if (serverPort != serverUrl.getDefaultPort() || branchHost.contains(":")) {
			serverHost += ":" + serverPort;
		}

		if ((!branchHost.isEmpty() && !branchHost.equals(serverHost))
			|| (branchSsl != null && !branchSsl == serverSsl)) {
			log.warn("Server host configuration missmatch for branch {}:", branch.getName());
			log.warn("  Mesh:   http{}://{}", Boolean.TRUE.equals(branchSsl) ? "s" : "", branchHost);
			log.warn("  Portal: http{}://{}", serverSsl ? "s" : "", serverHost);

			return;
		}

		if (branchHost.isEmpty() || branchSsl == null) {
			if (log.isInfoEnabled()) {
				log.info(
					"Setting server host for {} branch {} to {}://{}",
					config.getMeshProject(),
					branch.getName(),
					serverUrl.getProtocol(),
					serverHost);
			}

			BranchUpdateRequest request = new BranchUpdateRequest();

			request.setHostname(serverHost).setSsl(serverSsl);

			meshClient
				.updateBranch(config.getMeshProject(), branch.getUuid(), request)
				.toSingle()
				.doOnError(e -> log.error("Could not update host information in branch: {}", e.getMessage(), e))
				.subscribe();
		}
	}

	/**
	 * Check branches of the Mesh project for missing host configurations.
	 *
	 * When there is only one branch, and it has neither its {@link BranchResponse#getHostname() hostname} nor its {@link BranchResponse#getSsl SSL information}
	 * set, they will be set to the values from the {@link PortalConfig#getServerUrl() portal configuration}.
	 *
	 * @see #updateBranchHost
	 * @param meshClient
	 *            The Mesh client to use
	 * @param branches
	 *            The branches to check
	 */
	private void checkBranches(MeshRestClient meshClient, List<BranchResponse> branches) {
		String project = config.getMeshProject();
		boolean branchFound = false;
		URL serverUrl = config.getServerUrl();
		int portFromUrl = serverUrl.getPort();

		if (config.getServerPort() != portFromUrl) {
			log.info("The portal server runs on a different port than specified in the serverUrl");
			log.info("A reverse proxy will be needed to correctly resolve links");
		}

		if (branches.size() == 1) {
			BranchResponse branch = branches.get(0);

			if (project.equals(branch.getName())) {
				branchFound = true;
				updateBranchHost(meshClient, branch);
			}
		} else {
			String serverHost = serverUrl.getHost();
			boolean serverSsl = serverUrl.getProtocol().equals("https");

			for (BranchResponse branch : branches) {
				String host = branch.getHostname();
				Boolean ssl = branch.getSsl();
				String fullServerHost = serverHost;

				if (portFromUrl != serverUrl.getDefaultPort() || host.contains(":")) {
					fullServerHost += ":" + portFromUrl;
				}

				if (host == null || host.isEmpty() || ssl == null) {
					log.warn("No host configured for {} branch {}", project, branch.getName());
				} else if (host.equals(fullServerHost) || ssl == serverSsl) {
					branchFound = true;
				}
			}
		}

		if (!branchFound) {
			log.error("No branch found for this portals host: {}", config.getServerUrl());
		}
	}

	@Override
	public void deployHttpVerticles(Consumer<Router> routeInjector) {
		int poolSize = config.getVertxOptions().getEventPoolSize();

		for (int i = 1; i <= poolSize && !aborted; i++) {
			log.info("Deploying HTTP verticle instance {}", i);

			PortalHttpVerticle verticle = verticleProvider.get();
			Handler<AsyncResult<String>> deploymentHandler = event -> {
				if (event.failed()) {
					abort(event.cause());
				} else {
					routeInjector.accept(verticle.getRouter());
				}
			};

			vertx.deployVerticle(verticle).andThen(deploymentHandler);
		}
	}

	/**
	 * Abort portal startup and close the Vert.x instance.
	 *
	 * @param cause
	 *            The cause for aborting portal startup
	 */
	private void abort(Throwable cause) {
		log.error("FATAL: Verticle deployment failed, aborting startup", cause);
		aborted = true;
		vertx.close();
	}

	@Override
	public void stop() {
		log.info("Stopping Portal | java");

		handlerProvider.okHttpClient().dispatcher().cancelAll();
		handlerProvider.cachingHandler().disable();
		handlerProvider.meshWebsocket().close();

		Observable.fromIterable(vertx.deploymentIDs())
			.flatMapCompletable(id -> rxVertx().rxUndeploy(id))
			.andThen(rxVertx().rxClose())
			.blockingAwait(10, TimeUnit.SECONDS);

		handlerProvider.okHttpClient().dispatcher().executorService().shutdown();
		handlerProvider.okHttpClient().connectionPool().evictAll();

		// TODO OkHttpClient is actually unable to close gracefully, and never will be per design.
		// https://github.com/square/okhttp/issues/3372
		System.exit(0);
	}

	/**
	 * Returns RX variant of Vert.x
	 *
	 * @return
	 */
	protected io.vertx.reactivex.core.Vertx rxVertx() {
		return new io.vertx.reactivex.core.Vertx(vertx);
	}

}
