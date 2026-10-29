package com.gentics.mesh.portal.applications;

import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import static com.gentics.mesh.portal.context.PortalRoutingContext.wrap;

public class EchoLanguageApp implements PortalApplication {

	private static final Logger log = LoggerFactory.getLogger(EchoLanguageApp.class);

	@Override
	public PortalApplication init() throws InvalidConfigurationException {
		return this;
	}

	@Override
	public String getApplicationKey() {
		return "echo-language";
	}

	@Override
	public void handle(RoutingContext rc) {
		String lang = wrap(rc).getLanguage();

		log.debug("Sending current portal language: {}", lang);

		rc.response()
			.setStatusCode(HttpResponseStatus.OK.code())
			.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
			.end(lang);
	}
}
