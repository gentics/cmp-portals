package com.gentics.mesh.portal.content.preview;

import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.portal.api.cache.CacheProvider;
import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.api.server.PortalInfo;
import com.gentics.mesh.portal.cache.CachingHandler;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.content.ContentPathHandler;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.util.JsonUtils;
import com.gentics.mesh.portal.util.RxUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

@Singleton
public class ContentPreviewHandler extends ContentPathHandler {

	private static final String FIELD_LANGUAGE = "language";

	private final PreviewDataMapper previewDataMapper;

	@Inject
	public ContentPreviewHandler(
			PortalConfig config,
			MeshRestClientProvider meshClientProvider,
			QueryStore queryStore,
			CacheProvider cacheProvider,
			CachingHandler cachingHandler,
			PortalInfo portalInfo,
			MetricsService metricsService,
			PreviewDataMapper previewDataMapper) {
		super(config, meshClientProvider, queryStore, cacheProvider, cachingHandler, portalInfo, metricsService);

		this.previewDataMapper = previewDataMapper;
	}

	private JsonObject injectPreviewData(JsonObject folder, JsonObject previewData) {
		JsonArray folderBreadcrumbs = new JsonArray();

		if (folder == null) {
			log.warn("Folder data could not be loaded, navigation information will be missing");
		} else {
			folderBreadcrumbs = JsonUtils.getValue(folder, "node.breadcrumb", folderBreadcrumbs);
		}

		previewData = previewDataMapper.mapNodeResponse(previewData);

		JsonObject nodeBreadcrumb = new JsonObject()
			.put("schema", previewData.getJsonObject("schema"))
			.put("path", previewData.getString("path"))
			.put("lang", previewData.getString("language"))
			.put("fields", new JsonObject());
		JsonArray breadcrumbs = new JsonArray()
			.addAll(folderBreadcrumbs)
			.add(nodeBreadcrumb);

		return new JsonObject()
			.put("node", previewData
				.put("breadcrumb", breadcrumbs));
	}

	@Override
	protected void processPageData(RoutingContext rc, String path, JsonObject data) {
		super.processPageData(rc, path, data);
		prepareRender(rc);
	}

	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		String path = rc.pathParam("param0");
		JsonObject previewJson = rc.body() != null ? rc.body().asJsonObject() : null;

		log.debug("Handling preview for path {}", path);

		if (previewJson != null && previewJson.containsKey(FIELD_LANGUAGE)) {
			String lang = previewJson.getString(FIELD_LANGUAGE);

			log.debug("Setting language to \"{}\" from preview data", lang);
			prc.setLanguage(lang, LanguageSource.PAGE);
		}

		// load with admin client
		loadByPath(path, rc, true)
			.map(GraphQLResponse::getData)
			.map(data -> injectPreviewData(data, previewJson))
			.to(RxUtils.observable())
			.subscribe(data -> processPageData(rc, path, data), rc::fail);
	}

	/**
	 * Set the preview mode according to the compatibility options.
	 *
	 * @param rc The current routing context
	 */
	public void setPreviewMode(RoutingContext rc) {
		log.debug("Preparing preview mode");

		wrap(rc).enablePreview(config.getCompatibilityOptions()).enableNavigation();
		rc.next();
	}
}
