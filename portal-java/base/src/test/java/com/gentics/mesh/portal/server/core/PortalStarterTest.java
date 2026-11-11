package com.gentics.mesh.portal.server.core;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.gentics.mesh.portal.api.PortalEvents;

import io.reactivex.observers.TestObserver;
import io.vertx.core.Vertx;

/**
 * Test cases for {@link PortalStarter}.
 */
public class PortalStarterTest {

	private Vertx vertx;

	@Before
	public void setup() {
		vertx = Vertx.vertx();
	}

	@After
	public void tearDown() throws Exception {
		vertx.close().await(10, TimeUnit.SECONDS);
	}

	/**
	 * Test that the observable of implementation updates emits the published events, and unregisters its consumer
	 * when disposed.
	 */
	@Test
	public void testImplementationUpdates() throws Exception {
		TestObserver<Object> observer = PortalStarter.implementationUpdates(vertx).test();

		vertx.eventBus().publish(PortalEvents.PORTAL_IMPLEMENTATION_UPDATED.address(), "impl-1");
		vertx.eventBus().publish(PortalEvents.PORTAL_IMPLEMENTATION_UPDATED.address(), "impl-2");

		observer.awaitCount(2).assertValues("impl-1", "impl-2");

		observer.dispose();

		// A new subscriber receives the next event, the disposed one must not.
		TestObserver<Object> other = PortalStarter.implementationUpdates(vertx).test();

		vertx.eventBus().publish(PortalEvents.PORTAL_IMPLEMENTATION_UPDATED.address(), "impl-3");

		other.awaitCount(1).assertValues("impl-3");
		observer.assertValueCount(2);
		other.dispose();
	}
}
