package com.gentics.mesh.portal.config;

import com.gentics.mesh.portal.api.queries.QueryStore;

import java.util.function.Function;

/**
 * Queries that are required for portal start-up.
 */
public enum RequiredQuery {
	/** Query to load available languages for a node. */
	LANGUAGES("loadLanguages", QueryStore::getLanguagesQuery),
	/** Query to load a node by path. */
	LOAD_BY_PATH("loadByPath", QueryStore::getByPathQuery),
	/** Query to load the navigation. */
	NAVIGATION("loadNavigation", QueryStore::getNavigationQuery),
	/** Query to load the sitemap information. */
	SITEMAP("loadAllForSitemap", QueryStore::getSitemapQuery);

	private final String queryName;
	private final Function<QueryStore, String> querySupplier;

	RequiredQuery(String queryName, Function<QueryStore, String> querySupplier) {
		this.queryName = queryName;
		this.querySupplier = querySupplier;
	}

	/**
	 * Get the query name.
	 * @return The query name.
	 */
	public String queryName() {
		return queryName;
	}

	/**
	 * Get the query.
	 * @param queryStore The query store to load the query from.
	 * @return The query.
	 */
	public String query(QueryStore queryStore) {
		return querySupplier.apply(queryStore);
	}
}
