package com.gentics.mesh.portal.gis;

import com.gentics.mesh.parameter.ImageManipulationParameters;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.api.monitoring.PortalMetric;
import com.gentics.mesh.portal.api.monitoring.PortalTimer;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.mesh.portal.util.RxUtils;
import com.gentics.mesh.rest.client.MeshRequest;
import com.google.common.net.HttpHeaders;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Abstract handler for /GenticsImageStore URLs.
 * <br>
 * The GIS handlers work in two steps:
 * <ol>
 *   <li>Requests to /GenticsImageStore URLs will parse the URL into resizing data.
 *   	Resizing data is put into the routing context, and the request is rerouted to the URL of the original image</li>
 *   <li>All requests will be handled by the GIS handlers. If GIS data is found in the routing context, resized images will be requested from Mesh.</li>
 * </ol>
 * In this way, other handlers (like BranchSelectors) can handle the GIS requests exactly like requests to download the original image.
 */
public abstract class AbstractGisHandler implements Handler<RoutingContext> {

	protected static final Logger log = LoggerFactory.getLogger(GisHandler.class);

	private static final String FIELD_BINARYCONTENT = "binarycontent";

	protected final static String GIS_HANDLER = "mesh.portal.gis_handler";

	protected final static String WIDTH = "width";

	protected final static String HEIGHT = "height";

	protected final static String MODE = "mode";

	protected final static String PATH = "path";

	protected final static String WIDTH_PARAM = GIS_HANDLER + "." + WIDTH;

	protected final static String HEIGHT_PARAM = GIS_HANDLER + "." + HEIGHT;

	protected final static String MODE_PARAM = GIS_HANDLER + "." + MODE;

	protected final static String PATH_PARAM = GIS_HANDLER + "." + PATH;

	private final MetricsService metricsService;

	protected final MeshRestClientProvider meshClientProvider;
	protected final PortalConfig config;

	public AbstractGisHandler(MeshRestClientProvider meshClientProvider, PortalConfig config, MetricsService metricsService) {
		this.meshClientProvider = meshClientProvider;
		this.config = config;
		this.metricsService = metricsService;
	}

	/**
	 * Handle download of a resized (and cropped) image
	 *
	 * @param rc
	 *            routing context
	 * @param path
	 *            webroot path to the image
	 * @param params
	 *            resize parameters
	 */
	protected void handleDownload(RoutingContext rc, String path, ImageManipulationParameters params) {
		String projectName = config.getMeshProject();
		PortalTimer timer = metricsService.wrapTimer(PortalMetric.MESH_LOAD_BINARY);
		MeshRequest<?> request;

		if (config.getMeshOptions().isWebrootFieldAvailable()) {
			log.debug("Loading file {} via webroot field request", path);
			request = meshClientProvider.get(rc).webrootField(projectName, FIELD_BINARYCONTENT, path, params, wrap(rc).getVersion());
		} else {
			log.debug("Loading file {} via webroot request", path);
			request = meshClientProvider.get(rc).webroot(projectName, path, params, wrap(rc).getVersion());
		}

		// Pass request headers to the webroot request (e.g. for HTTP range requests). We explicitly do not forward the Authorization header,
		// cookies, and host because this has to be set by the Mesh REST client itself.
		request.setHeaders(HandlerUtils.toMapExcluding(rc.request().headers(), true, HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.HOST));

		request.getResponse()
			.observeOn(RxUtils.scheduler(rc))
			.doOnSubscribe(ignored -> timer.start())
			.doOnError(ignored -> timer.stop())
			.subscribe(
				HandlerUtils.fileDownloadPipe(path, rc, timer, config.getCachingOptions().getBinaryMaxAge(), null),
				HandlerUtils.simpleFailureHandler(rc, "Error while handling GIS path " + path));
	}

	/**
	 * Reroute gis requests to the image URL and set gis data into the routing context
	 * @param rc routing context
	 */
	public abstract void reroute(RoutingContext rc);
}
