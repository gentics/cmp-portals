package com.gentics.mesh.portal.util;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.monitoring.PortalTimer;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.context.RedirectMode;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.client.MeshRestClientMessageException;
import com.google.common.net.HttpHeaders;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.MimeMapping;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Miscellaneous helpers for portal handlers.
 */
public class HandlerUtils {

	private static final Logger log = LoggerFactory.getLogger(HandlerUtils.class);

	/** Regular expression to match consecutive slashes or backslashes. */
	private static final Pattern SLASH_PATTERN = Pattern.compile("[\\\\/]{2,}");

	/** Regular expression to match URL protocol and domain. */
	private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(?:https?:)?[\\\\/]{2,}");

	/** Regular expression to match newline characters. */
	private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\r\n]*");

	/** Response headers forwarded from Mesh to the client for binary responses. */
	private static final List<String> FORWARDED_HEADERS = Arrays.asList(
		HttpHeaders.ETAG,
		HttpHeaders.ACCEPT_RANGES,
		HttpHeaders.CONTENT_RANGE);

	/**
	 * Create a {@link io.vertx.ext.web.Route#failureHandler(Handler) failure handler}
	 * that will log an error, and end the request with <em>500 Internal Server Error</em>.
	 *
	 * @param location The handler class that added this failure handler
	 * @return A failure handler that will log an error and end the request
	 */
	public static Handler<RoutingContext> unrecoverableFailureHandler(Class<?> location) {
		return rc -> {
			HttpServerRequest request = rc.request();
			Throwable error = rc.failure();
			String msg = "Error in {} handling request {} {}: {}";

			if (log.isTraceEnabled()) {
				log.error(msg, location.getName(), request.method().name(), request.absoluteURI(), error.getMessage(), error);
			} else {
				log.error(msg, location.getName(), request.method().name(), request.absoluteURI(), error.getMessage());
			}

			HttpServerResponse response = rc.response();

			if (response.ended()) {
				log.warn("Response was already sent, nothing more to do");
			} else {
				response
					.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
					.end();
			}
		};
	}

	// TODO move fileDownloadPipe() to its own BinaryHandler class and
	// * remove the eTagHandler parameter (inject the CachingHandler)
	// * inject that BinaryHandler class into ContentPathHandler instead of the CachingHandler
	// * let the GIS handler extend the BinaryHandler and also enable ETag caching there
	/**
	 * Return a consumer for a binary webroot response to be used as a callback for <code>subscribe</code>.
	 *
	 * <p>
	 * When the webroot response does not specify a MIME type it is inferred from the filename.
	 * </p>
	 *
	 * @param path The path of the loaded binary
	 * @param rc The current routing context
	 * @param timer portaltimer for monitoring download time
	 * @param maxAge The {@code max-age} value for the {@code cache-control} header
	 * @param eTagHandler optional etag handler (will be called with the etag header coming from mesh)
	 * @return a consumer for a binary webroot response to be used as a callback for <code>subscribe()</code>
	 */
	public static Consumer<MeshResponse<?>> fileDownloadPipe(String path, RoutingContext rc, PortalTimer timer, int maxAge, java.util.function.Consumer<String> eTagHandler) {
		return rawResponse -> {
			int status = rawResponse.getStatusCode();
			HttpServerResponse response = rc.response().setStatusCode(status);

			if (status < 200 || status >= 300) {
				if (status == HttpResponseStatus.NOT_MODIFIED.code()) {
					log.debug("Resource was not modified");
					rawResponse.close();
					response.end();
				} else {
					log.debug("Failing request due to Mesh response status code {}", status);
					rawResponse.close();
					rc.fail(status);
				}

				timer.stop();

				return;
			}

			MeshWebrootResponseWrapper meshResponse = MeshWebrootResponseWrapper.wrap(rawResponse.getBody());

			if (log.isDebugEnabled()) {
				log.debug(
					"Mesh response: status: {}, headers:\n{}",
					status,
					rawResponse.getHeaders().entrySet().stream()
						.map(e -> "  " + e.getKey() + ": " + String.join(", ", e.getValue()))
						.collect(Collectors.joining("\n")));

				if (meshResponse != null && !meshResponse.isBinary()) {
					log.trace("Response body:\n{}", meshResponse.getResponseAsJsonString());
				}
			}


			// The webroot API may have returned binary data for the specified path. We
			// pass the binary data along and return it to the client.
			if (meshResponse != null && meshResponse.isBinary()) {
				String mimeType = meshResponse.getBinaryResponse().getContentType();

				if (StringUtils.isBlank(mimeType)) {
					// Fallback to determine the mimetype via extension.
					log.trace("Falling back to MIME type for filename: {}", path);
					mimeType = MimeMapping.mimeTypeForFilename(path.toLowerCase());
				}

				response
					.putHeader(HttpHeaders.CONTENT_TYPE, mimeType)
					.putHeader(HttpHeaders.CACHE_CONTROL, "max-age=" + maxAge);

				Optional<String> length = rawResponse.getHeader(HttpHeaders.CONTENT_LENGTH);

				if (length.isPresent()) {
					response.putHeader(HttpHeaders.CONTENT_LENGTH, length.get());
				} else {
					response.setChunked(true);
				}

				FORWARDED_HEADERS.forEach(
					header -> rawResponse.getHeader(header).ifPresent(value -> response.putHeader(header, value)));

				if (eTagHandler != null) {
					rawResponse.getHeader(HttpHeaders.ETAG).ifPresent(eTagHandler);
				}

				if (log.isTraceEnabled()) {
					log.trace(
						"Response status: {}, headers:\n{}",
						status,
						response.headers().entries().stream()
							.map(e -> "  " + e.getKey() + ": " + String.join(", ", e.getValue()))
							.collect(Collectors.joining("\n")));
				}

				meshResponse.getBinaryResponse().getFlowable()
					.map(Buffer::buffer)
					.doFinally(() -> timer.stop())
					.subscribe(response::write, rc::fail, response::end);

				// Note that we are not calling rc.next() which will prevent the execution of the template route handler.
				// Instead response().end() is called in the subscription of the flowable above.
			} else {
				log.warn("Received 2xx response from Mesh without body");

				// it is important to close the response
				rawResponse.close();

				timer.stop();
				response.end();
			}
		};
	}

	/**
	 * Convenience method calling {@link #simpleFailureHandler(RoutingContext, String)} with an empty message.
	 *
	 * @param rc The current routing context
	 * @return A consumer to be used as an error callback for <code>subscribe()</code>.
	 */
	public static Consumer<Throwable> simpleFailureHandler(RoutingContext rc) {
		return simpleFailureHandler(rc, null);
	}

	/**
	 * Returns a consumer to be used as an error callback for <code>subscribe()</code>.
	 *
	 * <p>
	 * When the passed error is a {@link MeshRestClientMessageException} the request will be failed with the specified
	 * {@link MeshRestClientMessageException#getStatusCode() status code}, otherwise it will be failed with {@link
	 * HttpResponseStatus#INTERNAL_SERVER_ERROR INTERNAL SERVER ERROR}.
	 * </p>
	 *
	 * <p>
	 * The optional message will be logged together with the throwable.
	 * </p>
	 *
	 * @param rc The current routing context
	 * @param msg An optional error message
	 * @return A consumer to be used as an error callback for <code>subscribe()</code>.
	 */
	public static Consumer<Throwable> simpleFailureHandler(RoutingContext rc, String msg) {
		return cause -> {
			if (StringUtils.isBlank(msg)) {
				log.error("Handler error: {}", cause.getMessage(), cause);
			} else {
				log.error(msg, cause);
			}

			if (cause instanceof MeshRestClientMessageException) {
				rc.fail(((MeshRestClientMessageException) cause).getStatusCode());
			} else {
				rc.fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
			}
		};
	}

	/**
	 * Converts a Vert.x multimap to a java map. If multiple entries with the same name are found, the last entry will
	 * be used.
	 *
	 * @param map The multi map to convert to a normal map
	 * @return A normal map containing the same keys as the given multi map
	 */
	public static Map<String, String> toMap(MultiMap map) {
		return map.entries().stream().collect(Collectors.toMap(
			Map.Entry::getKey,
			Map.Entry::getValue,
			(a, b) -> b
		));
	}

	/**
	 * Converts a Vert.x multimap to a java map. If multiple entries with the same name are found, the last entry will
	 * be used.
	 *
	 * @param map The multi map to convert to a normal map
	 * @param excluded The keys which should be excluded from the resulting map
	 * @return A normal map containing the same keys as the given multi map
	 */
	public static Map<String, String> toMapExcluding(MultiMap map, boolean ignoreCase, String ...excluded) {
		List<String> excludeSet = ignoreCase
			? Arrays.stream(excluded).map(String::toLowerCase).collect(Collectors.toList())
			: Arrays.asList(excluded);

		if (log.isDebugEnabled()) {
			log.debug("Filtering map entries case{}", ignoreCase ? "-insensitive" : "");
		}

		return map.entries().stream()
			.filter(e -> !excludeSet.contains(ignoreCase ? e.getKey().toLowerCase() : e.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
	}

	/**
	 * Additional query parameters that need to be added to proxied requests to Mesh.
	 *
	 * <p>
	 *     The default implementation will add the branch name, when a branch is set in the
	 *     {@link PortalRoutingContext}.
	 * </p>
	 *
	 * @see PortalRoutingContext#setBranch(BranchResponse)
	 * @see PortalRoutingContext#getBranch()
	 *
	 * @param params The request parameters to be extended
	 * @param pc The current routing context
	 */
	public static void addBranchParameter(PortalRoutingContext pc, MultiMap params) {
		BranchResponse branch = pc.getBranch();
		String branchName = branch == null ? null : branch.getName();

		if (!StringUtils.isBlank(branchName)) {
			params.add("branch", branchName);
			log.debug("Adding branch parameter: {}", branchName);
		} else {
			log.trace("No branch set");
		}
	}

	/**
	 * Get the value with the highest value for <code>q=</code> from an HTTP header with weighted values.
	 *
	 * <p>
	 *     When several values have the same weight, the first encountered value is returned.
	 * </p>
	 *
	 * @param weightedHeaderValue The value of the HTTP header
	 * @return The value with the highest weight
	 */
	public static String preferredValue(String weightedHeaderValue) {
		if (StringUtils.isBlank(weightedHeaderValue)) {
			return null;
		}

		// Split at ',' characters and remove whitespace and empty fields.
		String[] values = weightedHeaderValue.trim().split("\\s*,[,\\s]*");
		String bestValue = null;
		float bestWeight = 0;

		for (String value : values) {
			String[] parts = value.split(";");

			float weight;

			try {
				weight = parts.length < 2
 					// Missing weight is equal to 1
 					? 1
					// Strip the "q=" from the beginning.
					: Float.parseFloat(parts[1].substring(2));
			} catch (Exception e) {
				log.warn("Invalid HTTP header value: {}", weightedHeaderValue);

				return null;
			}

			if (bestValue == null || bestWeight < weight) {
				bestValue = value;
				bestWeight = weight;
			}

			if (bestWeight >= 1.0) {
				break;
			}
		}

		return bestValue;
	}

	/**
	 * Create a map from language to path from an array of {@link JsonObject JsonObjects} containing
	 * <code>language</code>, and <code>path</code> fields
	 *
	 * @param languages The available languages to map
	 * @return A mapping from language to path
	 */
	public static Map<String, String> mapLanguageToPath(JsonArray languages) {
		Map<String, String> ret = languages.stream()
			.map(l -> (JsonObject) l)
			.map(l -> Pair.of(l.getString("language"), l.getString("path")))
			.collect(Collectors.toMap(Pair::getKey, Pair::getValue));

		if (log.isTraceEnabled()) {
			log.trace("Loaded languages: {}", ret);
		}

		return ret;
	}

	/**
	 * Determine whether cookies should set the <em>secure</em> flag.
	 *
	 * @deprecated Use {@link CookieProvider#create(String, String)} or
	 * 		{@link CookieProvider#delete(String)} instead
	 * @param config The current portal configuration
	 * @return <code>true</code> when the protocol of the {@link PortalConfig#getServerUrl() server URL}
	 * 		is <code>https</code>, and <code>false</code> otherwise.
	 */
	@Deprecated
	public static boolean forceSecureCookies(PortalConfig config) {
		return Optional.ofNullable(config)
			.map(PortalConfig::getServerUrl)
			.map(URL::getProtocol)
			.orElse("")
			.equals("https");
	}

	/**
	 * Sanitize the given redirect URL or path according to the specified
	 * mode.
	 *
	 * <ul>
	 *     <li>
	 *         {@code TRUSTED}: the redirect is returned as is
	 *     </li>
	 *     <li>
	 *         {@code EXTERNAL}: remove only newlines from the redirect
	 *     </li>
	 *     <li>
	 *         {@code INTERNAL}: remove newlines, protocol and domain as well
	 *         as leading double slashes or backslashes, also consecutive
	 *         slashes will be replaced with a single slash
	 *     </li>
	 * </ul>
	 *
	 * @param redirect The path URL to sanitize
	 * @param mode The rules to be enforced by sanitazing
	 * @return The given redirect sanitized according to the specified {@code mdoe}
	 */
	public static String sanitizeRedirect(String redirect, RedirectMode mode) {
		if (mode == RedirectMode.TRUSTED || redirect == null) {
			return redirect;
		}

		// Remove any newline characters.
		String sanitized = NEWLINE_PATTERN.matcher(redirect).replaceAll("");

		if (mode == RedirectMode.EXTERNAL) {
			return sanitized;
		}

		// Remove protocol or leading double slashes
		sanitized = PROTOCOL_PATTERN.matcher(sanitized).replaceFirst("");

		// Ignore anything before the first slash since this would be
		// interpreted as a hostname.
		int idx = sanitized.indexOf('/');

		if (idx < 0) {
			idx = sanitized.indexOf('\\');
		}

		if (idx >= 0) {
			sanitized = sanitized.substring(idx);
		} else {
			sanitized = '/' + sanitized;
		}

		// Replace all remaining consecutive slashes with a single slash.
		sanitized = SLASH_PATTERN.matcher(sanitized).replaceAll("/");

		log.trace("Sanitized path {} to {}", redirect, sanitized);

		return sanitized;
	}
}
