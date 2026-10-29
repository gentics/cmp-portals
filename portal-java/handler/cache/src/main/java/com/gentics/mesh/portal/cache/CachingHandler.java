package com.gentics.mesh.portal.cache;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.mesh.core.rest.branch.BranchResponse;
import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.cache.CacheBuilder;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.cache.ExpireMode;
import com.gentics.mesh.portal.api.cache.PortalCache;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.monitoring.PortalMetric;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.CachingOptions;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.TemplateHandler;

/**
 * Handler which returns a cached response. Internally a websocket connection to the mesh server will be opened in order to clear the cache when the content
 * gets updated. The handler will also return an ETag for cached responses.
 */
@Singleton
public class CachingHandler extends AbstractBaseHandler {

	private static final Logger log = LoggerFactory.getLogger(CachingHandler.class);

	private final Vertx vertx;
	private final PortalCache<CacheEntry> cache;
	private final List<String> customCacheParameterFromRC;
	private final MetricsService metricsService;

	private boolean disabled = false;

	/**
	 * Create a new cache handler.
	 */
	@Inject
	public CachingHandler(Vertx vertx, PortalConfig portalConfig, CacheProvider cacheProvider, MetricsService metricsService) {
		this.vertx = vertx;
		this.metricsService = metricsService;

		CachingOptions options = portalConfig.getCachingOptions();

		if (!options.isEnabled()) {
			log.info("Not registering caching handler since caching is disabled.");

			this.cache = null;
			this.customCacheParameterFromRC = null;

			disable();

			return;
		}

		CacheBuilder<CacheEntry> builder = cacheProvider.<CacheEntry>cacheBuilder()
			.namespace("content")
			.expireMode(ExpireMode.Write)
			.expireTime(options.getExpireTime())
			.maximumSize(options.getMaximumSize())
			.diskCacheRegion(options.getDiskCacheRegionName())
			.clearEvents(cacheProvider.defaultClearEvents())
			.eventFilter(cacheProvider::defaultEventFilter)
			.keyMapper(this::keyMapper);

		this.cache = builder.build();

		this.cache.clear();

		this.customCacheParameterFromRC = options.getCustomParameters();

		log.info("CachingHandler initialized");

		if (log.isDebugEnabled()) {
			log.debug("Custom cache key parameters: [{}]", String.join(", ", customCacheParameterFromRC));
		}
	}

	@Override
	public void addRoutes(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withPathRegex(Constants.ROUTE_CATCH_ALL)
			.withRAMLPath("/:path")
			.withUriParameter("path", "Cached path", "/path/blume.jpg")
			.withMethod(HttpMethod.GET)
			.withOrder(RouteOrder.CACHING_ROUTES.getOrder())
			.withHandler(this)
			.hidden(true)
			.build();
	}

	@Override
	public void handle(RoutingContext rc) {
		if (disabled || cache == null) {
			rc.next();
			return;
		}

		// Do not use cached responses when caching is explicitly disabled for this request.
		if (wrap(rc).cacheDisabled()) {
			log.debug("Caching is disabled for this request");
			rc.next();

			return;
		}

		if (cache.isBlockingCache()) {
			vertx.<CacheEntry>executeBlocking(() -> cache.getIfPresent(rc), false)
			.andThen((AsyncResult<CacheEntry> rh) -> {
				if (rh.failed()) {
					rc.fail(rh.cause());
				} else {
					CacheEntry hit = rh.result();
					handleCacheResult(rc, hit);
				}

			});
		} else {
			CacheEntry hit = cache.getIfPresent(rc);
			handleCacheResult(rc, hit);
		}
	}

	private void handleCacheResult(RoutingContext rc, CacheEntry hit) {
		if (hit == null) {
			if (log.isDebugEnabled()) {
				log.debug("Cache MISS for key {}", keyMapper(rc));
			}

			metricsService.incCounter(PortalMetric.CACHE_MISS);
			rc.next();
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Cache HIT for key {}", keyMapper(rc));
			}

			metricsService.incCounter(PortalMetric.CACHE_HIT);

			// Check whether the request etag matches up with the found etag hash of the cache hit.
			// In this case we can just return a 304 instead of the actual content.
			if (matchesETag(rc, hit)) {
				rc.response()
					.setStatusCode(HttpResponseStatus.NOT_MODIFIED.code())
					.end();
			} else if (hit.getContent() != null) {
				rc.response()
					.putHeader(HttpHeaders.ETAG, hit.getEtagHeader())
					.putHeader(HttpHeaders.CONTENT_TYPE, String.format("%s; charset=UTF-8", TemplateHandler.DEFAULT_CONTENT_TYPE))
					.end(hit.getContent());
			} else {
				rc.next();
			}
		}
	}

	/**
	 * Check whether the etag of the request header matches up with the etag hash of the cache entry.
	 *
	 * @param rc
	 *            Routing context which contains the request information
	 * @param entry
	 *            Cache entry which contains the current hash for the request path
	 * @return <code>true</code> when the ETag in the requests <code>If-None-Match</code> header matches the ETag from the cached entry, and <code>false</code>
	 *         otherwise
	 */
	private boolean matchesETag(RoutingContext rc, CacheEntry entry) {
		String headerValue = rc.request().getHeader(HttpHeaders.IF_NONE_MATCH);

		if (headerValue == null) {
			return false;
		}

		return headerValue.equals(entry.getEtagHeader());
	}

	/**
	 * Clear the whole cache.
	 */
	public void clear() {
		if (cache != null) {
			cache.clear();
		}
	}

	/**
	 * Store the given content in the cache.
	 *
	 * @param rc
	 *            The current routing context
	 * @param content
	 *            The content to cache
	 */
	public void cache(RoutingContext rc, String content) {
		if (disabled || cache == null) {
			return;
		}

		if (wrap(rc).cacheDisabled()) {
			log.debug("Caching is disabled for this request");

			return;
		}

		CacheEntry entry = CacheEntry.content(content);

		if (log.isDebugEnabled()) {
			log.debug("Cache STORE element for key {}", keyMapper(rc));
		}

		cache.put(rc, entry);

		if (!rc.response().headWritten()) {
			// Append the etag to the response
			rc.response().putHeader(
				HttpHeaders.ETAG,
				entry.getEtagHeader());
		} else {
			log.warn("The header for " + rc.request().absoluteURI() + " is already written!");
		}
	}

	/**
	 * Store the given etag in the cache.
	 *
	 * @param rc
	 *            routing context
	 * @param eTag
	 *            etag
	 */
	public void cacheETag(RoutingContext rc, String eTag) {
		if (disabled || cache == null) {
			return;
		}

		if (wrap(rc).cacheDisabled()) {
			log.debug("Caching is disabled for this request");

			return;
		}

		CacheEntry entry = CacheEntry.eTag(eTag);

		if (log.isDebugEnabled()) {
			log.debug("Cache STORE element for key {}", keyMapper(rc));
		}

		cache.put(rc, entry);

		// Append the etag to the response
		rc.response().putHeader(
			HttpHeaders.ETAG,
			entry.getEtagHeader());
	}

	/**
	 * Creates a cache key for the request using the URI, portal language,
	 * user roles, branch name and custom cache parameters.
	 *
	 * @param rc
	 *            The current routing context
	 * @return The generated cache key
	 */
	private String keyMapper(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		BranchResponse branch = prc.getBranch();
		// We do not use rc.request().absoluteURI()) here as it will be `null` if invalid characters are in the URL
		// StringBuilder will then throw a NullPointerException
		StringBuilder cacheKey = new StringBuilder(rc.request().scheme() + "://" + rc.request().authority().host() + rc.request().uri());

		cacheKey
			.append('|')
			.append(prc.getLanguage())
			.append('|')
			.append(prc.getUserRolesHash())
			.append('|')
			.append(branch == null ? "default-branch" : branch.getUuid());

		for (String key : customCacheParameterFromRC) {
			Object val = rc.get(key);

			cacheKey
				.append('|')
				.append(key)
				.append("=")
				.append(val == null ? "" : val.toString());
		}

		return cacheKey.toString();
	}

	/**
	 * Disable the cache handler.
	 */
	public void disable() {
		log.debug("Disabling cache");

		disabled = true;
	}
}
