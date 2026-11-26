package com.gentics.mesh.portal.otel;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * Provider for the sampler "gentics_portal_java" (selected with OTEL_TRACES_SAMPLER=gentics_portal_java).
 * Spans without parent and spans with a sampled remote parent are sampled by the {@link PortalRequestSampler},
 * all other spans follow the sampling decision of their parent.
 * The excluded path prefixes can be replaced with the property {@link #EXCLUDED_PREFIXES_PROPERTY}
 * (environment variable OTEL_GENTICS_PORTAL_EXCLUDED_PATH_PREFIXES), e.g. when the FormGen app is served from another path.
 */
public class PortalRequestSamplerProvider implements ConfigurableSamplerProvider {
	/**
	 * Name of the sampler
	 */
	public final static String NAME = "gentics_portal_java";

	/**
	 * Name of the property containing the comma separated list of excluded path prefixes
	 */
	public final static String EXCLUDED_PREFIXES_PROPERTY = "otel.gentics.portal.excluded-path-prefixes";

	@Override
	public Sampler createSampler(ConfigProperties config) {
		Sampler portal = config != null
				? new PortalRequestSampler(config.getList(EXCLUDED_PREFIXES_PROPERTY, PortalRequestSampler.DEFAULT_EXCLUDED_PREFIXES))
				: new PortalRequestSampler();
		return Sampler.parentBasedBuilder(portal).setRemoteParentSampled(portal).build();
	}

	@Override
	public String getName() {
		return NAME;
	}
}
