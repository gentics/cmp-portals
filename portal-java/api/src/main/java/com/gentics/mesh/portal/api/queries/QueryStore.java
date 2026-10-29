package com.gentics.mesh.portal.api.queries;

/**
 * Interface for the class generating GraphQL queries.
 */
public interface QueryStore {
	/**
	 * Get the query to load a node via its path.
	 * @return Query to load  a node via its path.
	 */
	String getByPathQuery();

	/**
	 * Get the query to load the navigation.
	 * @return The query to load the navigation.
	 */
	String getNavigationQuery();

	/**
	 * Get the query to load available languages for a certain path.
	 * @return The query to load available languages for a certain path.
	 */
	String getLanguagesQuery();

	/**
	 * Get the query to load the top navigation.
	 * @return The query to load the top navigation.
	 */
	String getTopNavQuery();

	/**
	 * Get the query to load the sitemap information.
	 * @return The query to load the sitemap information.
	 */
	String getSitemapQuery();

	/**
	 * Clear the query cache.
	 * @return Fluent API.
	 */
	QueryStore clearCache();

	/**
	 * Disable the query cache.
	 * @return Fluent API.
	 */
	QueryStore disableCache();
}
