package com.gentics.mesh.portal.monitoring;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.monitoring.PortalMetric;
import com.gentics.mesh.portal.api.monitoring.PortalTimer;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MonitoringOptions;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementation of {@link MetricsService} using Vert.x Micrometer metrics.
 *
 * <p>
 * The provided metrics will be encoded such that they can be used by Prometheus.
 * </p>
 */
@Singleton
public class MicrometerMetricsService implements MetricsService {

	private final MeterRegistry micrometerRegistry;
	private final MonitoringOptions options;

	/**
	 * Default constructor.
	 *
	 * <p>
	 * Creates a Micrometer export and registers it with the Prometheus registry.
	 * </p>
	 *
	 * @param config
	 *            The monitoring configuration
	 * @param micrometerRegistry
	 */
	@Inject
	public MicrometerMetricsService(PortalConfig config, MeterRegistry micrometerRegistry) {
		MonitoringOptions options = config.getMonitoringOptions();

		if (options == null) {
			options = new MonitoringOptions();
		}

		this.options = options;
		this.micrometerRegistry = micrometerRegistry;
	}

	@Override
	public boolean isEnabled() {
		return options.isEnabled();
	}

	@Override
	public MeterRegistry getMetricRegistry() {
		return micrometerRegistry;
	}

	@Override
	public PortalTimer wrapTimer(PortalMetric metric) {
		LongTaskTimer longTaskTimer = LongTaskTimer
			.builder(metric.key())
			.register(getMetricRegistry());
		return new PortalTimerImpl(longTaskTimer);
	}

}
