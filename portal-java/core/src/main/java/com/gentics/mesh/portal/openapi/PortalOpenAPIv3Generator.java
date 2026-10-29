package com.gentics.mesh.portal.openapi;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

import com.gentics.mesh.portal.handler.SecurityHandler;
import com.gentics.vertx.openapi.OpenAPIv3Generator;
import com.gentics.vertx.openapi.model.ExtendedSecurityScheme;

import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Implementation for the Portal | java OpenAPIGenerator.
 */
public class PortalOpenAPIv3Generator extends OpenAPIv3Generator {

	public PortalOpenAPIv3Generator(String version, URL serverUrl) {
		this(version, Collections.singletonList(serverUrl.toString()), Optional.empty(), Optional.empty());
	}

	public PortalOpenAPIv3Generator(String version, List<String> servers, @NonNull Optional<? extends Collection<Pattern>> maybePathBlacklist, @NonNull Optional<? extends Collection<Pattern>> maybePathWhitelist) {
		super(version, servers, Collections.singletonMap("apiKey", new ExtendedSecurityScheme(true)), maybePathBlacklist, maybePathWhitelist);

		var securityScheme = security.get("apiKey").getScheme();
		securityScheme.setScheme("bearer");
		securityScheme.setType(SecurityScheme.Type.APIKEY);
		securityScheme.setName(SecurityHandler.HEADER_API_KEY);
		securityScheme.setIn(SecurityScheme.In.HEADER);
	}
}
