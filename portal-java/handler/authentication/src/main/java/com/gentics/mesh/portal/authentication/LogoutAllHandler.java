package com.gentics.mesh.portal.authentication;

import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.AuthenticationOptions;
import com.gentics.mesh.portal.context.RedirectMode;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.util.AuthUtils;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.vertx.core.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Handler that logs a user out from all the Portals defined in the portal configuration.
 *
 * <p>
 *     All portals listed in {@code authentication.singleSignOut.externalPortals}
 *     must define the same {@code authentication.signleSignOut.singleSignOutPath},
 *     and all configurations must contain the same portals.
 * </p>
 */
@Singleton
public class LogoutAllHandler extends AbstractBaseHandler {

	private static final Logger log = LoggerFactory.getLogger(LogoutAllHandler.class);

	private final AuthenticationOptions authOptions;
	private final CookieProvider cookieProvider;
	private final List<String> allPortals;
	private final int serverIdx;

	@Inject
	public LogoutAllHandler(PortalConfig portalConfig, CookieProvider cookieProvider) {
		this.authOptions = portalConfig.getAuthOptions();
		this.cookieProvider = cookieProvider;
		this.allPortals = new ArrayList<>(authOptions.getSingleSignOutOptions().getExternalPortals());
		this.allPortals.sort(Comparator.naturalOrder());
		this.serverIdx = allPortals.indexOf(portalConfig.getServerUrl().toString());

		if (serverIdx < 0) {
			log.error("The server URL is not defined in the portal list, single sign out will be disabled");
		}
	}

	/**
	 * Start or continue the logout cycle.
	 *
	 * <p>
	 *     When no {@code start} query parameter is present, this is the firest
	 *     request and will start the cycle by redirecting to the next portals
	 *     single sign out endpoint with the {@code start} parameter set
	 *     accordingly.
	 * </p>
	 *
	 * <p>
	 *     When the start parameter is set, but not the same as the index of
	 *     the current portal, the portal will just delete its authentication
	 *     cookies and redirect to the next portal.
	 * </p>
	 *
	 * <p>
	 *     When the start parameter is set, and it is the same as the index of
	 *     the current portal, this is the last request in the cycle and the
	 *     portal will redirect to the regular logout endpoint.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		String startParam = rc.request().getParam("start");
		int startIdx = getStartIndex(startParam);

		if (startIdx < 0) {
			rc.response()
				.setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
				.end();

			return;
		}

		String nextPortalRedirect = getNextPortalRedirect(startIdx);

		if (startParam == null) {
			// We are the first portal to receive the request, so we just start
			// the cycle by redirecting to the next portal.
			wrap(rc).redirect(nextPortalRedirect, RedirectMode.TRUSTED);
		} else if (startIdx == serverIdx) {
			// We are back at the start. All other portals have deleted their
			// authentication cookies. Redirect to the logout endpoint as the
			// final step.
			wrap(rc).redirect(authOptions.getLogoutEndpoint(), RedirectMode.TRUSTED);
		} else {
			// We are in the middle of the cycle. Delete the authentication
			// cookies and redirect to the next portal.
			AuthUtils.deleteAuthCookies(cookieProvider, rc);
			wrap(rc).redirect(nextPortalRedirect, RedirectMode.TRUSTED);
		}
	}

	@Override
	public void addRoutes(Router router) {
		var route = InternalEndpointBuilder.wrap(router)
			.withDisplayName("Single sign out")
			.withDescription("Signs out of all configured portal instances")
			.withPath(authOptions.getSingleSignOutOptions().getSingleSignOutPath())
			.withMethod(HttpMethod.GET)
			.withOrder(RouteOrder.BEFORE_API_ROUTES.getOrder())
			.withQueryParameter("start", "Index of the portal that started the single sign out", "0")
			.withExampleResponse(HttpResponseStatus.BAD_REQUEST, "Invalid start index")
			.withExampleResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Invalid portal instance configuration")
			.withExampleResponse(HttpResponseStatus.SEE_OTHER, "Successfully logged out and redirected to the next portal");

		if (serverIdx >= 0 && allPortals != null && !allPortals.isEmpty()) {
			route.withHandler(this);
		} else {
			route.withHandler(rc -> rc.response().setStatusCode(RtspResponseStatuses.INTERNAL_SERVER_ERROR.code()).end());
		}

		route.build();
	}

	/**
	 * Parse the start parameter.
	 *
	 * <p>
	 *     When the parameter is {@code null} this portal is the first to
	 *     receive the request, so the returned start index will be
	 *     {@link #serverIdx}.
	 * </p>
	 *
	 * @param startParam The query parameter {@code start}
	 * @return In case of an error -1, when {@code startParam} is {@code null}
	 * 		the {@link #serverIdx}, and otherwise the parsed integer value
	 */
	private int getStartIndex(String startParam) {
		if (startParam == null) {
			return serverIdx;
		}

		int startIdx;

		try {
			startIdx = Integer.parseInt(startParam);
		} catch (Exception e) {
			startIdx = -1;
			log.error("Could not parse start index \"{}\": {}", startParam, e.getMessage());
		}

		if (startIdx < 0 || startIdx >= allPortals.size()) {
			log.error("Invalid start index {} (number of portals: {})", startIdx, allPortals.size());

			return -1;
		}

		return startIdx;
	}

	/**
	 * Create the redirect URL to the next portal in the list.
	 *
	 * @param start The start index
	 * @return The redirect URL to the next portal in the list
	 */
	private String getNextPortalRedirect(int start) {
		String nextPortal = allPortals.get((serverIdx + 1) % allPortals.size());

		if (nextPortal.endsWith("/")) {
			nextPortal = nextPortal.substring(0, nextPortal.length() - 1);
		}

		return nextPortal + authOptions.getSingleSignOutOptions().getSingleSignOutPath() + "?start=" + start;
	}
}
