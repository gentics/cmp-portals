package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.portal.Portal;
import com.gentics.mesh.portal.api.PortalEvents;
import com.gentics.mesh.portal.api.PortalVersion;
import com.gentics.mesh.portal.api.server.BootstrapInitializer;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.server.dagger.BaseComponent;
import io.reactivex.Observable;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Create the portal component, wait for Mesh to be ready and start the
 * {@link BootstrapInitializer}.
 */
public class PortalStarter {

	public static void start(Supplier<BaseComponent> contextProvider) {
		Logger log = LoggerFactory.getLogger(PortalStarter.class);

		log.info("Starting Gentics Portal | java {}", PortalVersion.plainVersion());

		BaseComponent context = Portal.create(contextProvider);

		// Load the portal implementation before the start-up checks, which already need the queries.
		context.portalImplementation().start();

		boolean shouldStart = performStartupChecks(context, log);

		if (shouldStart) {
			try {
				BootstrapInitializer boot = context.boot();

				registerShutdownHook(boot, context.portalInfo());
				boot.start();
			} catch (Exception e) {
				log.error("FATAL: Could not start portal: {}", e.getMessage(), e);
			}
		} else {
			log.info("Portal | java start-up was aborted");
		}
	}

	/**
	 * Start the start-up checks and return whether the portal startup
	 * should continue.
	 *
	 * @param context The booting context.
	 * @param log The log to use.
	 * @return {@code true} when the portal startup should continue, and
	 * 		{@code false} when it should abort.
	 */
	private static boolean performStartupChecks(BaseComponent context, Logger log) {
		boolean shouldStart;
		List<PortalFunctionStartupChecker> startupChecks = createStartupChecks(context);

		switch (context.portalConfig().getMonitoringOptions().getStartupCheckMode()) {
		case OFF:
			// Do not perform any checks, start let the poral start.
			shouldStart = true;

			break;

		default:
		case ABORT:
			// Only start the portal when the checks pass.
			shouldStart = startupChecks.stream().allMatch(PortalFunctionStartupChecker::check);

			break;

		case UNREADY:
			// Set the ready state according to the result of the start-up checks, which are repeated
			// after each update of the portal implementation ...
			new UnreadyStartupChecks(
				context.portalInfo(),
				startupChecks,
				() -> createStartupChecks(context),
				implementationUpdates(context.vertx())).start();

			// ... and let the portal start either way.
			shouldStart = true;
		}

		return shouldStart;
	}

	/**
	 * Create the start-up checks.
	 *
	 * @param context The booting context.
	 * @return The start-up checks.
	 */
	private static List<PortalFunctionStartupChecker> createStartupChecks(BaseComponent context) {
		return Arrays.asList(
			new MeshStatusChecker(context),
			new QueryStoreChecker(context));
	}

	/**
	 * Create an observable emitting the keyword of the portal implementation for each
	 * {@link PortalEvents#PORTAL_IMPLEMENTATION_UPDATED update}.
	 *
	 * @param vertx The Vert.x instance, whose event bus is used.
	 * @return The observable of portal implementation updates.
	 */
	static Observable<Object> implementationUpdates(Vertx vertx) {
		return Observable.create(emitter -> {
			MessageConsumer<Object> consumer = vertx.eventBus()
				.localConsumer(PortalEvents.PORTAL_IMPLEMENTATION_UPDATED.address(), message -> emitter.onNext(message.body()));

			emitter.setCancellable(consumer::unregister);
		});
	}

	/**
	 * Register a hook to shut down the portal properly when the JVM terminates.
	 *
	 * <p>
	 *     The started shutdown hook will call {@code portalInfo.shutDown()}
	 *     and {@code boot.stop()}.
	 * </p>
	 *
	 * @param boot The portals {@link BootstrapInitializer}.
	 * @param portalInfo The portals {@link PortalInfo}.
	 */
	private static void registerShutdownHook(BootstrapInitializer boot, PortalInfo portalInfo) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				portalInfo.shutdown();
			} catch (Exception e) {
				// Use system err since logging system may have been shutdown.
				System.err.printf("Error while shutting down Portal | java: %s\n", e.getMessage());
				e.printStackTrace();
			}
		}));
	}
}
