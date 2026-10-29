package com.gentics.mesh.portal.handler;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.util.IpAddressMatcher;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

/**
 * Handler that checks if the request is allowed.
 *
 * <p>
 *     This security check is intended for backend API endpoints, like
 *     the preview, or clearing the caches.
 * </p>
 *
 * <p>
 *     A request is allowed if it comes from a remote host that is configured
 *     as an {@link SecurityOptions#getAllowedIps() allowed IP}, <em>and</em>
 *     if the provided API key (as an {@link #HEADER_API_KEY HTTP header} or
 *     in the request body) matches the configured
 *     {@link SecurityOptions#getApiKey() API key}.
 * </p>
 *
 * <p>
 *     <strong>Note</strong> that when neither the allowed IPs nor the API key
 *     are configured, <em>all</em> requests passed to this handler will end
 *     with a <em>403 Forbidden</em> response.
 * </p>
 */
@Singleton
public class SecurityHandler implements Handler<RoutingContext> {

	public static final String HEADER_API_KEY = "X-GPJ-API-Key";

	private static final Logger log = LoggerFactory.getLogger(SecurityHandler.class);

	private final String apiKey;
	private final Collection<String> fixedIps = new HashSet<>();
	private final Collection<IpAddressMatcher> ipRanges = new ArrayList<>();
	private final boolean alwaysForbidden;

	@Inject
	public SecurityHandler(PortalConfig config) {
		SecurityOptions options = config.getSecurityOptions();

		this.apiKey = StringUtils.isBlank(options.getApiKey())
			? null
			: options.getApiKey();

		for (String entry : options.getAllowedIps()) {
			if (!entry.contains("/")) {
				// Checking with the IpAddressMatcher is more expensive, so
				// we check addresses which are not in CIDR notation by
				// simple string comparison first.
				this.fixedIps.add(entry);
			}

			this.ipRanges.add(new IpAddressMatcher(entry));
		}

		this.alwaysForbidden = apiKey == null && options.getAllowedIps().isEmpty();
	}

	/**
	 * Check if the remote address is in the list of allowed IPs, or if
	 * the provided API key is correct.
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		if (alwaysForbidden) {
			log.warn("Neither allowed IPs nor portal API key was configured, all requests are forbidden");
		}

		if (alwaysForbidden || ipForbidden(rc) || invalidApiKey(rc)) {
			if (log.isWarnEnabled()) {
				log.warn("Forbidden request to {}", rc.normalizedPath());
			}

			rc.response()
				.setStatusCode(HttpResponseStatus.FORBIDDEN.code())
				.end();
		} else {
			log.trace("Request to {} allowed", rc.normalizedPath());

			rc.next();
		}
	}

	/**
	 * Check if there are IP restrictions, and if so if the remote host is
	 * allowed or not.
	 *
	 * @param rc The current routing context
	 * @return {@code true} when allowed IPs are configured, but the remote
	 * 		host does not match any, and {@code false} when either nor allowed
	 * 		IPs are configured, or the remote host matches one of them
	 */
	private boolean ipForbidden(RoutingContext rc) {
		String remoteHost = rc.request().remoteAddress().host();

		if (ipRanges.isEmpty()) {
			log.trace("No IP address restrictions");

			return false;
		}

		if (fixedIps.contains(remoteHost) || isInAllowedRange(remoteHost)) {
			log.trace("Request from {} is allowed", remoteHost);

			return false;
		}

		log.trace("Request from {} is forbidden", remoteHost);

		return true;
	}

	/**
	 * Check if the given address matches any of the configured IPs.
	 *
	 * @param remoteHost The remote address to check
	 * @return {@code true} when at least one
	 * 		{@link SecurityOptions#getAllowedIps()} matches the given
	 * 		{@code remoteHost}, and {@code false} otherwise
	 */
	private boolean isInAllowedRange(String remoteHost) {
		for (IpAddressMatcher matcher : ipRanges) {
			try {
				if (matcher.matches(remoteHost)) {
					return true;
				}
			} catch (Exception e) {
				// Just ignore unknown remote hosts (which should not happen at
				// this point anyways).
			}
		}

		return false;
	}

	/**
	 * Check if an API key is required for the request, and if so if it is
	 * valid.
	 *
	 * <p>
	 *     The key is taken from the HTTP header {@link #HEADER_API_KEY}, or
	 *     from the request body (which must be a JSON object with the field
	 *     {@code key}) which ever gives a non-null result first.
	 * </p>
	 *
	 * <p>
	 *     <strong>Note</strong> that if no API key is required, but one is
	 *     provided in the request, the provided key will be considered
	 *     invalid.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @return {@code true} when the supplied API key is invalid, when it
	 * 		is required but missing, or when it is <em>not</em> required but
	 * 		present. {@code false} when the supplied API key is valid, or not
	 * 		required and missing.
	 */
	private boolean invalidApiKey(RoutingContext rc) {
		String key = rc.request().getHeader(HEADER_API_KEY);

		if (key == null) {
			try {
				key = rc.body().asJsonObject().getString("key");
			} catch (Exception e) {
				log.trace("Could not get key from request body: {}", e, e.getMessage());
			}
		}

		if (Objects.equals(apiKey, key)) {
			if (log.isTraceEnabled()) {
				log.trace("API key is {}", apiKey == null ? "not required" : "valid");
			}

			return false;
		}

		log.trace("Invalid API key: {}", key);

		return true;
	}
}
