package com.gentics.mesh.portal.config.option;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;

import javax.annotation.Nonnull;

public class SearchOptions implements Config {

	private static final String DEFAULT_ENDPOINT = "/search";
	private static final int DEFAULT_QUERY_SIZE = 8; // This is the default "size" parameter in Elasticsearch.
	private static final int DEFAULT_PAGINATION_MAX_PAGES = 6;
	private static final String DEFAULT_PREVIOUS = "<";
	private static final String DEFAULT_ELLIPSIS = "...";
	private static final String DEFAULT_NEXT = ">";
	private static final String DEFAULT_TEMPLATE = "searchresults";

	private static final String ENV_ENABLED = "GPJ_SEARCH_ENABLED";
	private static final String ENV_ENDPOINT = "GPJ_SEARCH_ENDPOINT";
	private static final String ENV_QUERY_SIZE = "GPJ_SEARCH_QUERY_SIZE";
	private static final String ENV_PAGINATION_MAX_PAGES = "GPJ_SEARCH_PAGINATION_MAX_PAGES";
	private static final String ENV_PREVIOUS = "GPJ_SEARCH_PREVIOUS";
	private static final String ENV_ELLIPSIS = "GPJ_SEARCH_ELLIPSIS";
	private static final String ENV_NEXT = "GPJ_SEARCH_NEXT";
	private static final String ENV_TEMPLATE = "GPJ_SEARCH_TEMPLATE";

	@JsonProperty(defaultValue = "false")
	@JsonPropertyDescription("Whether the search handler should be used.")
	@EnvironmentVariable(name = ENV_ENABLED, description = "Override whether the search handler should be enabled.")
	private boolean enabled = false;

	@JsonProperty(defaultValue = DEFAULT_TEMPLATE)
	@JsonPropertyDescription("The name of the handlebars template to render results. May contain directory segments, and the .hbs extension is optional.")
	@EnvironmentVariable(name = ENV_TEMPLATE, description = "Override the template used to render results.")
	private String template = DEFAULT_TEMPLATE;

	@JsonProperty(defaultValue = DEFAULT_ENDPOINT)
	@JsonPropertyDescription("The endpoint for the search handler. Custom searches can be made by appending the custom query name as a path segment.")
	@EnvironmentVariable(name = ENV_ENDPOINT, description = "Override the search endpoint.")
	private String endpoint = DEFAULT_ENDPOINT;

	@JsonProperty(defaultValue = "" + DEFAULT_QUERY_SIZE)
	@JsonPropertyDescription("The number of hits returned in a single result page. This must be the same in the configuration of the search plugin, or the results will be undefined.")
	@EnvironmentVariable(name = ENV_QUERY_SIZE, description = "Override the query size")
	private int querySize = DEFAULT_QUERY_SIZE;

	@JsonProperty(defaultValue = "" + DEFAULT_PAGINATION_MAX_PAGES)
	@JsonPropertyDescription("The maximum number of links in the pagination.")
	@EnvironmentVariable(name = ENV_PAGINATION_MAX_PAGES, description = "Override the maximum number of links in the pagination")
	private int paginationMaxPages = DEFAULT_PAGINATION_MAX_PAGES;

	@JsonProperty(defaultValue = DEFAULT_PREVIOUS)
	@JsonPropertyDescription("The string to use for the \"previous\" link in the pagination.")
	@EnvironmentVariable(name = ENV_PREVIOUS, description = "Override the string for the \"previous\" pagination link.")
	private String paginationPrevious = DEFAULT_PREVIOUS;

	@JsonProperty(defaultValue = DEFAULT_ELLIPSIS)
	@JsonPropertyDescription("The string to use for the ellipsis when there are more pages as can be displayed in the pagination.")
	@EnvironmentVariable(name = ENV_ELLIPSIS, description = "Override the string to use for the pagination ellipsis.")
	private String paginationEllipsis = DEFAULT_ELLIPSIS;

	@JsonProperty(defaultValue = DEFAULT_NEXT)
	@JsonPropertyDescription("The string to use for the \"next\" link in the pagination.")
	@EnvironmentVariable(name = ENV_NEXT, description = "Override the string for the \"next\" pagination link.")
	private String paginationNext = DEFAULT_NEXT;

	public boolean isEnabled() {
		return enabled;
	}

	public SearchOptions setEnabled(boolean enabled) {
		this.enabled = enabled;

		return this;
	}

	@Nonnull
	public String getTemplate() {
		if (template == null) {
			template = DEFAULT_TEMPLATE;
		}

		return template;
	}

	public SearchOptions setTemplate(String template) {
		this.template = template;

		return this;
	}

	@Nonnull
	public String getEndpoint() {
		if (endpoint == null) {
			endpoint = DEFAULT_ENDPOINT;
		}

		return endpoint;
	}

	public SearchOptions setEndpoint(String endpoint) {
		this.endpoint = endpoint;

		return this;
	}

	public int getQuerySize() {
		return querySize;
	}

	public SearchOptions setQuerySize(int querySize) {
		this.querySize = querySize;

		return this;
	}

	public int getPaginationMaxPages() {
		return paginationMaxPages;
	}

	public SearchOptions setPaginationMaxPages(int paginationMaxPages) {
		this.paginationMaxPages = paginationMaxPages;

		return this;
	}

	@Nonnull
	public String getPaginationPrevious() {
		if (paginationPrevious == null) {
			paginationPrevious = DEFAULT_PREVIOUS;
		}

		return paginationPrevious;
	}

	public SearchOptions setPaginationPrevious(String paginationPrevious) {
		this.paginationPrevious = paginationPrevious;

		return this;
	}

	@Nonnull
	public String getPaginationEllipsis() {
		if (paginationEllipsis == null) {
			paginationEllipsis = DEFAULT_ELLIPSIS;
		}

		return paginationEllipsis;
	}

	public SearchOptions setPaginationEllipsis(String paginationEllipsis) {
		this.paginationEllipsis = paginationEllipsis;

		return this;
	}

	@Nonnull
	public String getPaginationNext() {
		if (paginationNext == null) {
			paginationNext = DEFAULT_NEXT;
		}

		return paginationNext;
	}

	public SearchOptions setPaginationNext(String paginationNext) {
		this.paginationNext = paginationNext;

		return this;
	}
}
