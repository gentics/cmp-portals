package com.gentics.mesh.portal.monitoring;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.config.option.MonitoringOptions;
import com.gentics.mesh.portal.util.MiscUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Eventlistener implementation for the okHttp Client for monitoring and logging
 */
@NonNullApi
@NonNullFields
public class OkHttpCallStateEventListener extends EventListener {
	private static final Logger log = LoggerFactory.getLogger(OkHttpClient.class);

	/**
	 * Map containing the currently tracked calls
	 */
	private final Map<Call, CallState> callStates = new ConcurrentHashMap<>();

	/**
	 * Monitoring Registry
	 */
	private final MeterRegistry registry;

	/**
	 * Metrics name
	 */
	private final String requestsMetricName;

	/**
	 * Portal info reference for checking error state.
	 */
	private final PortalInfo portalInfo;

	/**
	 * Maximum number of timeout or other socket errors before changing the portal State
	 * to {@link com.gentics.mesh.portal.api.server.PortalState#NETWORK_ERROR}.
	 */
	private final int maxTimeouts;

	/**
	 * Number of timeouts or other socket errors since the last successfully
	 * ended call.
	 */
	private int timeoutCount;

	/**
	 * Create an instance
	 * @param registry registry
	 * @param name metrics name
	 */
	OkHttpCallStateEventListener(MeterRegistry registry, String name, PortalInfo portalInfo, int maxTimeouts) {
		this.registry = registry;
		this.requestsMetricName = name;
		this.portalInfo = portalInfo;
		this.maxTimeouts = maxTimeouts;
		this.timeoutCount = 0;

		// add a gauge for every call state value
		for (CallStateValue callState : CallStateValue.values()) {
			registry.gauge(name, Tags.of("state", callState.name()), callStates,
					map -> map.values().stream().filter(s -> s.value == callState).count());
		}
	}

	/**
	 * Return a builder
	 * @param registry registry
	 * @param name metrics name
	 * @return builder
	 */
	public static Builder builder(MeterRegistry registry, String name) {
		return new Builder(registry, name);
	}

	@Override
	public void callStart(Call call) {
		callStates.put(call, new CallState(registry.config().clock().monotonicTime(), call.request()));
		handle(call, CallStateValue.a_call_start);
	}

	@Override
	public void dnsStart(Call call, String domainName) {
		handle(call, CallStateValue.b_dns_start);
	}

	@Override
	public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
		handle(call, CallStateValue.c_dns_end);
	}

	@Override
	public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
		handle(call, CallStateValue.d_conn_start);
	}

	@Override
	public void secureConnectStart(Call call) {
		handle(call, CallStateValue.e_tls_start);
	}

	@Override
	public void secureConnectEnd(Call call, Handshake handshake) {
		handle(call, CallStateValue.f_tls_end);
	}

	@Override
	public void connectEnd(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
		handle(call, CallStateValue.g_conn_end);
	}

	@Override
	public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol,
			IOException ioe) {
		handle(call, CallStateValue.h_conn_failed);
	}

	@Override
	public void connectionAcquired(Call call, Connection connection) {
		handle(call, CallStateValue.i_conn_acquired);
	}

	@Override
	public void requestHeadersStart(Call call) {
		handle(call, CallStateValue.j_requ_headers_start);
	}

	@Override
	public void requestHeadersEnd(Call call, Request request) {
		handle(call, CallStateValue.k_requ_headers_end);
	}

	@Override
	public void requestBodyStart(Call call) {
		handle(call, CallStateValue.l_requ_body_start);
	}

	@Override
	public void requestBodyEnd(Call call, long byteCount) {
		handle(call, CallStateValue.m_requ_body_end);
		Counter.builder(this.requestsMetricName).tags("bytes", "sent").register(registry).increment(byteCount);
	}

	@Override
	public void responseHeadersStart(Call call) {
		handle(call, CallStateValue.n_resp_headers_start);
	}

	@Override
	public void responseHeadersEnd(Call call, Response response) {
		CallState state = handle(call, CallStateValue.o_resp_headers_end);
		if (state != null) {
			state.response = response;
		}
	}

	@Override
	public void responseBodyStart(Call call) {
		handle(call, CallStateValue.p_resp_body_start);
	}

	@Override
	public void responseBodyEnd(Call call, long byteCount) {
		handle(call, CallStateValue.q_resp_body_end);
		Counter.builder(this.requestsMetricName).tags("bytes", "received").register(registry).increment(byteCount);
	}

	@Override
	public void connectionReleased(Call call, Connection connection) {
		handle(call, CallStateValue.r_conn_released);
	}

	@Override
	public void callFailed(Call call, IOException ioe) {
		CallState state = callStates.remove(call);
		if (state != null) {
			state.exception = ioe;
			time(state);
		}

		Request request = call.request();

		log.error("HTTP request failed: {} {}: {}", request.method(), request.url(), ioe.getMessage(), ioe);

		if (portalInfo != null && MiscUtils.isSocketError(ioe)) {
			if (++timeoutCount >= maxTimeouts) {
				portalInfo.checkState(ioe);
			}
		}
	}

	@Override
	public void callEnd(Call call) {
		CallState state = callStates.remove(call);
		if (state != null) {
			time(state);
		}

		if (portalInfo != null) {
			timeoutCount = 0;
			portalInfo.resetErrors();
		}
	}

	/**
	 * Handle a call state change
	 * @param call call
	 * @param stateValue new state value
	 * @return the tracked callstate or null, if the call is not tracked
	 */
	private CallState handle(Call call, CallStateValue stateValue) {
		if (log.isTraceEnabled()) {
			log.trace("Call state {}: {}", call.request().toString(), stateValue);
		}
		CallState state = callStates.get(call);
		if (state != null) {
			state.value = stateValue;
		}
		return state;
	}

	/**
	 * Add request time to the metrics
	 * @param state call state
	 */
	private void time(CallState state) {
		Request request = state.request;
		boolean requestAvailable = request != null;

		String type = RequestType.from(request);

		Iterable<Tag> tags = Tags.of("method", requestAvailable ? request.method() : "UNKNOWN",
						"type", type,
						"status", getStatusMessage(state.response, state.exception),
						"host", requestAvailable ? request.url().host() : "UNKNOWN");

		Timer.builder(this.requestsMetricName).tags(tags).description("Timer of OkHttp operation").register(registry)
				.record(registry.config().clock().monotonicTime() - state.startTime, TimeUnit.NANOSECONDS);
	}

	/**
	 * Get status message for the given response and exception
	 * @param response response
	 * @param exception exception
	 * @return status message
	 */
	private String getStatusMessage(@Nullable Response response, @Nullable IOException exception) {
		if (exception != null) {
			return "IO_ERROR";
		}

		if (response == null) {
			return "CLIENT_ERROR";
		}

		return Integer.toString(response.code());
	}


	/**
	 * Builder for the event listener
	 */
	public static class Builder {
		private final MeterRegistry registry;
		private final String name;

		private PortalInfo portalInfo;
		private int maxTimeouts = MonitoringOptions.DEFAULT_MAX_TIMEOUTS;

		Builder(MeterRegistry registry, String name) {
			this.registry = registry;
			this.name = name;
		}

		public Builder portalInfo(PortalInfo portalInfo) {
			this.portalInfo = portalInfo;

			return this;
		}

		public Builder maxTimeouts(int maxTimeouts) {
			this.maxTimeouts = maxTimeouts;

			return this;
		}

		/**
		 * Build the event listener
		 * @return event listener
		 */
		public OkHttpCallStateEventListener build() {
			return new OkHttpCallStateEventListener(registry, name, portalInfo, maxTimeouts);
		}
	}

	/**
	 * Tracked call state
	 */
	private static class CallState {
		final long startTime;
		@Nullable
		final Request request;
		@Nullable
		Response response;
		@Nullable
		IOException exception;

		CallStateValue value = CallStateValue.a_call_start;

		CallState(long startTime, @Nullable Request request) {
			this.startTime = startTime;
			this.request = request;
		}
	}

	/**
	 * Enum of possible call state values
	 */
	private static enum CallStateValue {
		a_call_start, b_dns_start, c_dns_end, d_conn_start, e_tls_start, f_tls_end, g_conn_end, h_conn_failed,
		i_conn_acquired, j_requ_headers_start, k_requ_headers_end, l_requ_body_start, m_requ_body_end,
		n_resp_headers_start, o_resp_headers_end, p_resp_body_start, q_resp_body_end, r_conn_released
	}

	/**
	 * Enum of possible request types. The encoded URL Path is matched with the given patterns (in the defined order)
	 */
	private static enum RequestType {
		graphql(Pattern.compile("/api/v[0-9]/[^/]+/graphql")),
		webroot(Pattern.compile("/api/v[0-9]/[^/]+/webroot/(.*)")),
		other(Pattern.compile(".*"));

		private Pattern pattern;

		RequestType(Pattern pattern) {
			this.pattern = pattern;
		}

		/**
		 * Get the type as string for the given request. If no request is given, or no pattern matches, return "none"
		 * @param request request
		 * @return type as string
		 */
		public static String from(Request request) {
			if (request != null) {
				for (RequestType type : values()) {
					if (type.pattern.matcher(request.url().encodedPath()).matches()) {
						return type.name();
					}
				}
			}

			return "none";
		}
	}
}
