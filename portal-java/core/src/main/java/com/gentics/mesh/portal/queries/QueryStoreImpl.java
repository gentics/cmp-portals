package com.gentics.mesh.portal.queries;

import com.gentics.mesh.portal.api.queries.QueryStore;
import com.gentics.mesh.portal.config.PortalConfig;
import com.gentics.mesh.portal.util.SimpleLRUCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class QueryStoreImpl implements QueryStore {

	protected static final Logger log = LoggerFactory.getLogger(QueryStore.class);

	/** Pattern to remove comments in the source file. The inline modified (?m) enables Pattern.MULTILINE mode. */
	private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)\\s*#.*$");

	/** Pattern to find occurrences of fragments in a GraphQL query. */
	private static final Pattern FRAGMENT_USAGE_PATTERN = Pattern.compile("\\.{3}\\s*(\\w+)");

	/** Pattern to find fragment definitions. */
	private static final Pattern FRAGMENT_DEFINITION_PATTERN = Pattern.compile("fragment\\s+(\\w+)");

	/** The absolute path to the query files. */
	private final Path absoluteQueryPath;
	/** The {@link PortalConfig#getMeshProject() mesh project name} for this portal instance. */
	private final String defaultProjectName;

	private final PortalConfig config;

	// TODO Maybe use Caffeine as in memory cache in the future.
	/** Cache for prepared queries. */
	private final SimpleLRUCache<String, String> cache = new SimpleLRUCache<>();

	private boolean isCacheEnabled = true;

	/**
	 * Default constructor.
	 *
	 * @param config The portal configuration
	 */
	@Inject
	public QueryStoreImpl(PortalConfig config) {
		this(config, Paths.get(config.getQueryPath()).toAbsolutePath(), config.getMeshProject());
	}

	/**
	 * Parameterized non-injected constructor. Direct usage is for testing purposes only.
	 *
	 * @param config The portal configuration
	 * @param absoluteQueryPath query localion
	 * @param meshProject mesh project ID
	 */
	QueryStoreImpl(PortalConfig config, Path absoluteQueryPath, String meshProject) {
		this.config = config;
		this.absoluteQueryPath = absoluteQueryPath;
		this.defaultProjectName = meshProject;
	}

	/**
	 * Get the GraphQL query to load an object by its path.
	 *
	 * @return The GraphQL query to load an object by its path
	 */
	@Override
	public String getByPathQuery() {
		return loadQuery(QueryType.GraphQL, "loadByPath");
	}

	@Override
	public String getNavigationQuery() {
		return loadQuery(QueryType.GraphQL, "loadNavigation");
	}

	@Override
	public String getLanguagesQuery() {
		return loadQuery(QueryType.GraphQL, "loadLanguages");
	}

	/**
	 * Get the GraphQL query to load the top navigation.
	 *
	 * @return The GraphQL query to load the top navigation
	 */
	@Override
	public String getTopNavQuery() {
		return loadQuery(QueryType.GraphQL, "loadOnlyTopNav");
	}

	/**
	 * Get the GraphQL query to load the sitemap.
	 *
	 * @return The GraphQL query to load the sitemap
	 */
	@Override
	public String getSitemapQuery() {
		return loadQuery(QueryType.GraphQL, "loadAllForSitemap");
	}

	/**
	 * Check whether caching of queries is enabled.
	 *
	 * @return <code>true</code> when caching is enabled, <code>false</code> otherwise
	 */
	public boolean isCacheEnabled() {
		return isCacheEnabled;
	}

	/**
	 * Disable the query cache.
	 *
	 * @return Fluent API
	 */
	@Override
	public QueryStore disableCache() {
		isCacheEnabled = false;
		return this;
	}

	/**
	 * Enable the query cache.
	 *
	 * @return Fluent API
	 */
	public QueryStore enableCache() {
		isCacheEnabled = true;
		return this;
	}

	/**
	 * Clear the query.
	 *
	 * @return Fluent API
	 */
	@Override
	public QueryStore clearCache() {
		cache.clear();
		return this;
	}

	/**
	 * Convenience wrapper for {@link #loadQuery(QueryType, String, String)}
	 * for when the project name and schema prefix are irrelevant.
	 *
	 * @param type The query type
	 * @param queryName The name of the query
	 * @return The loaded query
	 */
	protected String loadQuery(QueryType type, String queryName) {
		return loadQuery(type, queryName, null);
	}

	/**
	 * Load the query with the given name and utilize the cache if available.
	 *
	 * <p>
	 * For GraphQL queries the <code>%contentSchema%</code>, <code>%folderSchema%</code>, and
	 * <code>%binadyContentSchema%</code> placeholders are replaced with the respective schema names
	 * for the specified project.
	 * </p>
	 *
	 * <p>
	 * Also this method will try to load missing fragment definitions in a GraphQL query from the
	 * <em>.../projectName/fragments</em> or <em>.../fragments</em> subdirectories.
	 * </p>
	 *
	 * @see #loadFromFile(String, String)
	 *
	 * @param type The type of the query
	 * @param queryName The name of the query (the filename without <code>.graphql</code>)
	 * @param projectName The name of the project to use (maybe <code>null</code> to use the default project)
	 * @return The loaded query
	 */
	protected String loadQuery(QueryType type, String queryName, String projectName) {
		String cacheKey = getCacheKey(type, queryName, projectName);
		// Try to load the query from cache
		String query = isCacheEnabled() ? cache.get(cacheKey) : null;

		// Otherwise load the query again and put it in the cache
		if (query == null) {
			query = loadFromFile(type.filename(queryName), projectName);

			if (type == QueryType.GraphQL) {
				String oldQuery;

				do {
					oldQuery = query = COMMENT_PATTERN.matcher(query).replaceAll("");
					query = addFragments(projectName, query);
				} while (!oldQuery.equals(query));

				query = query.replace("%folderSchema%", config.getFolderSchema())
					.replace("%contentSchema%", config.getContentSchema())
					.replace("%binaryContentSchema%", config.getBinaryContentSchema());
			}

			log.debug("Loaded query from filesystem:\n{}", query);

			if (isCacheEnabled() && query != null) {
				cache.put(cacheKey, query);
			}
		} else {
			log.trace("Loaded query from cache:\n{}", query);
		}

		return query;
	}

	/**
	 * Create a cache key from the provided arguments.
	 *
	 * @param type The type of the query
	 * @param queryName The query name
	 * @param projectName The project name
	 * @return A cache key constructed from the arguments
	 */
	private String getCacheKey(QueryType type, String queryName, String projectName) {
		StringBuilder ret = new StringBuilder(type.filename(queryName));

		if (!StringUtils.isBlank(projectName)) {
			ret.append("/").append(projectName);
		}

		return ret.toString();
	}

	/**
	 * Returns the query with the given name.
	 *
	 * <p>
	 * All query files are searched in the directory {@link #absoluteQueryPath}. The method
	 * first tries to open the file <code><em>projectName/queryName.typeExtension</em></code>.
	 * When this file cannot be opened the method will try again with the filename
	 * <code><em>queryName.typeExtension</em></code>. When this also fails, the method tries to
	 * load a default query from the classpath.
	 * </p>
	 *
	 * @param queryFilename The filename of the query
	 * @param projectName The name of the mesh project to use (defaults to the
	 * 	configured {@link PortalConfig#getMeshProject mesh project})
	 * @return The requested query or <code>null</code> if it could not be found
	 */
	private String loadFromFile(String queryFilename, String projectName) {
		log.trace("Trying to load file {}", queryFilename);

		if (StringUtils.isBlank(projectName)) {
			projectName = defaultProjectName;
		}

		Path path = absoluteQueryPath.resolve(projectName);
		InputStream input = openFile(path, queryFilename);

		if (input == null) {
			log.trace("No project specific query found in {}, trying generic version", path);

			path = absoluteQueryPath;
			input = openFile(path, queryFilename);

			if (input == null) {
				log.trace("No query found in {}, trying classpath", path);

				// nio.Path blames the "classpath:/" format, so we adapt it to the ordinary folder
				path = Paths.get("classpath/queries/");
				input = getClass().getResourceAsStream("/queries/" + queryFilename);

				if (input == null) {
					log.error("Could not find {}", queryFilename);

					return StringUtils.EMPTY;
				}
			}
		}

		log.debug("Loading {} from {}", queryFilename, path);

		try {
			return IOUtils.toString(input, "UTF-8");
		} catch (IOException e) {
			log.error("Could not load query {}: {}", queryFilename, e.getMessage(), e);
		} finally {
			try {
				input.close();
			} catch (IOException e) {
				log.warn("Could not close {} properly: {}", path, e.getMessage());
			}
		}

		return null;
	}

	private InputStream openFile(Path path, String filename) {
		try {
			return Files.newInputStream(path.resolve(filename), StandardOpenOption.READ);
		} catch (IOException e) {
			if (log.isDebugEnabled()) {
				log.debug("Cannot read query \"{}\" from {}", filename, path);
			}

			return null;
		}
	}

	private String addFragments(String projectName, String query) {
		// Find fragment uses (e.g. "... my_fragment")
		Set<String> missing = new TreeSet<>();
		Matcher matcher = FRAGMENT_USAGE_PATTERN.matcher(query);

		while (matcher.find()) {
			missing.add(matcher.group(1));
		}

		// The keyword "on" is not a fragment name, but also used after "...".
		missing.remove("on");

		// Find fragment definitions.
		matcher = FRAGMENT_DEFINITION_PATTERN.matcher(query);

		while (matcher.find()) {
			missing.remove(matcher.group(1));
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format("Missing fragments: [%s]", String.join(", ", missing)));
		}

		StringBuffer buf = new StringBuffer(query);

		for (String fragment : missing) {
			buf.append(loadFromFile(QueryType.GraphQL.filename("fragments/" + fragment), projectName));
		}

		return buf.toString();
	}
}
