package com.gentics.mesh.portal.server.core;

import com.gentics.mesh.core.rest.graphql.ErrorLocation;
import com.gentics.mesh.core.rest.graphql.GraphQLError;
import com.gentics.mesh.core.rest.graphql.GraphQLResponse;
import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.config.RequiredQuery;
import com.gentics.mesh.portal.server.dagger.BaseComponent;
import com.gentics.mesh.rest.client.MeshRestClient;
import io.reactivex.Completable;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checker of the {@link QueryStore} functioning.
 * Currently checks whether the mandatory queries exist and are syntactically correct.
 *
 * @author plyhun
 *
 */
public class QueryStoreChecker implements PortalFunctionStartupChecker {

	protected static final Logger log = LoggerFactory.getLogger(QueryStoreChecker.class);

	private final MeshRestClient meshClient;
	private final String project;
	private final Map<String, String> requiredQueries;

	public QueryStoreChecker(BaseComponent base) {
		this.meshClient = base.meshRestClient();
		this.project = base.portalConfig().getMeshProject();
		this.requiredQueries = new HashMap<>();

		QueryStore queryStore = base.queryStore();
		List<RequiredQuery> checkedQueries = base.portalConfig().getCheckedQueries();

		if (checkedQueries.isEmpty()) {
			checkedQueries = Arrays.asList(RequiredQuery.values());
		}

		for (RequiredQuery query : checkedQueries) {
			requiredQueries.put(query.queryName(), query.query(queryStore));
		}
	}

	/**
	 * Check whether the required queries exist and are syntactically correct.
	 *
	 * @see #requiredQueries for the list of required queries.
	 *
	 * @return {@code true} when the check is successful and {@code false}
	 * 		otherwise
	 */
	@Override
	public boolean check() {
		return internalCheck(false)
			.all(Boolean::booleanValue)
			.blockingGet();
	}

	@Override
	public Completable checkAsync() {
		return internalCheck(true)
			.ignoreElements();
	}

	private Observable<Boolean> internalCheck(boolean throwOnError) {
		return Observable.fromIterable(requiredQueries.entrySet())
			.doOnSubscribe(ignored -> log.info("Checking required queries"))
			.flatMap(query -> meshClient.graphqlQuery(project, query.getValue()).toObservable()
				.map(rs -> queryWorks(rs, query.getKey(), throwOnError)));
	}

	/**
	 * Checks if the query works by checking the provided response.
	 *
	 * @param response The GraphQL response from Mesh for the query
	 * @param queryName The name of the tested query
	 * @return {@code true} if the response does not contain any errors, and
	 * 		{@code false otherwise}
	 */
	private boolean queryWorks(GraphQLResponse response, String queryName, boolean throwOnError) throws PortalStartupException {
		List<GraphQLError> errors = response.getErrors();

		if (errors != null && !errors.isEmpty()) {
			for (GraphQLError error: errors) {
				if ("InvalidSyntax".equals(error.getType()) || "ValidationError".equals(error.getType())) {
					String errorMsg = getGraphQlError(error, queryName);

					if (throwOnError) {
						throw new PortalStartupException(errorMsg);
					} else {
						log.error(errorMsg);
					}
				}
			}

			return false;
		}

		return true;
	}

	/**
	 * Log the given GraphQL error as an error message.
	 *
	 * @param error The GraphQL error to be logged
	 * @param queryName The name of the erroneous GraphQL query
	 */
	private String getGraphQlError(GraphQLError error, String queryName) {
		List<ErrorLocation> locations = error.getLocations();

		if (locations != null && !locations.isEmpty()) {
			String errorLocations = locations.stream()
				.map(loc -> String.format("%d:%d", loc.getLine(), loc.getColumn()))
				.collect(Collectors.joining(", "));

			return String.format("%s on query '%s' (%s): %s",
				error.getType(),
				queryName,
				errorLocations,
				error.getMessage());
		} else {
			return String.format("%s on query '%s': %s",
				error.getType(),
				queryName,
				error.getMessage());
		}
	}
}
