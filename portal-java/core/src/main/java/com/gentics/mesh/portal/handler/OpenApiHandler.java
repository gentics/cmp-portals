package com.gentics.mesh.portal.handler;

import com.gentics.mesh.portal.api.PortalVersion;
import com.gentics.mesh.portal.api.RouteOrder;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.openapi.OpenApiVersion;
import com.gentics.mesh.portal.openapi.PortalOpenAPIv3Generator;
import com.gentics.vertx.openapi.model.Format;
import com.gentics.vertx.openapi.model.OpenAPIGenerationException;
import com.gentics.vertx.openapi.route.InternalEndpointBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.inject.Inject;

import org.raml.model.parameter.QueryParameter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handler for OpenAPI requests.
 */
public class OpenApiHandler extends AbstractBaseHandler {

	public static final String RC_FORMAT = "gpj.openapi.format";

	private static final String PARAM_VERSION = "version";
	private static final OpenApiVersion DEFAULT_VERSION = OpenApiVersion.V30;

	private final SecurityHandler securityHandler;
	private final PortalConfig config;
	private final PortalOpenAPIv3Generator generator;
	private final Map<Router, String> routerRepo = new HashMap<>();

	@Inject
	public OpenApiHandler(SecurityHandler securityHandler, PortalConfig config) {
		this.securityHandler = securityHandler;
		this.config = config;
		this.generator = new PortalOpenAPIv3Generator(PortalVersion.plainVersion(), config.getServerUrl());
	}

	@Override
	public void addRoutes(Router router) {
		routerRepo.put(router, "");

		QueryParameter versionParameter = new QueryParameter();
		versionParameter.setDescription("Specification version");
		versionParameter.setEnumeration(Arrays.stream(OpenApiVersion.values()).map(v -> v.toString().toLowerCase()).collect(Collectors.toList()));
		versionParameter.setDefaultValue(OpenApiVersion.V30.toString().toLowerCase());
		versionParameter.setRequired(false);

		for (var format: Format.values()) {
			InternalEndpointBuilder.wrap(router)
				.withDisplayName("OpenAPI specification")
				.withDescription("Returns the OpenAPI specification")
				.withPath("/api/openapi.%s".formatted(format.name().toLowerCase()))
				.withQueryParameter(PARAM_VERSION, versionParameter)
				.withOrder(RouteOrder.API_ROUTES.getOrder())
				//.withHandler(securityHandler)
				.withHandler(rc -> rc.put(RC_FORMAT, format).next())
				.withHandler(this)
				.insecure(true)
				.produces(format == Format.YAML ? "application/x-yaml" : "application/json")
				.withExampleResponse(HttpResponseStatus.OK, format.name() + " content")
				.build();
		}
	}

	@Override
	public void handle(RoutingContext rc) {
		if (!config.getDataProviderOptions().isServeOpenApi()) {
			rc.response().setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end();
			return;
		}

		if (routerRepo.isEmpty()) {
			rc.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
			return;
		}

		Format format = rc.get(RC_FORMAT);
		var version = OpenApiVersion.parse(rc.queryParam(PARAM_VERSION).stream().findAny().orElse(DEFAULT_VERSION.name()), DEFAULT_VERSION);

		try {
			var result = generator.generate("Gentics Portal|Java", routerRepo, format, false, version == OpenApiVersion.V31, Optional.empty(), Optional.empty());

			rc.response().setStatusCode(HttpResponseStatus.OK.code()).end(result);
		} catch (OpenAPIGenerationException e) {
			rc.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("Could not generate OpenAPI document: " + e.getMessage());
		}
	}
}
