package com.gentics.mesh.portal.search;

import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.config.option.SearchOptions;
import com.gentics.mesh.portal.util.RenderContextUtils;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.helper.EachHelper;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Handlebars helpers for rendering search results.
 */
@Singleton
public class SearchResultHelper {

	private static final Logger log = LoggerFactory.getLogger(SearchResultHelper.class);

	private final SearchOptions config;

	@Inject
	public SearchResultHelper(PortalConfig config) {
		this.config = config.getSearchOptions();
	}

	/**
	 * The value of the field {@code totalHits} from the search results.
	 *
	 * @param options The helper options
	 * @return The value of the field {@code totalHits} from the search results
	 */
	public static int getTotalHits(Options options) {
		return getSearchResponse(options).getInteger("totalHits", 0);
	}

	/**
	 * The endpoint for which the {@link SearchHandler} is registered.
	 *
	 * <p>
	 *     This yields just the default search endpoint. Custom searches must
	 *     add the name of the plugins custom query (e.g.
	 *     <code>&lt;form action=/{{searchActionUrl}}/contactSearch&gt;</code>).
	 * </p>
	 *
	 * @return The endpoint for the search handler
	 */
	public Object searchActionUrl() {
		return config.getEndpoint();
	}

	/**
	 * Checks if a search query was specified (parameter {@code q}).
	 *
	 * @param options The helper options
	 * @return The rendered body if there is a search query present, and the
	 * 		rendered <code>{{else}}</code> part otherwise
	 * @throws IOException When {@link Options#fn()} or {@link Options#inverse()}
	 * 		throws an error
	 */
	public Object ifSearchQuery(Options options) throws IOException {
		MultiMap params = RenderContextUtils.getValue(options.context, SearchHandler.RC_QUERY_PARAMS);
		Object query = params != null ? params.get("q") : null;

		if (query != null && StringUtils.isNotBlank((String) query)) {
			return options.fn();
		}

		return options.inverse();
	}

	/**
	 * Iterate over the search result items.
	 *
	 * <p>
	 *     Elements where the {@code "raw.fields"} field is missing or empty
	 *     will be skipped.
	 * </p>
	 *
	 * @param options The helper options
	 * @return The result of the {@link EachHelper} applied to the search
	 * 		result items
	 * @throws IOException When the {@code EachHelper} cannot be applied
	 */
	public Object eachSearchResult(Options options) throws IOException {
		List<JsonObject> results = getSearchResults(options).stream()
			.filter(o -> o instanceof JsonObject)
			.map(o -> (JsonObject) o)
			.filter(o -> !o.getJsonObject("raw", new JsonObject()).getJsonObject("fields", new JsonObject()).isEmpty())
			.collect(Collectors.toList());

		return new EachHelper().apply(results, options);
	}

	/**
	 * Checks if pagination is needed.
	 *
	 * @param options The helper options
	 * @return The rendered contents of the block if pagination is needed, and
	 * 		the rendered contents of the <code>{{else}}</code> block otherwise
	 * @throws IOException When rendering of the block fails
	 */
	public Object ifNeedPagination(Options options) throws IOException {
		JsonObject response = getSearchResponse(options);

		if (response == null) {
			log.warn("Helper <ifNeedPagination> has no search results available, this might be a problem in the Handlebars template");

			return "";
		}

		int hits = response.getJsonArray("results", new JsonArray()).size();
		int totalHits = response.getInteger("totalHits", 0);

		return totalHits > hits && hits > 0 ? options.fn() : options.inverse();
	}

	/**
	 * Render the pagination entries.
	 *
	 * <p>
	 *     Each entry is rendered using the markup in the block if it is
	 *     <em>not</em> the current page and should therefore be an active link,
	 *     and with the markup from the <code>{{else}}</code> block otherwise.
	 * </p>
	 *
	 * <p>
	 *     The previous (default "{@code &lt;}") and next
	 *     (default "{@code &gt;}") entries are always rendered, the number of
	 *     entries in between can be capped with the {@code maxPages} parameter
	 *     of the helper. Superfluous pages will be removed from the middle and
	 *     replaced with an ellipsis (if more than one entry is skipped).
	 * </p>
	 *
	 * @see #processPaginationEntry(StringBuilder, String, Options, JsonObject, int, int)
	 *
	 * @param options The helper options
	 * @return The rendered pagination entries
	 * @throws IOException When the block contents cannot be rendered
	 */
	public Object eachPaginationEntry(Options options) throws IOException {
		JsonObject params = RenderContextUtils.getValue(options.context, SearchHandler.RC_QUERY_PARAMS, JsonObject::new);
		JsonObject response = getSearchResponse(options);
		int totalHits = response.getInteger("totalHits", 0);
		int maxActivePages = options.hash("maxPages", config.getPaginationMaxPages());
		int activePage = -1;

		if (params.containsKey("p")) {
			try {
				activePage = Integer.parseInt(params.getJsonArray("p", new JsonArray()).getString(0));
			} catch (NumberFormatException e) {
				// Nothing we can do, default value will be set later.
			}
		}

		if (activePage < 0) {
			activePage = 0;
		}

		StringBuilder result = new StringBuilder();
		String actionBase = RenderContextUtils.getValue(options.context, SearchHandler.RC_CUR_ACTION);
		JsonObject entry = new JsonObject()
			.put("label", config.getPaginationPrevious())
			.put("url", createPaginationUrl(actionBase, params, "p", activePage - 1));

		if (activePage > 0) {
			result.append(options.fn(entry));
		} else {
			result.append(options.inverse(entry));
		}

		int totalPages = (int) Math.ceil((double) totalHits / config.getQuerySize());
		int lastHeadPage = Math.min(totalPages, maxActivePages / 2);

		for (int ii = 1; ii <= lastHeadPage; ii++) {
			processPaginationEntry(result, actionBase, options, params, ii - 1, activePage);
		}

		int firstTailPage = Math.max(
			lastHeadPage + 1,
			totalPages - (maxActivePages + 1) / 2 + 1);

		if (totalPages > maxActivePages) {
			if (totalPages > maxActivePages + 1) {
				// Show the ellipsis only when at least two entries are skipped ...
				entry.put("label", config.getPaginationEllipsis())
					.remove("url");
				result.append(options.inverse(entry));
			} else {
				// ... otherwise just show the single entry anyways.
				processPaginationEntry(result, actionBase, options, params, lastHeadPage, activePage);
			}
		}

		for (int ii = firstTailPage; ii <= totalPages; ii++) {
			processPaginationEntry(result, actionBase, options, params, ii - 1, activePage);
		}

		entry = new JsonObject()
			.put("label", config.getPaginationNext())
			.put("url", createPaginationUrl(actionBase, params, "p", activePage + 1));

		if (activePage < totalPages - 1) {
			result.append(options.fn(entry));
		} else {
			result.append(options.inverse(entry));
		}

		return result.toString();
	}

	/**
	 * Get the JSON array containing the search results.
	 *
	 * @see #getSearchResponse(Options)
	 * @param options The helper options
	 * @return The JSON array containing the search results
	 */
	public static JsonArray getSearchResults(Options options) {
		return getSearchResponse(options).getJsonArray("results", new JsonArray());
	}

	/**
	 * Get the search response from the routing context data.
	 *
	 * @param options The helper options
	 * @return The search response as a JSON object
	 */
	public static JsonObject getSearchResponse(Options options) {
		return RenderContextUtils.getValue(options.context, SearchHandler.RC_SEARCH_RESULTS, JsonObject::new);
	}

	/**
	 * Render a single pagination entry into the given {@code result} string
	 * builder.
	 *
	 * <p>
	 *     When the {@code currentPage == activePage} the entry is rendered
	 *     using the helpers <code>{{else}}</code> block, otherwise it is
	 *     rendered using the normal block.
	 * </p>
	 *
	 * @see #createPaginationUrl(String, JsonObject, Object...)
	 *
	 * @param result The accumulator for the rendered entries
	 * @param actionBase The base path for the search action
	 * @param options The helper options (used for {@link Options#fn()} and
	 * 		{@link Options#inverse()})
	 * @param params The search parameters
	 * @param currentPage The page for the currently rendered pagination entry
	 * @param activePage The currently displayed page of the search results
	 * @throws IOException When rendering fails
	 */
	private void processPaginationEntry(StringBuilder result, String actionBase, Options options, JsonObject params, int currentPage, int activePage) throws IOException {
		JsonObject entry = new JsonObject()
			.put("label", currentPage + 1)
			.put("url", createPaginationUrl(actionBase, params, "p", currentPage));

		if (currentPage == activePage) {
			result.append(options.inverse(entry));
		} else {
			result.append(options.fn(entry));
		}
	}

	/**
	 * Create a pagination URL from the base action path, the current request
	 * parameters and the specified overrides.
	 *
	 * <p>
	 *     The overrides must be key/value pairs which will replace entries
	 *     from the {@code params}, and are intended to override the page
	 *     parameter {@code p}.
	 * </p>
	 *
	 * @param actionBase The base path for the search action
	 * @param params The request parameters
	 * @param overrides Overrides for the request parameters (e.g. the page parameter)
	 * @return The created URL
	 */
	private String createPaginationUrl(String actionBase, JsonObject params, Object ...overrides) {
		if (params == null || params.isEmpty() && overrides.length == 0) {
			return actionBase;
		}

		List<String> entries = new ArrayList<>();
		Object query = params.getValue("q");

		if (query instanceof JsonArray) {
			String queryString = ((JsonArray) query).stream()
				.filter(Objects::nonNull)
				.map(Object::toString)
				.collect(Collectors.joining("+"));

			params.put("q", queryString);
		}

		for (int ii = 0; ii < overrides.length; ii++) {
			String key = overrides[ii++].toString();

			params.remove(key);
			params.put(key, ii < overrides.length ? overrides[ii].toString() : "");
		}

		params.forEach(p -> entries.add(p.getKey() + "=" + p.getValue()));

		return actionBase + '?' + String.join("&", entries);
	}
}

