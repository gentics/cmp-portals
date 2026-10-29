package com.gentics.mesh.portal.api.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Interface for services providing metrics about the portal.
 */
public interface MetricsService {

	public static final String REGISTRY_NAME = "mesh-portal";

	/**
	 * Whether monitoring is enabled.
	 *
	 * @return <code>true</code> when monitoring is enabled, and <code>false</code> otherwise
	 */
	boolean isEnabled();

	/**
	 * Get the underlying metrics registry of this service.
	 *
	 * @return The underlying metrics registry of this service
	 */
	MeterRegistry getMetricRegistry();

	/**
	 * Get the <code>Meter</code> for the specified <code>PortalMetric</code>.
	 *
	 * @param metric
	 *            The <code>PortalMetric</code> to get a <code>Meter</code> for
	 * @return The <code>Meter</code> for the specified <code>PortalMetric</code>
	 */
	default Meter meter(PortalMetric metric) {
		return getMetricRegistry().summary(metric.key());
	}

	/**
	 * Get the <code>Counter</code> for the specified <code>PortalMetric</code>.
	 *
	 * @param metric
	 *            The <code>PortalMetric</code> to get a <code>Counter</code> for
	 * @return The <code>Counter</code> for the specified <code>PortalMetric</code>
	 */
	default Counter counter(PortalMetric metric) {
		return getMetricRegistry().counter(metric.key());
	}

	/**
	 * Increase the <code>Counter</code> for the given <code>PortalMetric</code> if the service is {@link #isEnabled() enabled}.
	 *
	 * @param metric
	 *            The <code>PortalMetric</code> specifying the <code>Counter</code> to increase
	 */
	default void incCounter(PortalMetric metric) {
		if (isEnabled()) {
			getMetricRegistry().counter(metric.key()).increment();
		}
	}

	default Timer timer(PortalMetric metric) {
		return getMetricRegistry().timer(metric.key());
	}

	/**
	 * Wrap a {@link #timer(PortalMetric) timer} in a <code>PortalTimer</code>.
	 *
	 * <p>
	 * When monitoring is {@link #isEnabled() disabled}, the returned timers methods will do nothing.
	 * </p>
	 *
	 * @param metric
	 *            The <code>PortalMetric</code> specifying the timer to retrieve
	 * @return A {@link #timer(PortalMetric) timer} wrapped in a <code>PortalTimer</code>
	 */
	PortalTimer wrapTimer(PortalMetric metric);

	/**
	 * Start a <code>PortalTimer</code> for the specified <code>PortalMetric</code> if the service is {@link #isEnabled() enabled}.
	 *
	 * <p>
	 * When monitoring is not enabled, the returned timers methods will do nothing.
	 * </p>
	 *
	 * @param metric
	 *            The <code>PortalMetric</code> specifying the timer to start
	 * @return The <code>PortalTimer</code> of the started <code>PortalTimer</code>
	 */
	default PortalTimer startTimer(PortalMetric metric) {
		return wrapTimer(metric).start();
	}
}
