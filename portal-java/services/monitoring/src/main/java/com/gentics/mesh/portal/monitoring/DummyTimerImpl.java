package com.gentics.mesh.portal.monitoring;

import com.gentics.mesh.portal.api.monitoring.PortalTimer;

import java.util.function.Supplier;

/**
 * A dummy implementation used when monitoring is disabled.
 */
public class DummyTimerImpl implements PortalTimer {

	@Override
	public PortalTimer start() {
		return this;
	}

	@Override
	public long stop(Supplier<String> message) {
		return 0;
	}

	@Override
	public long stop(String message) {
		return 0;
	}

	@Override
	public long stop() {
		return 0;
	}
}
