package com.gentics.mesh.portal.server.core;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.api.server.PortalState;

import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * Test cases for {@link UnreadyStartupChecks}.
 */
public class UnreadyStartupChecksTest {

	private TestPortalInfo portalInfo;

	private PublishSubject<String> updates;

	/** Result of the checks created by the factory. */
	private AtomicBoolean checksPass;

	/** Number of checks created by the factory. */
	private AtomicInteger createdChecks;

	@Before
	public void setup() {
		portalInfo = new TestPortalInfo();
		updates = PublishSubject.create();
		checksPass = new AtomicBoolean(true);
		createdChecks = new AtomicInteger();
	}

	private UnreadyStartupChecks create(boolean initialPass) {
		return new UnreadyStartupChecks(
			portalInfo,
			List.of(() -> initialPass),
			() -> {
				createdChecks.incrementAndGet();
				boolean pass = checksPass.get();
				return List.of(() -> pass);
			},
			updates,
			Schedulers.trampoline());
	}

	/**
	 * Test that a failing initial check sets the error state, and a passing check after an update makes the portal
	 * ready again.
	 */
	@Test
	public void testRecoveryAfterUpdate() {
		create(false).start();

		assertEquals(PortalState.STARTUP_CHECK_ERROR, portalInfo.getState());

		updates.onNext("impl");

		assertEquals(PortalState.READY, portalInfo.getState());
	}

	/**
	 * Test that a passing initial check does not change the state, and a failing check after an update sets the
	 * error state.
	 */
	@Test
	public void testFailureAfterUpdate() {
		create(true).start();

		assertEquals("Passing check must not change the state", PortalState.STARTING, portalInfo.getState());

		portalInfo.setState(PortalState.READY);
		checksPass.set(false);
		updates.onNext("impl");

		assertEquals(PortalState.STARTUP_CHECK_ERROR, portalInfo.getState());
	}

	/**
	 * Test that new checks are created for each update, and that a failing check does not stop further repetitions.
	 */
	@Test
	public void testRepeatedForEachUpdate() {
		create(true).start();

		assertEquals("Initial run must use the initial checks", 0, createdChecks.get());

		checksPass.set(false);
		updates.onNext("impl");
		assertEquals(1, createdChecks.get());
		assertEquals(PortalState.STARTUP_CHECK_ERROR, portalInfo.getState());

		updates.onNext("impl");
		assertEquals(2, createdChecks.get());
		assertEquals(PortalState.STARTUP_CHECK_ERROR, portalInfo.getState());

		checksPass.set(true);
		updates.onNext("impl");
		assertEquals(3, createdChecks.get());
		assertEquals(PortalState.READY, portalInfo.getState());
	}

	/**
	 * Test that a passing repetition does not change a state other than {@link PortalState#STARTUP_CHECK_ERROR}.
	 */
	@Test
	public void testPassingRepetitionKeepsOtherState() {
		create(true).start();

		portalInfo.setState(PortalState.NETWORK_ERROR);
		updates.onNext("impl");

		assertEquals(PortalState.NETWORK_ERROR, portalInfo.getState());
	}

	/**
	 * {@link PortalInfo} which just stores the state.
	 */
	private static class TestPortalInfo implements PortalInfo {
		private PortalState state = PortalState.STARTING;

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
		}

		@Override
		public void resetErrors() {
		}

		@Override
		public void shutdown() {
		}
	}
}
