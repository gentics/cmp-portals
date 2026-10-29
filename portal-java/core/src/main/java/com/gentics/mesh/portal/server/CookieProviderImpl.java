package com.gentics.mesh.portal.server;

import com.gentics.mesh.portal.api.server.CookieProvider;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.HandlerUtils;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default implementation of {@link CookieProvider}.
 */
@Singleton
public class CookieProviderImpl implements CookieProvider {

	private static final String HOST_PREFIX = "__Host-";

	private static final Logger log = LoggerFactory.getLogger(CookieProviderImpl.class);

	private final boolean secure;
	private final boolean useHostPrefix;
	private final CookieSameSite defaultSameSitePolicy;

	@Inject
	public CookieProviderImpl(PortalConfig config) {
		boolean shouldUseHostPrefix = config.getSecurityOptions().isUseHostCookiePrefix();

		this.secure = HandlerUtils.forceSecureCookies(config);
		this.useHostPrefix = secure && shouldUseHostPrefix;
		this.defaultSameSitePolicy = config.getSecurityOptions().getCookieSameSitePolicy();

		if (shouldUseHostPrefix != useHostPrefix) {
			log.warn("Usage of host prefix requested in configuration, but disabled, because secure flag cannot be set");
		}

		log.info(
			"CookieProvider initialized: secure cookies: {}, host prefix: {}, same site default: {}",
			secure,
			useHostPrefix,
			defaultSameSitePolicy.name());
	}

	@Override
	public String realName(String name) {
		return useHostPrefix
			? HOST_PREFIX + name
			: name;
	}

	@Override
	public Cookie create(String name, String value, CookieSameSite sameSite) {
		String realName = realName(name);

		if (sameSite == null) {
			sameSite = defaultSameSitePolicy;
		}

		if (log.isTraceEnabled()) {
			log.trace(
				"Creating{} cookie (SameSite: {}): {}",
				secure ? " secure" : "",
				sameSite.name(),
				realName);
		}

		return Cookie.cookie(realName, value)
			.setSecure(secure)
			.setSameSite(sameSite)
			.setHttpOnly(true)
			.setPath("/");
	}

}
