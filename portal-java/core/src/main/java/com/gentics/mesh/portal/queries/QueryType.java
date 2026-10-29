package com.gentics.mesh.portal.queries;

/**
 * The query types supported by the {@link QueryStore}.
 */
public enum QueryType {
	GraphQL(".graphql"),
	ElasticSearch(".es");

	private final String extension;

	/**
	 * Default constructor.
	 *
	 * @param extension The filename extension for the query type
	 */
	private QueryType(String extension) {
		this.extension = extension;
	}

	/**
	 * Get the filename for a query of this type.
	 *
	 * @param queryName The name of the query file without extension
	 * @return The filename extension for the query type
	 */
	public String filename(String queryName) {
		return queryName + extension;
	}
}
