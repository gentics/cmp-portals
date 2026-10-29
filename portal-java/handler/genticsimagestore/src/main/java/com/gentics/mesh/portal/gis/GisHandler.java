package com.gentics.mesh.portal.gis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.parameter.ImageManipulationParameters;
import com.gentics.mesh.parameter.client.ImageManipulationParametersImpl;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;

import io.vertx.ext.web.RoutingContext;

/**
 * Handler for resized images in GenticsImageStore
 */
@Singleton
public class GisHandler extends AbstractGisHandler {

	/**
	 * Route Regex
	 */
	public final static String ROUTE_REGEX = "/GenticsImageStore/(?<" + WIDTH + ">[^/]+)/(?<" + HEIGHT + ">[^/]+)/(?<" + MODE + ">[^/]+)/(?<" + PATH + ">.*)";

	/**
	 * Regex pattern
	 */
	public final static Pattern PATTERN = Pattern.compile(ROUTE_REGEX);

	@Inject
	public GisHandler(MeshRestClientProvider meshClientProvider, PortalConfig config, MetricsService metricsService) {
		super(meshClientProvider, config, metricsService);
	}

	@Override
	public void handle(RoutingContext rc) {
		String clazz = rc.get(GIS_HANDLER);
		if (getClass().getName().equals(clazz)) {
			String width = rc.get(WIDTH_PARAM);
			String height = rc.get(HEIGHT_PARAM);
			String mode = rc.get(MODE_PARAM);
			String path = rc.request().path();
			ImageManipulationParameters params = new ImageManipulationParametersImpl();
			try {
				params.setWidth(Integer.parseInt(width));
			} catch (NumberFormatException e) {
			}
			try {
				params.setHeight(Integer.parseInt(height));
			} catch (NumberFormatException e) {
			}

			params.setResizeMode(mode);

			handleDownload(rc, path, params);
		} else {
			rc.next();
		}
	}

	@Override
	public void reroute(RoutingContext rc) {
		Matcher matcher = PATTERN.matcher(rc.request().path());
		if (matcher.matches()) {
			// put resizer class into rc
			rc.put(GIS_HANDLER, getClass().getName());

			// put parameters
			rc.put(WIDTH_PARAM, matcher.group(WIDTH));
			rc.put(HEIGHT_PARAM, matcher.group(HEIGHT));
			rc.put(MODE_PARAM, matcher.group(MODE));

			// reroute to path
			rc.reroute("/" + matcher.group(PATH));
		} else {
			rc.next();
		}
	}
}
