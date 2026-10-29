package com.gentics.mesh.portal.server.dagger;

import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Singleton;

import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.MonitoringOptions;
import com.gentics.mesh.portal.monitoring.MicrometerMetricsService;

import dagger.Module;
import dagger.Provides;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.Match;
import io.vertx.micrometer.MetricsDomain;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;

@Module
public class MicrometerModule {

	@Provides
	@Singleton
	public static MeterRegistry meterRegistry(PortalConfig config) {
		PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		return registry;
	}

	@Provides
	@Singleton
	public static MetricsService bindMetricsService(MicrometerMetricsService e) {
		return e;
	}

	@Provides
	@Singleton
	public static MetricsOptions micrometerMetricsOptions(PortalConfig config) {
		MonitoringOptions monitoringOptions = config.getMonitoringOptions();
		MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
			.setRegistryName(MetricsService.REGISTRY_NAME)
			.setJvmMetricsEnabled(monitoringOptions.isJmxEnabled())
			.setLabels(EnumSet.of(Label.HTTP_CODE, Label.HTTP_METHOD, Label.LOCAL))
			.setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
			.setEnabled(true);
		labelMatches(config).forEach(metricsOptions::addLabelMatch);

		return metricsOptions;
	}

	private static Stream<Match> labelMatches(PortalConfig config) {
		return Stream.of(
			new Match()
				.setDomain(MetricsDomain.HTTP_SERVER)
				.setLabel("local")
				.setAlias("portal")
				.setValue(config.getServerHost() + ":" + config.getServerPort()),
			new Match()
				.setDomain(MetricsDomain.HTTP_SERVER)
				.setLabel("local")
				.setAlias("monitoring")
				.setValue(config.getMonitoringOptions().getHost() + ":" + config.getMonitoringOptions().getPort()));
	}

	interface PathMatcher {
		Optional<String> getAlias(String path);
	}

}