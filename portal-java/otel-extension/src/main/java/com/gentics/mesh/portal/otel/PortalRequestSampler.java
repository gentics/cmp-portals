package com.gentics.mesh.portal.otel;

import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * Sampler, which samples only the server spans of incoming requests to the portal.
 * Requests to the monitoring endpoints, to static files and from the Blackbox exporter are not sampled,
 * neither are all other spans without a parent (e.g. requests to Mesh done by background tasks).
 * Used as root sampler of a parent based sampler (see {@link PortalRequestSamplerProvider}), so that
 * the work done for a sampled request is sampled as well.
 */
public class PortalRequestSampler implements Sampler {
	/**
	 * Attribute containing the path of the requested URL
	 */
	public final static AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");

	/**
	 * Attribute containing the User-Agent header of the request
	 */
	public final static AttributeKey<String> USER_AGENT = AttributeKey.stringKey("user_agent.original");

	/**
	 * Paths of requests, which are not sampled (the health and metrics endpoints of the monitoring server)
	 */
	public final static List<String> EXCLUDED_PATHS = List.of("/health/live", "/health/ready", "/metrics");

	/**
	 * Default path prefixes of requests, which are not sampled (static files and the files of the FormGen app)
	 */
	public final static List<String> DEFAULT_EXCLUDED_PREFIXES = List.of("/static/", "/formgen/");

	/**
	 * Prefix of the User-Agent of the Prometheus Blackbox exporter, whose requests are not sampled
	 */
	public final static String BLACKBOX_USER_AGENT_PREFIX = "Blackbox Exporter/";

	/**
	 * Path prefixes of requests, which are not sampled
	 */
	private final List<String> excludedPrefixes;

	/**
	 * Create an instance with the {@link #DEFAULT_EXCLUDED_PREFIXES}
	 */
	public PortalRequestSampler() {
		this(DEFAULT_EXCLUDED_PREFIXES);
	}

	/**
	 * Create an instance with the given excluded path prefixes
	 * @param excludedPrefixes path prefixes of requests, which are not sampled
	 */
	public PortalRequestSampler(List<String> excludedPrefixes) {
		this.excludedPrefixes = List.copyOf(excludedPrefixes);
	}

	@Override
	public SamplingResult shouldSample(Context parentContext, String traceId, String name, SpanKind spanKind,
			Attributes attributes, List<LinkData> parentLinks) {
		if (spanKind == SpanKind.SERVER && isSampledPath(attributes.get(URL_PATH)) && !isBlackbox(attributes.get(USER_AGENT))) {
			return SamplingResult.recordAndSample();
		} else {
			return SamplingResult.drop();
		}
	}

	@Override
	public String getDescription() {
		return "PortalRequestSampler{excludedPrefixes=" + excludedPrefixes + "}";
	}

	/**
	 * Get the path prefixes of requests, which are not sampled
	 * @return list of prefixes
	 */
	public List<String> getExcludedPrefixes() {
		return excludedPrefixes;
	}

	/**
	 * Check whether requests to the given path are sampled
	 * @param path path, may be null
	 * @return true for sampled paths
	 */
	protected boolean isSampledPath(String path) {
		if (path == null || EXCLUDED_PATHS.contains(path)) {
			return false;
		}
		return excludedPrefixes.stream().noneMatch(path::startsWith);
	}

	/**
	 * Check whether the request was sent by the Blackbox exporter
	 * @param userAgent User-Agent of the request, may be null
	 * @return true for requests of the Blackbox exporter
	 */
	protected boolean isBlackbox(String userAgent) {
		return userAgent != null && userAgent.startsWith(BLACKBOX_USER_AGENT_PREFIX);
	}
}
