package com.gentics.mesh.portal.server.core;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.portal.EncryptionService;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.authentication.AuthenticationHandler;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.config.MeshAuthenticationMode;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CachingOptions;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.context.UrlParameterInjector;
import com.gentics.mesh.portal.helper.CommonHelper;
import com.gentics.mesh.portal.helper.EvaluateHelper;
import com.gentics.mesh.portal.helper.LanguageHelper;
import com.gentics.mesh.portal.helper.NavigationHelper;
import com.gentics.mesh.portal.search.SearchResultHelper;
import com.gentics.mesh.portal.server.dagger.NoRedirect;
import com.gentics.mesh.portal.util.WriteStreamWrapper;
import com.gentics.mesh.rest.client.MeshWebsocket;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.StringHelpers;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.impl.PipeImpl;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.handlebars.HandlebarsTemplateEngine;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http.HttpMethod;
import okio.BufferedSink;
import okio.Okio;

/**
 * Container class for various handlers which can be shared across multiple verticles.
 */
@Singleton
public class HandlerProvider {

	/**
	 * Callback for asynchronous requests with the OkHttp client.
	 */
	private static class ProxyCallback implements Callback {

		private final RoutingContext rc;

		public ProxyCallback(RoutingContext rc) {
			this.rc = rc;
		}

		/**
		 * Checks if the cause is a timeout exception, and if so fails the
		 * request with a <em>504 Gateway Timeout</em>, otherwise the request
		 * is failed with the supplied error.
		 *
		 * @param call The failed call
		 * @param error The error that occurred
		 */
		@Override
		public void onFailure(Call call, @Nonnull IOException error) {
			log.error(
				"Could not forward request for {} to Mesh: {}",
				call.request().url(),
				error.getMessage(),
				error);

			if (error.getCause() instanceof SocketTimeoutException) {
				rc.fail(HttpResponseStatus.GATEWAY_TIMEOUT.code());
			} else {
				rc.fail(error);
			}
		}

		/**
		 * Removes blacklisted headers, and forwards the response with the
		 * remaining headers, the status code and the body.
		 *
		 * @param call The succeeded call
		 * @param response The response from the Mesh plugin
		 * @throws IOException When the response body cannot be read
		 */
		@Override
		public void onResponse(@Nonnull Call call, Response response) throws IOException {
			Buffer body = response.body() == null ? Buffer.buffer() : Buffer.buffer(response.body().bytes());
			int statusCode = response.code();

			if (log.isDebugEnabled()) {
				log.debug("Response from {}: {}", call.request().url(), statusCode);
			}

			HttpServerResponse forwardResponse = rc.response();

			log.trace("Response headers:");

			for (Map.Entry<String, List<String>> header : response.headers().toMultimap().entrySet()) {
				String headerName = header.getKey();

				if (headerBlacklist.contains(headerName.toLowerCase())) {
					if (log.isTraceEnabled()) {
						log.trace("Skipping blacklisted header: {}: {}", headerName, String.join(", ", header.getValue()));
					}

					continue;
				}

				if (log.isTraceEnabled()) {
					log.trace("  - {}: {}", headerName, String.join(", ", header.getValue()));
				}

				forwardResponse.putHeader(headerName, header.getValue());
			}

			if (log.isTraceEnabled()) {
				log.trace("Response body:\n========== BEGIN ==========\n{}\n==========  END  ==========", body);
			}

			forwardResponse
				.setStatusCode(statusCode)
				.end(body);
		}
	}

	private static final Logger log = LoggerFactory.getLogger(HandlerProvider.class);

	/**
	 * Set of HTTP headers which will <em>not</em> be forwarded from Mesh to the client.
	 *
	 * TODO: make this configurable
	 */
	private static final Set<String> headerBlacklist = Stream.of(
		"X-Powered-By").map(String::toLowerCase).collect(Collectors.toSet());

	private final PortalConfig config;

	private final HandlebarsTemplateEngine engine;

	private final CommonHelper commonHelper;

	private final LanguageHelper languageHelper;

	private final EvaluateHelper evaluateHelper;

	private final NavigationHelper navigationHelper;

	private final SearchResultHelper searchResultHelper;

	private final QueryStore queryStore;

	private final CachingHandler cachingHandler;

	private final OkHttpClient okHttpClient;

	private final String baseProxyUrl;

	private final MeshOptions meshOptions;

	private final Set<String> allowedHeaders;

	private final CookieProvider cookieProvider;

	private final EncryptionService encryptionService;

	private Function<RoutingContext, String> authCookieExtractor;

	private final MeshWebsocket meshWebsocket;

	@Inject
	public HandlerProvider(
			PortalConfig config,
			HandlebarsTemplateEngine engine,
			CookieProvider cookieProvider,
			EncryptionService encryptionService,
			CommonHelper commonHelper,
			LanguageHelper languageHelper,
			EvaluateHelper evaluateHelper,
			NavigationHelper navigationHelper,
			SearchResultHelper searchResultHelper,
			QueryStore queryStore,
			CachingHandler cachingHandler,
			MeshWebsocket meshWebsocket,
			@NoRedirect OkHttpClient okHttpClient) {
		MeshOptions meshOptions = config.getMeshOptions();

		this.config = config;
		this.engine = engine;
		this.commonHelper = commonHelper;
		this.languageHelper = languageHelper;
		this.evaluateHelper = evaluateHelper;
		this.navigationHelper = navigationHelper;
		this.searchResultHelper = searchResultHelper;
		this.queryStore = queryStore;
		this.cachingHandler = cachingHandler;
		this.okHttpClient = okHttpClient;
		this.meshWebsocket = meshWebsocket;
		this.baseProxyUrl = createBaseUrl(meshOptions);
		this.meshOptions = meshOptions;
		this.allowedHeaders = meshOptions.getProxyHeaderWhitelist().stream()
			.map(String::toLowerCase)
			.collect(Collectors.toSet());
		this.cookieProvider = cookieProvider;
		this.encryptionService = encryptionService;
		this.authCookieExtractor = getAuthCookieExtractor(true);

		init();
	}

	private void init() {
		// @TODO make CookieDisclaimerAccepted configurable
		Handlebars handlebars = engine.unwrap();

		handlebars.registerHelpers(commonHelper);
		handlebars.registerHelpers(navigationHelper);
		handlebars.registerHelpers(languageHelper);

		if (config.getSearchOptions().isEnabled()) {
			handlebars.registerHelpers(searchResultHelper);
		}

		handlebars.registerHelper("evaluate", evaluateHelper);
		handlebars.setInfiniteLoops(true);

		for (StringHelpers helper : StringHelpers.values()) {
			helper.registerHelper(handlebars);
		}

		CachingOptions options = config.getCachingOptions();

		// TODO let CacheProvider handle this.
		if (!options.isEnabled()) {
			cachingHandler.disable();
			queryStore.disableCache();
		}
	}

	public void registerHelper(Object helperSource) {
		engine.<Handlebars>unwrap().registerHelpers(helperSource);
	}

	/**
	 * Convenience method for {@link #pluginProxy(String, String, boolean, UrlParameterInjector, boolean)} which does
	 * not use the admin client, an empty parameter injector and does not recreate the Mesh URL.
	 *
	 * <p>
	 *     This method will return a proxy using the OkHttp client.
	 * </p>
	 *
	 * @param regex Regular expression for replacing the original URI
	 * @param replacement Replacement for replacing the original URI
	 * @return Proxy handler forwarding requests to Mesh
	 */
	public Handler<RoutingContext> pluginProxy(String regex, String replacement) {
		return pluginProxy(regex, replacement, false, new UrlParameterInjector(), false);
	}

	/**
	 * Return a proxy handler, that will forward requests to Mesh. The route must use the order {@link RouteOrder#BEFORE_BODY_HANDLER} for the proxy to work.
	 *
	 * <p>
	 *     This method will return a proxy using the OkHttp client.
	 * </p>
	 *
	 * @param regex Regular expression for replacing the original URI
	 * @param replacement Replacement for replacing the original URI
	 * @param allowAdminClient Whether to the the Mesh API token for the admin client, when no user token is available
	 * @param parameterInjector Provider for additional query parameters to add to the proxy URL
	 * @param forTesting Whether the proxy is created for testing, in which case some settings will be read again from
	 * 		the configuration (like the Mesh base URL or authentication mode).
	 * @return Proxy handler forwarding requests to Mesh
	 */
	public Handler<RoutingContext> pluginProxy(
			String regex,
			String replacement,
			boolean allowAdminClient,
			UrlParameterInjector parameterInjector,
			boolean forTesting) {
		return rc -> {
			HttpServerRequest request = rc.request();
			String method = request.method().toString();
			String proxyUri = (forTesting ? createBaseUrl(config.getMeshOptions()) : baseProxyUrl)
				+ parameterInjector.inject(wrap(rc), request.uri()).replaceAll(regex, replacement);

			if (log.isDebugEnabled()) {
				log.debug(
					"Forwarding {} request to Mesh via OkHttp: {} -> {}",
					rc.request().method().name(),
					rc.request().path(),
					proxyUri);
			}

			int contentLenth = -1;

			try {
				contentLenth = Integer.parseInt(request.getHeader(HttpHeaders.CONTENT_LENGTH));
			} catch (NumberFormatException e) {
				// Cannot do anything about that.
			}

			int finalContentLength = contentLenth;

			RequestBody requestBody = null;
			WriteStreamWrapper wrapper = null;

			// create a requestbody, if the forwarded method permits it
			if (HttpMethod.permitsRequestBody(method)) {
				String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
				if (StringUtils.isEmpty(contentType)) {
					log.warn("Request body is not empty, but no Content-Type header is present, falling back to application/json");
					contentType = "application/json";
				}
				MediaType mediaType = MediaType.parse(contentType);

				// we use an instance of WriteStreamWrapper, which will consume the body from the original request
				// and provides an InputStream for okhttp Client to read the body
				wrapper = new WriteStreamWrapper();
				WriteStreamWrapper finalWrapper = wrapper;

				// when the incoming request ends, the WriteStreamWrapper also ends
				request.endHandler(v -> finalWrapper.end());


				requestBody = new RequestBody() {
					@Override
					public void writeTo(@Nonnull BufferedSink sink) throws IOException {
						// consume the InputStream to write the data to the sink
						sink.writeAll(Okio.source(finalWrapper.createInputStream()));
					}

					@Override
					public long contentLength() {
						return finalContentLength;
					}

					@Override
					public MediaType contentType() {
						return mediaType;
					}
				};
			}

			// forward all headers
			Headers headers = forwardAuthenticationOkHttp(request.headers(), getAuthCookieExtractor(forTesting).apply(rc), allowAdminClient);

			Request forwardRequest = new Builder()
				.headers(headers)
				.url(proxyUri)
				.method(request.method().toString(), requestBody)
				.build();

			if (log.isDebugEnabled()) {
				log.debug("Forward request to {}", forwardRequest.url());

				if (log.isTraceEnabled()) {
					log.trace("Forward request headers:\n{}", forwardRequest.headers());
					if (requestBody != null) {
						log.trace("Forward request body size: {}", finalContentLength);
					}
				}
			}

			// create a new asynchronous call
			Call call = okHttpClient.newCall(forwardRequest);
			// when the request fails with an exception, cancel the call
			request.exceptionHandler(e -> call.cancel());
			// enqueue the call and let the ProxyCallback handle the response
			call.enqueue(new ProxyCallback(rc));

			// if a WriteStreamWrapper is present (meaning, that we forward the request body),
			// use a Pipe to forward the original request body in chunks
			if (wrapper != null) {
				new PipeImpl<>(request).to(wrapper.setWriteQueueMaxSize(config.getMeshOptions().getWriteQueueMaxSize()));
			}
		};
	}

	/**
	 * Make sure the forwarded request has the correct authentication information.
	 *
	 * <p>
	 * <strong>NOTE</strong> that this method will remove the <code>Cookie</code> header from the <code>headers</code> if it is present.
	 * </p>
	 *
	 * @param origHeader
	 *            The headers of the original request
	 * @param authCookieValue
	 *            The value of the authentication cookie from the original request
	 * @param allowAdminClient
	 *            Whether to use the admin credentials when no authentication cookie is available
	 * @return The possible modified headers of the original request
	 */
	private Headers forwardAuthenticationOkHttp(
			MultiMap origHeader,
			String authCookieValue,
			boolean allowAdminClient) {
		Headers.Builder headers = new Headers.Builder();

		forwardAuthentication(origHeader, authCookieValue, allowAdminClient)
			.forEach(h -> headers.add(h.getKey(), h.getValue()));

		return headers.build();
	}

	/**
	 * Make sure the forwarded request has the correct authentication information.
	 *
	 * <p>
	 * <strong>NOTE</strong> that this method will remove the <code>Cookie</code> header from the <code>headers</code> if it is present.
	 * </p>
	 *
	 * @param headers The headers of the original request.
	 * @param authCookieValue The value of the authentication cookie from the original request.
	 * @param allowAdminClient Whether to use the admin credentials when no authentication cookie is available.
	 * @return The possible modified headers of the original request
	 */
	private MultiMap forwardAuthentication(
		MultiMap headers,
		String authCookieValue,
		boolean allowAdminClient) {
		String cookie = applyCookieWhitelist(headers.get(HttpHeaders.COOKIE));

		if (cookie != null) {
			headers.remove(HttpHeaders.COOKIE);
		}

		String token = null;

		if (StringUtils.isNotBlank(authCookieValue)) {
			log.debug("Using token from authentication cookie");
			token = authCookieValue;
		} else if (allowAdminClient) {
			log.debug("Using admin client token");
			token = config.getMeshOptions().getApiKey();
		}

		if (StringUtils.isNotBlank(token)) {
			MeshAuthenticationMode authMode = meshOptions.getAuthenticationMode();

			if (authMode == MeshAuthenticationMode.BOTH || authMode == MeshAuthenticationMode.HEADER) {
				headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
			}

			if (authMode == MeshAuthenticationMode.BOTH || authMode == MeshAuthenticationMode.COOKIE) {
				token = "mesh.token=" + token;

				if (StringUtils.isBlank(cookie)) {
					cookie = token;
				} else {
					cookie += "; " + token;
				}
			}
		}

		if (!StringUtils.isBlank(cookie)) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}

		log.debug("Applying proxy header whitelist");

		headers.names().stream()
			.filter(header -> !allowedHeaders.contains(header.toLowerCase()))
			.forEach(header -> {
				if (log.isDebugEnabled()) {
					log.debug(
						"Removing header {}: {}",
						header,
						String.join(", ", headers.getAll(header)));
				}

				headers.remove(header);
			});

		return headers;
	}

	public CachingHandler cachingHandler() {
		return cachingHandler;
	}

	/**
	 * Create the base Mesh URL from the configuration.
	 *
	 * @param meshOptions The configured Mesh options
	 * @return The Mesh base URL as a String
	 */
	private String createBaseUrl(MeshOptions meshOptions) {
		return String.format(
			"http%s://%s:%d",
			meshOptions.isSsl() ? "s" : "",
			meshOptions.getHost(),
			meshOptions.getPort());
	}

	/**
	 * Remove all cookie values from the given string which are not in the forward cookie whitelist.
	 *
	 * @param originalCookies The original cookie values.
	 * @return The cookies values that are whitelisted for forwarding to Mesh.
	 */
	private String applyCookieWhitelist(String originalCookies) {
		if (StringUtils.isBlank(originalCookies)) {
			return originalCookies;
		}

		Set<String> whitelist = config.getMeshOptions().getCookieForwardWhitelist();

		if (log.isDebugEnabled()) {
			log.debug("Applying cookie whitelist: [{}]", String.join(", ", whitelist));
		}

		return Stream.of(originalCookies.split(";"))
			.map(String::trim)
			// Only allow cookies with a non-empty name which are in the cookie forward whitelist.
			.filter(s -> s.indexOf('=') > 0 && whitelist.contains(s.substring(0, s.indexOf('='))))
			.collect(Collectors.joining("; "));
	}

	/**
	 * Create the function to extract the authentication cookie value from the
	 * given routing context.
	 *
	 * <p>
	 *     The value will be retrieved from the {@link CookieProvider} and
	 *     decrypted if {@link SecurityOptions#isEncryptCookies() encrypted cookies}
	 *     are enabled.
	 * </p>
	 *
	 * <p>
	 *     When the extractor has already been set, it will only be recreated
	 *     if {@code forceCreate} is {@code true}.
	 * </p>
	 *
	 * @param forceCreate Whether to force recreation of the extractor.
	 *
	 * @return A function that extracts the authentication cookie value from the
	 * 		routing context.
	 */
	private Function<RoutingContext, String> getAuthCookieExtractor(boolean forceCreate) {
		if (authCookieExtractor == null || forceCreate) {
			Function<String, String> decrypter = config.getSecurityOptions().isEncryptCookies()
				? encryptionService::decrypt
				: Function.identity();

			authCookieExtractor = rc -> Optional.ofNullable(cookieProvider.get(rc, AuthenticationHandler.COOKIE_AUTH))
				.map(cookie -> decrypter.apply(cookie.getValue()))
				.orElse(null);
		}

		return authCookieExtractor;
	}

	public MeshWebsocket meshWebsocket() {
		return meshWebsocket;
	}

	public OkHttpClient okHttpClient() {
		return okHttpClient;
	}

}
