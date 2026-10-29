package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.portal.config.PortalConfig;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Http2PushMapping;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * Static file handler for the FormGen app.
 *
 * <p>
 *     Adds {@code "Access-Control-Allow-Origin} headers which allow any origin for downloading files belonging
 *     to the FormGen app.
 * </p>
 */
@Singleton
public class FormGenStaticHandler implements StaticHandler {

	private final String formgenServePath;
	private final StaticHandler delegate;

	@Inject
	public FormGenStaticHandler(PortalConfig config) {
		var formsOptions = config.getFormsOptions();
		var staticHandlerOptions = config.getStaticHandlerOptions();

		this.formgenServePath = formsOptions.getFormgenStaticServePath();

		this.delegate = StaticHandler.create(formsOptions.getFormgenStaticFileDirectory());
		this.delegate.setDirectoryListing(false)
			.setCachingEnabled(staticHandlerOptions.isCaching())
			.setMaxAgeSeconds(staticHandlerOptions.getMaxAge());
	}

	@Override
	public void handle(RoutingContext rc) {
		var requestPath = rc.request().path();

		if (requestPath.startsWith(formgenServePath)) {
			rc.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		}

		delegate.handle(rc);
	}

	@Override
	public StaticHandler setFilesReadOnly(boolean readOnly) {
		return delegate.setFilesReadOnly(readOnly);
	}

	@Override
	public StaticHandler setMaxAgeSeconds(long maxAgeSeconds) {
		return delegate.setMaxAgeSeconds(maxAgeSeconds);
	}

	@Override
	public StaticHandler setCachingEnabled(boolean enabled) {
		return delegate.setCachingEnabled(enabled);
	}

	@Override
	public StaticHandler setDirectoryListing(boolean directoryListing) {
		return delegate.setDirectoryListing(directoryListing);
	}

	@Override
	public StaticHandler setIncludeHidden(boolean includeHidden) {
		return delegate.setIncludeHidden(includeHidden);
	}

	@Override
	public StaticHandler setCacheEntryTimeout(long timeout) {
		return delegate.setCacheEntryTimeout(timeout);
	}

	@Override
	public StaticHandler setIndexPage(String indexPage) {
		return delegate.setIndexPage(indexPage);
	}

	@Override
	public StaticHandler setMaxCacheSize(int maxCacheSize) {
		return delegate.setMaxCacheSize(maxCacheSize);
	}

	@Override
	public StaticHandler setHttp2PushMapping(List<Http2PushMapping> http2PushMappings) {
		return delegate.setHttp2PushMapping(http2PushMappings);
	}

	@Override
	public StaticHandler skipCompressionForMediaTypes(Set<String> mediaTypes) {
		return delegate.skipCompressionForMediaTypes(mediaTypes);
	}

	@Override
	public StaticHandler skipCompressionForSuffixes(Set<String> fileSuffixes) {
		return delegate.skipCompressionForSuffixes(fileSuffixes);
	}

	@Override
	public StaticHandler setAlwaysAsyncFS(boolean alwaysAsyncFS) {
		return delegate.setAlwaysAsyncFS(alwaysAsyncFS);
	}

	@Override
	public StaticHandler setEnableFSTuning(boolean enableFSTuning) {
		return delegate.setEnableFSTuning(enableFSTuning);
	}

	@Override
	public StaticHandler setMaxAvgServeTimeNs(long maxAvgServeTimeNanoSeconds) {
		return delegate.setMaxAvgServeTimeNs(maxAvgServeTimeNanoSeconds);
	}

	@Override
	public StaticHandler setDirectoryTemplate(String directoryTemplate) {
		return delegate.setDirectoryTemplate(directoryTemplate);
	}

	@Override
	public StaticHandler setEnableRangeSupport(boolean enableRangeSupport) {
		return delegate.setEnableRangeSupport(enableRangeSupport);
	}

	@Override
	public StaticHandler setSendVaryHeader(boolean varyHeader) {
		return delegate.setSendVaryHeader(varyHeader);
	}

	@Override
	public StaticHandler setDefaultContentEncoding(String contentEncoding) {
		return delegate.setDefaultContentEncoding(contentEncoding);
	}
}
