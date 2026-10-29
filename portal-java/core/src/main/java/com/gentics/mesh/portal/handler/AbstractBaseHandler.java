package com.gentics.mesh.portal.handler;

import com.gentics.mesh.core.rest.graphql.GraphQLError;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.portal.api.handler.PortalHandler;
import com.gentics.mesh.portal.util.MapUtils;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractBaseHandler implements PortalHandler {

	private static final Logger log = LoggerFactory.getLogger(AbstractBaseHandler.class);

	/**
	 * Maps messages to log functions. This allows finer log levels for known errors.
	 */
	private static Map<String, Consumer<Object>> graphQlErrorLogLevels = MapUtils.mapOf(
		"graphql_error_missing_perm", msg -> log.debug(msg == null ? "(null)" : msg.toString())
	);

	/**
	 * Logger for graphql errors.
	 */
	protected Function<GraphQLResponse, GraphQLResponse> errorLogger = resp -> {
		if (resp != null && resp.getErrors() != null && !resp.getErrors().isEmpty()) {
			for (GraphQLError error : resp.getErrors()) {
				Optional.ofNullable(error)
					.map(GraphQLError::getMessage)
					.map(graphQlErrorLogLevels::get)
					.orElse(msg -> log.warn(msg == null ? "(null)" : msg.toString()))
					.accept("Encountered error: " + JsonUtil.toJson(error));
			}
		}
		return resp;
	};
}
