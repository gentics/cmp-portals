package com.gentics.mesh.portal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.ext.web.RoutingContext;

public interface TestRoutingContext extends RoutingContext {

	CountDownLatch latch();

	default boolean await() throws InterruptedException {
		return await(10);
	}

	default boolean await(int timeoutSeconds) throws InterruptedException {
		return latch().await(timeoutSeconds, TimeUnit.SECONDS);
	}

}
