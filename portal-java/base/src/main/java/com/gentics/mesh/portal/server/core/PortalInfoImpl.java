package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.portal.api.PortalEvents;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.api.server.PortalState;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MonitoringOptions;
import com.gentics.mesh.portal.config.option.VertxOptions;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.MiscUtils;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link PortalInfo} listening for {@link PortalEvents#SERVER_VERTICLE_DEPLOYED deployed events} of
 * the {@link PortalHttpVerticle HTTP verticles} to determine the state of the portal.
 */
@Singleton
public class PortalInfoImpl implements PortalInfo {

	private static final Logger log = LoggerFactory.getLogger(PortalInfo.class);
	private static final String HTTP_VERTICLE = PortalHttpVerticle.class.getName();

	/**
	 * The total number of HTTP verticles to be deployed is the configured
	 * {@link com.gentics.mesh.portal.config.option.VertxOptions#getEventPoolSize() event pool size}.
	 */
	private final int totalHttpVerticles;

	private final boolean conservativePortalState;

	/** The current state of the portal. */
	private PortalState state = PortalState.STARTING;

	/** The number of already deployed HTTP verticles. */
	private int startedHttpVerticles = 0;

	/** The file which last modified timestamp will be set regularly as long as the portal is alive. */
	private File liveFile;

	/** Executor to update the modification time of the {@link #liveFile} regularly. */
	private ScheduledExecutorService executor;

	/**
	 * Default constructor.
	 *
	 * <p>
	 *     Registers a {@link #verticleDeployed(Message) consumer} for {@link PortalEvents#SERVER_VERTICLE_DEPLOYED}
	 *     events from the Vert.x eventbus.
	 * </p>
	 *
	 * @param config The portal configuration
	 * @param vertx The Vert.x instance of the portal
	 */
	@Inject
	public PortalInfoImpl(PortalConfig config, Vertx vertx) {
		VertxOptions options = config.getVertxOptions();
		MonitoringOptions monitoringOptions = config.getMonitoringOptions();

		this.totalHttpVerticles = options.getEventPoolSize();
		this.conservativePortalState = monitoringOptions.isConservativePortalState();
		this.liveFile = new File(monitoringOptions.getLivePath());

		startLiveFileUpdates(monitoringOptions.getLiveUpdateInterval());

		log.debug("Waiting for {} HTTP verticles to be deployed", totalHttpVerticles);
		vertx.eventBus().localConsumer(PortalEvents.SERVER_VERTICLE_DEPLOYED.address(), this::verticleDeployed);
	}

	@Override
	public PortalState getState() {
		return state;
	}

	@Override
	public void setState(PortalState state) {
		this.state = state;
	}

	@Override
	public void checkState(Throwable error) {
		if (!conservativePortalState || state == PortalState.STARTUP_CHECK_ERROR) {
			// The configuration says not to change the state based on errors or there already was an unrecoverable
			// error so there is nothing to do.
			return;
		}

		if (MiscUtils.isSocketError(error)) {
			state = PortalState.NETWORK_ERROR;

			log.warn("Portal state changed to {} due to Mesh connection problem: {}", state, error.getMessage(), error);
		} else if (log.isDebugEnabled()) {
			log.debug("Not changing portal state due to {}: {}", error.getClass().getSimpleName(), error.getMessage());
		}
	}

	@Override
	public void resetErrors() {
		if (state != PortalState.STARTING && state != PortalState.STARTUP_CHECK_ERROR) {
			state = PortalState.READY;
		}
	}

	@Override
	public void shutdown() {
		setState(PortalState.STOPPING);

		if (executor != null) {
			executor.shutdown();
		}
	}

	/**
	 * Create the {@link #liveFile} if necessary and start the scheduler task
	 * to update its modified timestamp regularly.
	 *
	 * @param updateDelay The initial delay as well as the interval for updating
	 * 		the modified timestamp of the {@code liveFile}.
	 */
	private void startLiveFileUpdates(int updateDelay) {
		File liveDir = liveFile.getParentFile();

		if (liveDir != null && !liveDir.exists() && !liveDir.mkdirs()) {
			log.error("Could not create parent directory for live file \"{}\"", liveDir.getAbsolutePath());
		}

		try {
			liveFile.createNewFile();
			executor = Executors.newSingleThreadScheduledExecutor();
			executor.scheduleWithFixedDelay(this::updateLiveFile, updateDelay, updateDelay, TimeUnit.SECONDS);
		} catch (IOException e) {
			log.error("Could not create live file \"{}\": {}", liveFile.getAbsolutePath(), e.getMessage(), e);
		}
	}

	/**
	 * Update the modified timestamp of the {@link #liveFile} if the portal is
	 * not in an error state.
	 */
	private synchronized void updateLiveFile() {
		if (state == PortalState.STARTING || state == PortalState.READY) {
			liveFile.setLastModified(System.currentTimeMillis());
			// Tests wait on this object to avoid Thread.sleep() calls in test methods.
			notifyAll();
		}
	}

	/**
	 * Checks if the deployed verticle is a {@link PortalHttpVerticle} and changes the {@link #state} to
	 * {@link PortalState#READY READY} when all HTTP verticles have been deployed.
	 *
	 * @param msg The event data
	 */
	private void verticleDeployed(Message<JsonObject> msg) {
		String verticle = JsonUtils.getValue(msg.body(), "verticle", "");

		if (verticle.equals(HTTP_VERTICLE) && ++startedHttpVerticles >= totalHttpVerticles && state == PortalState.STARTING) {
			log.info("All HTTP verticles deployed, portal is ready");
			state = PortalState.READY;
		}
	}
}
