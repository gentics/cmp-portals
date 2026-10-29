package com.gentics.mesh.portal.content;

import com.gentics.mesh.portal.api.Constants;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.api.content.LanguageSource;
import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.content.ContentPathHandler.RenderMode;
import com.gentics.mesh.portal.context.PortalRoutingContext;
import com.gentics.mesh.portal.context.RedirectMode;
import com.gentics.mesh.portal.handler.AbstractBaseHandler;
import com.gentics.mesh.portal.util.HandlerUtils;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

/**
 * Determines the language for the current request and sets a language cookie accordingly.
 *
 * <p>
 *     The priority for selecting languages is defined by {@link LanguageSource}.
 * </p>
 *
 * <p>
 *     This handler also registers the endpoint <code>/api/language</code> which accepts a query parameter
 *     <code>lang</code> to explicitly set the language cookie to the specified value.
 * </p>
 */
@Singleton
public class ContentLanguageHandler extends AbstractBaseHandler {

	/** The name for the query parameter to set the language. */
	private static final String PARAM_LANG = "lang";
	/** The name for the redirect query parameter. */
	private static final String PARAM_REDIRECT = "redirect";
	/** The name of the JSON field containing the language of a loaded node. */
	private static final String FIELD_LANG = "language";

	private static final Logger log = LoggerFactory.getLogger(ContentLanguageHandler.class);

	private final CookieProvider cookieProvider;

	/**
	 * Default constructor
	 */
	@Inject
	public ContentLanguageHandler(CookieProvider cookieProvider) {
		this.cookieProvider = cookieProvider;
	}

	@Override
	public void addRoutes(Router router) {
		InternalEndpointBuilder.wrap(router)
			.withDisplayName("Language switch")
			.withDescription("Switch the language served by the portal")
			.withPath("/api/language")
			.withQueryParameter(PARAM_LANG, "The language to switch to", "en")
			.withQueryParameter(PARAM_REDIRECT, "Path to redirect to after language switch", "/en/welcome")
			.withExampleResponse(HttpResponseStatus.BAD_REQUEST, "Query parameters \"lang\" and \"redirect\" are missing or cannot be encoded")
			.withExampleResponse(HttpResponseStatus.OK, "Language was successfully switched (no redirect)")
			.withExampleResponse(HttpResponseStatus.SEE_OTHER, "Language was successfully switched (redirect to specified path or referer)")
			.withMethod(HttpMethod.GET)
			.withOrder(RouteOrder.API_ROUTES.getOrder())
			.withHandler(this::switchLanguage)
			.build();

		router.route()
			// Get the language as early as possible from the HTTP headers.
			.order(RouteOrder.BODY_HANDLER.getOrder())
			.handler(this);

		router.route()
			.order(RouteOrder.AFTER_CONTENT_ROUTES.getOrder())
			.handler(this::handlePageLanguage);

		router.route()
			.order(RouteOrder.AFTER_CONTENT_ROUTES.getOrder())
			.handler(rc -> wrap(rc).setLanguageCookie(cookieProvider).next());
	}

	/**
	 * Set the language from the cookie or HTTP headers if available.
	 *
	 * @param rc The current routing context
	 */
	@Override
	public void handle(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		Cookie langCookie = cookieProvider.get(prc, Constants.COOKIE_LANG);

		if (langCookie != null) {
			prc.setLanguage(langCookie.getValue(), LanguageSource.COOKIE);
		} else {
			prc.setLanguage(getLanguageFromHeader(rc), LanguageSource.HEADER);
		}

		rc.next();
	}

	/**
	 * Set the language from the currently loaded page if not already set by cookie.
	 *
	 * @param rc The current routing context
	 */
	public void handlePageLanguage(RoutingContext rc) {
		PortalRoutingContext prc = wrap(rc);
		JsonObject page = rc.get(Constants.RC_NODE_TO_RENDER);

		if (rc.get(Constants.RC_RENDER_MODE) == RenderMode.Page && page != null && page.containsKey(FIELD_LANG)) {
			prc.setLanguage(page.getString(FIELD_LANG), LanguageSource.PAGE);
		} else {
			prc.setLanguage(getLanguageFromHeader(rc), LanguageSource.HEADER);
		}

		rc.next();
	}

	/**
	 * Sets or deletes the language cookie and redirects the specified URL or the referrer.
	 *
	 * <p>
	 *     Either the <code>lang</code> or the <code>redirect</code> parameters must be specified,
	 *     or the result will be a <code>Bad Request</code> response.
	 * </p>
	 *
	 * <p>
	 *     When no <code>lang</code> parameter is specified, the language cookie will be deleted
	 *     (and the redirect will cause a new language cookie to be set).
	 * </p>
	 *
	 * <p>
	 *     When no <code>redirect</code> parameter is specified, the response will redirect to
	 *     the <code>Referer</code>. If that also cannot be determined, the response will be an
	 *     empty {@link HttpResponseStatus#OK 200 OK}.
	 * </p>
	 *
	 * @param rc The current routing context
	 */
	public void switchLanguage(RoutingContext rc) {
		HttpServerRequest request = rc.request();
		String lang = request.getParam(PARAM_LANG);
		String redirect = request.getParam(PARAM_REDIRECT);

		if (StringUtils.isBlank(lang) && StringUtils.isBlank(redirect)) {
			log.warn("Cannot switch language: either parameter \"{}\" or \"{}\" must be present", PARAM_LANG, PARAM_REDIRECT);

			rc.response()
				.setStatusMessage(String.format("Missing parameter \"%s\" or \"%s\"", PARAM_LANG, PARAM_REDIRECT))
				.setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
				.end();

			return;
		}

		// escape get param values before saving to cookie and redirecting
		try {
			lang = !StringUtils.isBlank(lang) ? java.net.URLEncoder.encode(lang, "utf8") : null;
		} catch (UnsupportedEncodingException e) {
			String msg = String.format("Unable to encode/escape param \"%s\": %s", PARAM_LANG, e.getMessage());

			log.error(msg, e);

			rc.response()
				.setStatusMessage(msg)
				.setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
				.end();

			return;
		}

		log.debug("Setting portal language to {}", lang);
		rc.response().addCookie(getLanguageSwitchCookie(lang));

		if (StringUtils.isBlank(redirect)) {
			redirect = request.getHeader(HttpHeaders.REFERER);
		}

		if (redirect == null) {
			rc.response().setStatusCode(HttpResponseStatus.OK.code()).end();
		} else {
			wrap(rc).redirect(redirect, RedirectMode.INTERNAL);
		}
	}

	/**
	 * Get the preferred language from the <code>Accept-Language</code> HTTP header.
	 *
	 * @see HandlerUtils#preferredValue(String)
	 * @param rc The current routing context
	 * @return The preferred language set in the HTTP headers, or <code>null</code> if the header is not set
	 */
	private String getLanguageFromHeader(RoutingContext rc) {
		String lang = HandlerUtils.preferredValue(rc.request().getHeader(HttpHeaders.ACCEPT_LANGUAGE));

		if (lang != null) {
			int idx = lang.indexOf('-');

			if (idx > 1) {
				// Remove additional information after the language code (like locale or encoding information).
				lang = lang.substring(0, idx);
			}
		}

		return lang;
	}

	/**
	 * Create a cookie which will set the language to the specified one, or deletes the cookie
	 * if <code>lang</code> is empty.
	 *
	 * @param lang The desired language or null when the cookie should be deleted
	 * @return A <code>Cookie</code> that will set the language cookie to the specified value, or delete it
	 */
	private Cookie getLanguageSwitchCookie(String lang) {
		if (StringUtils.isBlank(lang)) {
			// Deleting the cookie and redirecting to another page will set the
			// language cookie for the new page.
			log.debug("Deleting language cookie");

			return cookieProvider.delete(Constants.COOKIE_LANG);
		}

		log.debug("Setting language cookie: {}", lang);

		return cookieProvider.create(Constants.COOKIE_LANG, lang);
	}

}
