package com.gentics.mesh.portal.otel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;

/**
 * Test cases for the sampler created by the {@link PortalRequestSamplerProvider}
 */
public class PortalRequestSamplerTest {
	private final static String TRACE_ID = "71ed2eac5bb03cd01bcc828ca0673c1b";

	private final static String SPAN_ID = "a1b2c3d4e5f60718";

	private final static String BROWSER = "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0";

	private final Sampler sampler = new PortalRequestSamplerProvider().createSampler(null);

	@Test
	public void testName() {
		assertThat(new PortalRequestSamplerProvider().getName()).as("Sampler name").isEqualTo("gentics_portal_java");
	}

	@Test
	public void testContentRequest() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/en/welcome.html", BROWSER)).as("Decision").isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
	}

	@Test
	public void testRequestWithoutUserAgent() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/en/welcome.html", null)).as("Decision").isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
	}

	@Test
	public void testImageStoreRequest() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/GenticsImageStore/100/auto/prop/images/image.jpg", BROWSER))
				.as("Decision").isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
	}

	@Test
	public void testApiRequest() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/api/forms/form/abc/load", BROWSER)).as("Decision").isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
	}

	@Test
	public void testLivenessProbe() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/health/live", "kube-probe/1.33")).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testReadinessProbe() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/health/ready", "kube-probe/1.33")).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testMetrics() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/metrics", "Prometheus/3.16.0")).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testStaticFile() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/static/css/style.css", BROWSER)).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testFormGenFile() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/formgen/index.js", BROWSER)).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testBlackboxRequest() {
		assertThat(sample(Context.root(), SpanKind.SERVER, "/", "Blackbox Exporter/0.27.0")).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testRequestWithoutPath() {
		assertThat(sampler.shouldSample(Context.root(), TRACE_ID, "GET", SpanKind.SERVER, Attributes.empty(), Collections.emptyList())
				.getDecision()).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testRootClientSpan() {
		assertThat(sample(Context.root(), SpanKind.CLIENT, null, null)).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testRootInternalSpan() {
		assertThat(sample(Context.root(), SpanKind.INTERNAL, null, null)).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testChildOfSampledParent() {
		assertThat(sample(parent(false, TraceFlags.getSampled()), SpanKind.CLIENT, null, null)).as("Decision")
				.isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
	}

	@Test
	public void testChildOfNotSampledParent() {
		assertThat(sample(parent(false, TraceFlags.getDefault()), SpanKind.CLIENT, null, null)).as("Decision").isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testRequestWithSampledRemoteParent() {
		assertThat(sample(parent(true, TraceFlags.getSampled()), SpanKind.SERVER, "/en/welcome.html", BROWSER)).as("Decision")
				.isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
	}

	@Test
	public void testStaticFileWithSampledRemoteParent() {
		assertThat(sample(parent(true, TraceFlags.getSampled()), SpanKind.SERVER, "/static/css/style.css", BROWSER)).as("Decision")
				.isEqualTo(SamplingDecision.DROP);
	}

	@Test
	public void testRequestWithNotSampledRemoteParent() {
		assertThat(sample(parent(true, TraceFlags.getDefault()), SpanKind.SERVER, "/en/welcome.html", BROWSER)).as("Decision")
				.isEqualTo(SamplingDecision.DROP);
	}

	/**
	 * Test that the excluded path prefixes can be replaced with the configuration property
	 */
	@Test
	public void testConfiguredPrefixes() {
		Sampler configured = new PortalRequestSamplerProvider().createSampler(DefaultConfigProperties.createFromMap(
				Map.of(PortalRequestSamplerProvider.EXCLUDED_PREFIXES_PROPERTY, "/static/,/static/cpa/")));

		assertThat(sample(configured, Context.root(), SpanKind.SERVER, "/static/cpa/index.js", BROWSER)).as("Decision for configured prefix")
				.isEqualTo(SamplingDecision.DROP);
		assertThat(sample(configured, Context.root(), SpanKind.SERVER, "/formgen/index.js", BROWSER)).as("Decision for default prefix, which was replaced")
				.isEqualTo(SamplingDecision.RECORD_AND_SAMPLE);
		assertThat(sample(configured, Context.root(), SpanKind.SERVER, "/health/ready", null)).as("Decision for probe")
				.isEqualTo(SamplingDecision.DROP);
	}

	/**
	 * Test that the default prefixes are used, when no prefixes are configured
	 */
	@Test
	public void testDefaultPrefixes() {
		Sampler configured = new PortalRequestSamplerProvider().createSampler(DefaultConfigProperties.createFromMap(Map.of()));

		assertThat(sample(configured, Context.root(), SpanKind.SERVER, "/static/css/style.css", BROWSER)).as("Decision for static file")
				.isEqualTo(SamplingDecision.DROP);
		assertThat(sample(configured, Context.root(), SpanKind.SERVER, "/formgen/index.js", BROWSER)).as("Decision for FormGen file")
				.isEqualTo(SamplingDecision.DROP);
	}

	/**
	 * Let the sampler decide
	 * @param parentContext parent context
	 * @param kind span kind
	 * @param path url.path attribute, may be null
	 * @param userAgent user_agent.original attribute, may be null
	 * @return sampling decision
	 */
	protected SamplingDecision sample(Context parentContext, SpanKind kind, String path, String userAgent) {
		return sample(sampler, parentContext, kind, path, userAgent);
	}

	/**
	 * Let the given sampler decide
	 * @param sampler sampler
	 * @param parentContext parent context
	 * @param kind span kind
	 * @param path url.path attribute, may be null
	 * @param userAgent user_agent.original attribute, may be null
	 * @return sampling decision
	 */
	protected SamplingDecision sample(Sampler sampler, Context parentContext, SpanKind kind, String path, String userAgent) {
		AttributesBuilder attributes = Attributes.builder();
		if (path != null) {
			attributes.put(PortalRequestSampler.URL_PATH, path);
		}
		if (userAgent != null) {
			attributes.put(PortalRequestSampler.USER_AGENT, userAgent);
		}
		return sampler.shouldSample(parentContext, TRACE_ID, "span", kind, attributes.build(), Collections.emptyList()).getDecision();
	}

	/**
	 * Create a context with a parent span
	 * @param remote true for a remote parent
	 * @param flags trace flags of the parent
	 * @return context
	 */
	protected Context parent(boolean remote, TraceFlags flags) {
		SpanContext spanContext = remote ? SpanContext.createFromRemoteParent(TRACE_ID, SPAN_ID, flags, TraceState.getDefault())
				: SpanContext.create(TRACE_ID, SPAN_ID, flags, TraceState.getDefault());
		return Context.root().with(Span.wrap(spanContext));
	}
}
