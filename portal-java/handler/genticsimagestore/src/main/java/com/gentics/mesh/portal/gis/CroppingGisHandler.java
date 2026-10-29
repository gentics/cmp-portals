package com.gentics.mesh.portal.gis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.parameter.ImageManipulationParameters;
import com.gentics.mesh.parameter.client.ImageManipulationParametersImpl;
import com.gentics.mesh.parameter.image.CropMode;
import com.gentics.mesh.parameter.image.ImageRect;
import com.gentics.mesh.portal.api.monitoring.MetricsService;
import com.gentics.mesh.portal.client.MeshRestClientProvider;
import com.gentics.mesh.portal.config.PortalConfig;

import io.vertx.ext.web.RoutingContext;

/**
 * Handler for resized and cropped images in the GenticsImageStore
 */
@Singleton
public class CroppingGisHandler extends AbstractGisHandler {
	protected final static String STARTX = "startx";

	protected final static String STARTY = "starty";

	protected final static String CROPWIDTH = "cropwidth";

	protected final static String CROPHEIGHT = "cropheight";

	protected final static String STARTX_PARAM = GIS_HANDLER + "." + STARTX;

	protected final static String STARTY_PARAM = GIS_HANDLER + "." + STARTY;

	protected final static String CROPWIDTH_PARAM = GIS_HANDLER + "." + CROPWIDTH;

	protected final static String CROPHEIGHT_PARAM = GIS_HANDLER + "." + CROPHEIGHT;

	/**
	 * Route Regex
	 */
	public final static String ROUTE_REGEX = "/GenticsImageStore/(?<" + WIDTH + ">[^/]+)/(?<" + HEIGHT + ">[^/]+)/cropandresize/(?<" + MODE + ">[^/]+)/(?<"
			+ STARTX + ">[^/]+)/(?<" + STARTY + ">[^/]+)/(?<" + CROPWIDTH + ">[^/]+)/(?<" + CROPHEIGHT + ">[^/]+)/(?<" + PATH + ">.*)";

	/**
	 * Regex pattern
	 */
	protected final static Pattern PATTERN = Pattern.compile(ROUTE_REGEX);

	@Inject
	public CroppingGisHandler(MeshRestClientProvider meshClientProvider, PortalConfig config, MetricsService metricsService) {
		super(meshClientProvider, config, metricsService);
	}

	@Override
	public void handle(RoutingContext rc) {
		String clazz = rc.get(GIS_HANDLER);
		if (getClass().getName().equals(clazz)) {
			String width = rc.get(WIDTH_PARAM);
			String height = rc.get(HEIGHT_PARAM);
			String mode = rc.get(MODE_PARAM);
			String startx = rc.get(STARTX_PARAM);
			String starty = rc.get(STARTY_PARAM);
			String cropwidth = rc.get(CROPWIDTH_PARAM);
			String cropheight = rc.get(CROPHEIGHT_PARAM);
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

			try {
				if (startx != null && starty != null && cropwidth != null && cropheight != null) {
					ImageRect rect = new ImageRect(Integer.parseInt(startx), Integer.parseInt(starty), Integer.parseInt(cropheight), Integer.parseInt(
						cropwidth));
					params.setRect(rect);
					params.setCropMode(CropMode.RECT);
				}
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
			rc.put(STARTX_PARAM, matcher.group(STARTX));
			rc.put(STARTY_PARAM, matcher.group(STARTY));
			rc.put(CROPWIDTH_PARAM, matcher.group(CROPWIDTH));
			rc.put(CROPHEIGHT_PARAM, matcher.group(CROPHEIGHT));

			// reroute to path
			rc.reroute("/" + matcher.group(PATH));
		} else {
			rc.next();
		}
	}
}
