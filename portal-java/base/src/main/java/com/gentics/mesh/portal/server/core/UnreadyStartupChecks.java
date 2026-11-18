package com.gentics.mesh.portal.server.core;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.api.server.PortalState;
import com.gentics.mesh.portal.config.StartupCheckMode;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Start-up checks for the {@link StartupCheckMode#UNREADY UNREADY} mode.
 *
 * <p>
 *     The checks are performed once on startup and again after each update of the portal implementation. A failing
 *     run sets the portal state to {@link PortalState#STARTUP_CHECK_ERROR}, a passing run after a failed one sets it
 *     back to {@link PortalState#READY}. Runs never overlap, so the state reflects the most recent run.
 * </p>
 */
public class UnreadyStartupChecks {

	private static final Logger log = LoggerFactory.getLogger(UnreadyStartupChecks.class);

	private final PortalInfo portalInfo;
	private final List<PortalFunctionStartupChecker> initialChecks;
	private final Supplier<List<PortalFunctionStartupChecker>> checksFactory;
	private final Observable<?> implementationUpdates;
	private final Scheduler scheduler;

	/**
	 * Create an instance, which repeats the checks on {@link Schedulers#io()}.
	 *
	 * @param portalInfo portal info to update the state of
	 * @param initialChecks checks for the run on startup
	 * @param checksFactory factory for new checks for each repeated run (the checks may load their data on creation)
	 * @param implementationUpdates emits an item for each update of the portal implementation
	 */
	public UnreadyStartupChecks(PortalInfo portalInfo, List<PortalFunctionStartupChecker> initialChecks,
			Supplier<List<PortalFunctionStartupChecker>> checksFactory, Observable<?> implementationUpdates) {
		this(portalInfo, initialChecks, checksFactory, implementationUpdates, Schedulers.io());
	}

	UnreadyStartupChecks(PortalInfo portalInfo, List<PortalFunctionStartupChecker> initialChecks,
			Supplier<List<PortalFunctionStartupChecker>> checksFactory, Observable<?> implementationUpdates, Scheduler scheduler) {
		this.portalInfo = portalInfo;
		this.initialChecks = initialChecks;
		this.checksFactory = checksFactory;
		this.implementationUpdates = implementationUpdates;
		this.scheduler = scheduler;
	}

	/**
	 * Perform the initial run on the calling thread and repeat the checks after each portal implementation update.
	 *
	 * @return disposable to stop repeating the checks
	 */
	public Disposable start() {
		Completable initialRun = run(initialChecks);

		return Observable.concat(
			Observable.just(initialRun),
			implementationUpdates.map(update -> Completable.defer(() -> {
				log.info("Repeating start-up checks after portal implementation update");
				return run(checksFactory.get());
			}).subscribeOn(scheduler))
		).concatMapCompletable(run -> run).subscribe();
	}

	/**
	 * Run the given checks and update the portal state according to the result.
	 *
	 * @param checks checks to perform
	 * @return completable, which never fails
	 */
	private Completable run(List<PortalFunctionStartupChecker> checks) {
		return Observable.fromIterable(checks)
			.flatMapCompletable(PortalFunctionStartupChecker::checkAsync)
			.doOnComplete(() -> {
				log.info("Delayed start-up checks passed");

				if (portalInfo.getState() == PortalState.STARTUP_CHECK_ERROR) {
					log.info("Portal state changed from {} to {}", PortalState.STARTUP_CHECK_ERROR, PortalState.READY);
					portalInfo.setState(PortalState.READY);
				}
			})
			.doOnError(cause -> {
				log.warn("Start-up check failed, portal is configured to start anyways: {}", cause.getMessage());
				portalInfo.setState(PortalState.STARTUP_CHECK_ERROR);
			})
			.onErrorComplete();
	}
}
