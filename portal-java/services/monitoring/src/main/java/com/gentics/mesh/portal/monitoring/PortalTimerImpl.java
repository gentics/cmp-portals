package com.gentics.mesh.portal.monitoring;

import java.util.Objects;
import java.util.function.Supplier;

import com.gentics.mesh.portal.api.monitoring.PortalTimer;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link PortalTimer} that will do nothing if no delegate {@link Timer} was passed in the constructor.
 */
public class PortalTimerImpl implements PortalTimer {

	private static final Logger log = LoggerFactory.getLogger(PortalTimerImpl.class);

	private final LongTaskTimer delegateTimer;
	private final boolean logDisabled;
	private Sample sample;

	public PortalTimerImpl(LongTaskTimer delegateTimer) {
		Objects.requireNonNull(delegateTimer, "A delegate timer must be provided");

		this.delegateTimer = delegateTimer;
		this.logDisabled = !log.isTraceEnabled();
	}

	@Override
	public PortalTimer start() {
		sample = delegateTimer.start();
		return this;
	}

	@Override
	public long stop(Supplier<String> message) {
		return stop(logDisabled || message == null ? null : message.get());
	}

	@Override
	public long stop(String message) {
		if (sample == null) {
			log.warn("PortalTimer was not started");

			return 0;
		}

		long duration = sample.stop();

		if (message != null) {
			log.trace(message, duration);
		}

		return duration;
	}
}
