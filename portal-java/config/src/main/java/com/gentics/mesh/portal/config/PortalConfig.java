package com.gentics.mesh.portal.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.gentics.mesh.portal.api.config.Config;
import com.gentics.mesh.portal.api.config.EnvironmentVariable;
import com.gentics.mesh.portal.api.config.InvalidConfigurationException;
import com.gentics.mesh.portal.config.option.AuthenticationOptions;
import com.gentics.mesh.portal.config.option.CachingOptions;
import com.gentics.mesh.portal.config.option.CompatibilityOptions;
import com.gentics.mesh.portal.config.option.CustomOptionsMap;
import com.gentics.mesh.portal.config.option.DataProviderOptions;
import com.gentics.mesh.portal.config.option.FormsOptions;
import com.gentics.mesh.portal.config.option.MailOptions;
import com.gentics.mesh.portal.config.option.MeshOptions;
import com.gentics.mesh.portal.config.option.MonitoringOptions;
import com.gentics.mesh.portal.config.option.NavigationOptions;
import com.gentics.mesh.portal.config.option.ProxyOptions;
import com.gentics.mesh.portal.config.option.RedirectOptions;
import com.gentics.mesh.portal.config.option.SearchOptions;
import com.gentics.mesh.portal.config.option.SecurityOptions;
import com.gentics.mesh.portal.config.option.StaticHandlerOptions;
import com.gentics.mesh.portal.config.option.VertxOptions;
import io.reactivex.annotations.NonNull;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PortalConfig implements Config {

	public static final String MESH_PROJECT_ENV = "MESH_PROJECT";
	public static final String MESH_SCHEMA_PREFIX_ENV = "MESH_SCHEMA_PREFIX";
	public static final String IMPLEMENTATION_VERSION_ENV = "IMPLEMENTATION_VERSION";
	public static final String PORTAL_IMPLEMENTATION_ENV = "PORTAL_IMPLEMENTATION";
	public static final String PORTAL_API_KEY_ENV = "PORTAL_API_KEY";
	public static final String SERVER_URL_ENV = "SERVER_URL";
	public static final String SERVER_PORT_ENV = "SERVER_PORT";
	public static final String SERVER_HOST_ENV = "SERVER_HOST";
	public static final String QUERY_PATH_ENV = "QUERY_PATH";
	public static final String TEMPLATES_PATH_ENV = "TEMPLATES_PATH";
	public static final String TEMPLATES_TOLERATE_ERRORS_ENV = "TEMPLATES_TOLERATE_ERRORS";
	public static final String TEMPLATE_POLL_RESULTS = "poll-results";
	public static final String POLL_RESULT_TEMPLATE_ENV = "POLL_RESULTS_TEMPLATE";
	public static final String I18N_PATH_ENV = "I18N_PATH";
	public static final String STATIC_PATH_ENV = "STATIC_PATH";
	public static final String STATIC_IMPLEMENTATION_PATH_ENV = "STATIC_IMPLEMENTATION_PATH";
	public static final String STATIC_CACHING_ENV = "STATIC_CACHING";
	public static final String STATIC_MAX_AGE_ENV = "STATIC_MAX_AGE";
	public static final String BREADCRUMBS_LEVEL_ENV = "BREADCRUMBS_LEVEL";
	public static final String DEFAULT_LANGUAGE_ENV = "DEFAULT_LANGUAGE";
	public static final String NETWORK_TIMEOUT_ENV = "GPJ_NETWORK_TIMEOUT";
	public static final String MAX_HTTP_HEADER_SIZE_ENV = "MAX_HTTP_HEADER_SIZE";

	public static final int DEFAULT_NETWORK_TIMEOUT = 30;

	// TODO Change the default host to 127.0.0.1. This is a breaking change, and therefore postponed to v2.0.
	public static final String DEFAULT_SERVER_HOST = "0.0.0.0";
	public static final String DEFAULT_UPLOAD_DIR = "data/uploads";

	@Deprecated
	@EnvironmentVariable(name = PORTAL_API_KEY_ENV, description = "Deprecated, use " + SecurityOptions.API_KEY_ENV + " instead")
	private String portalAPIKey;

	@JsonProperty(required = true)
	@JsonPropertyDescription("Name of the Mesh project.")
	@EnvironmentVariable(name = MESH_PROJECT_ENV, description = "Override the mesh project name.")
	private String meshProject;

	@JsonProperty(defaultValue = "null")
	@JsonPropertyDescription("Prefix of schemas in Mesh, if different from the project name. When this is null, the name of the Mesh project is used as the prefix. When using the 'Project per Node' setting in Gentics CMS, make sure that the prefix is different from the projectname.")
	@EnvironmentVariable(name = MESH_SCHEMA_PREFIX_ENV, description = "Override the mesh schema prefix.")
	private String meshSchemaPrefix;

	@JsonProperty(defaultValue = "null")
	@JsonPropertyDescription("The implementation version of the branch to use. When this is null, branches are not restricted by implementation versions.")
	@EnvironmentVariable(name = IMPLEMENTATION_VERSION_ENV, description = "Override the implementation version.")
	private String implementationVersion;

	@JsonProperty(defaultValue = "null")
	@JsonPropertyDescription("Keyword of the portal implementation to load from Mesh. When this is set, the package of the portal implementation node (schema [prefix]_implementation, direct child of the project root node) is downloaded on startup and whenever it changes, and its folders templates, queries and i18n are extracted into the configured paths, replacing the existing files. The folder static is extracted into static.implementationPath, and its files are served in addition to the files in static.path, taking precedence over them. Configured paths whose folder is missing in the package are emptied. When this is null, only the local files are used.")
	@EnvironmentVariable(name = PORTAL_IMPLEMENTATION_ENV, description = "Override the portal implementation keyword.")
	private String portalImplementation;

	@JsonProperty(required = true)
	@JsonPropertyDescription("The URL used for link resolving.")
	@EnvironmentVariable(name = SERVER_URL_ENV, description = "Override the server url.")
	private URL serverUrl;

	@JsonProperty(defaultValue = "3000")
	@JsonPropertyDescription("The port the portal runs on. If this is different then the port in the serverUrl, a reverse proxy will be needed to resolve links correctly")
	@EnvironmentVariable(name = SERVER_PORT_ENV, description = "Override the server port.")
	private int serverPort = 3000;

	@JsonProperty(defaultValue = DEFAULT_SERVER_HOST)
	@JsonPropertyDescription("The host IP address the portal binds to. Note that by default the portal will bind to all interfaces. When a reverse proxy is used, it is advised to only bind to 127.0.0.1.")
	@EnvironmentVariable(name = SERVER_HOST_ENV, description = "Override the host IP address to bind to.")
	private String serverHost = DEFAULT_SERVER_HOST;

	@JsonProperty(defaultValue = "null")
	@JsonPropertyDescription("Path to static files. This option is deprecated and should not be used. Use `static.path` instead.")
	@EnvironmentVariable(name = STATIC_PATH_ENV, description = "Override the server static path.")
	@Deprecated
	private String staticPath;

	@JsonProperty(defaultValue = "data/templates")
	@JsonPropertyDescription("Path to handlebar templates.")
	@EnvironmentVariable(name = TEMPLATES_PATH_ENV, description = "Override the server templates path.")
	private String templatesPath = "data/templates";

	@JsonProperty(defaultValue = "true")
	@JsonPropertyDescription("Whether the templating engine should tolerate any errors, otherwise fail with HTTP 500.")
	@EnvironmentVariable(name = TEMPLATES_TOLERATE_ERRORS_ENV, description = "Override the templating strict mode.")
	private boolean templatesTolerateErrors = true;

	@JsonProperty(defaultValue = TEMPLATE_POLL_RESULTS)
	@JsonPropertyDescription("Name of the template to use for the poll results.")
	@EnvironmentVariable(name = POLL_RESULT_TEMPLATE_ENV, description = "Override the poll results template.")
	private String pollResultsTemplate = TEMPLATE_POLL_RESULTS;

	@JsonProperty(defaultValue = "data/queries")
	@JsonPropertyDescription("Path to files containing graphql queries.")
	@EnvironmentVariable(name = QUERY_PATH_ENV, description = "Override the server query path.")
	private String queryPath = "data/queries";

	@JsonProperty(defaultValue = "data/i18n")
	@JsonPropertyDescription("Path to YAML resource bundles")
	@EnvironmentVariable(name = I18N_PATH_ENV, description = "Override the server i18n path.")
	private String i18nPath = "data/i18n";

	@JsonProperty(defaultValue = "0 (unlimited)")
	@JsonPropertyDescription("The maximum number of breadcrumb entries displayed (beginning at the end).")
	@EnvironmentVariable(name = BREADCRUMBS_LEVEL_ENV, description = "Override the server breadcrumbs level")
	private int breadcrumbsLevel = 0;

	@JsonProperty
	@JsonPropertyDescription("Path to the start page. A path to any language is enough, language fallback will be done automatically. While not mandatory, the portal cannot serve the path / when this is not set correctly.")
	private String startPage = "";

	@JsonProperty
	@JsonPropertyDescription("Path to the cookie hint page.")
	private String cookieHintPage = "";

	@JsonProperty
	@JsonPropertyDescription("Whether to automatically delete uploaded files when the request is handled.")
	private boolean autoDeleteUploads = false;

	@JsonProperty(defaultValue = DEFAULT_UPLOAD_DIR)
	@JsonPropertyDescription("Path to a custom directories for temporary storing uploaded files.")
	private String uploadDir = DEFAULT_UPLOAD_DIR;

	@JsonProperty(defaultValue = "en")
	@JsonPropertyDescription("The default language the portal assumes when it cannot infer the desired language from the request.")
	@EnvironmentVariable(name = DEFAULT_LANGUAGE_ENV, description = "Override the default language")
	private String defaultLanguage = "en";

	@JsonProperty(defaultValue = DEFAULT_NETWORK_TIMEOUT + "")
	@JsonPropertyDescription("Timeout for network requests (in- and outbound) in seconds")
	@EnvironmentVariable(name = NETWORK_TIMEOUT_ENV, description = "Override the timeout for network requests (in- and outbound) in seconds")
	private int networkTimeout = DEFAULT_NETWORK_TIMEOUT;

	@JsonProperty("mesh")
	@JsonPropertyDescription("Mesh Configuration")
	private MeshOptions meshOptions = new MeshOptions();

	@JsonProperty("vertx")
	@JsonPropertyDescription("Vert.x configuration")
	private VertxOptions vertxOptions = new VertxOptions();

	@JsonProperty("dataProvider")
	@JsonPropertyDescription("Data provider configuration")
	private DataProviderOptions dataProviderOptions = new DataProviderOptions();

	@JsonProperty("mail")
	@JsonPropertyDescription("Mail configuration")
	private MailOptions mailOptions = new MailOptions();

	@JsonProperty("redirects")
	@JsonPropertyDescription("Redirects configuration")
	private RedirectOptions redirectOptions = new RedirectOptions();

	@JsonProperty("security")
	@JsonPropertyDescription("Security configuration")
	private SecurityOptions securityOptions = new SecurityOptions();

	@JsonProperty("authentication")
	@JsonPropertyDescription("Authentication Configuration")
	private AuthenticationOptions authOptions = new AuthenticationOptions();

	@JsonProperty("proxy")
	@JsonPropertyDescription("Proxy configuration")
	private ProxyOptions proxyOptions = new ProxyOptions();

	@JsonProperty("customConfigMap")
	@JsonPropertyDescription("Custom configuration map")
	private CustomOptionsMap customOptionsMap = new CustomOptionsMap();

	@JsonProperty("static")
	@JsonPropertyDescription("Static handler configuration")
	private StaticHandlerOptions staticHandlerOptions = new StaticHandlerOptions();

	@JsonProperty("navigation")
	@JsonPropertyDescription("Navigation configuration")
	private NavigationOptions navigationOptions = new NavigationOptions();

	@JsonProperty("caching")
	@JsonPropertyDescription("Cache configuration")
	private CachingOptions cachingOptions = new CachingOptions();

	@JsonProperty("monitoring")
	@JsonPropertyDescription("Monitoring configuration")
	private MonitoringOptions monitoringOptions = new MonitoringOptions();

	@JsonProperty("compatibility")
	@JsonPropertyDescription("Compatibility options for older versions of Mesh or GCMS")
	private CompatibilityOptions compatibilityOptions = new CompatibilityOptions();

	@JsonProperty("search")
	private SearchOptions searchOptions = new SearchOptions();
	
	@JsonProperty("forms")
	private FormsOptions formsOptions = new FormsOptions();


	@Deprecated
	private Map<String, String> additionalResponseHeaders = new HashMap<>();

	@JsonProperty(defaultValue = "null")
	@JsonPropertyDescription("The name of the folder index template to use when listing contents of folders. If no template is specified, requesting a folder will result in a 404 response.")
	private String folderTemplateName;

	@JsonProperty(defaultValue = "" + io.vertx.core.http.HttpServerOptions.DEFAULT_MAX_HEADER_SIZE)
	@JsonPropertyDescription("The max size of HTTP header")
	@EnvironmentVariable(name = MAX_HTTP_HEADER_SIZE_ENV, description = "Override the Vert.x HTTP max header size")
	private int maxHttpHeaderSize = io.vertx.core.http.HttpServerOptions.DEFAULT_MAX_HEADER_SIZE;

	private List<RequiredQuery> checkedQueries = Collections.emptyList();

	@Nonnull
	public VertxOptions getVertxOptions() {
		if (vertxOptions == null) {
			vertxOptions = new VertxOptions();
		}

		return vertxOptions;
	}

	public PortalConfig setVertxOptions(VertxOptions vertxOptions) {
		this.vertxOptions = vertxOptions;
		return this;
	}

	public int getServerPort() {
		return serverPort;
	}

	public PortalConfig setServerPort(int serverPort) {
		this.serverPort = serverPort;
		return this;
	}

	public String getServerHost() {
		return serverHost;
	}

	public PortalConfig setServerHost(String serverHost) {
		this.serverHost = serverHost;
		return this;
	}

	public String getMeshProject() {
		return meshProject;
	}

	public PortalConfig setMeshProject(String meshProject) {
		this.meshProject = meshProject;
		return this;
	}

	public String getMeshSchemaPrefix() {
		return meshSchemaPrefix;
	}

	public PortalConfig setMeshSchemaPrefix(String meshSchemaPrefix) {
		this.meshSchemaPrefix = meshSchemaPrefix;
		return this;
	}

	public String getImplementationVersion() {
		return implementationVersion;
	}

	public PortalConfig setImplementationVersion(String implementationVersion) {
		this.implementationVersion = implementationVersion;
		return this;
	}

	public String getPortalImplementation() {
		return portalImplementation;
	}

	public PortalConfig setPortalImplementation(String portalImplementation) {
		this.portalImplementation = portalImplementation;
		return this;
	}

	/**
	 * Get the effective schema prefix. This will either be the meshSchemaPrefix (if set) or the meshProject
	 *
	 * @return effective schema prefix
	 */
	@JsonIgnore
	public String getEffectiveSchemaPrefix() {
		return meshSchemaPrefix != null ? meshSchemaPrefix : meshProject;
	}

	/**
	 * Get the name of the folder schema
	 *
	 * @return name of folder schema
	 */
	@JsonIgnore
	public String getFolderSchema() {
		return String.format("%s_folder", getEffectiveSchemaPrefix());
	}

	/**
	 * Get the name of the content schema
	 *
	 * @return name of content schema
	 */
	@JsonIgnore
	public String getContentSchema() {
		return String.format("%s_content", getEffectiveSchemaPrefix());
	}

	/**
	 * Get the name of the binary content schema
	 *
	 * @return name of binary content schema
	 */
	@JsonIgnore
	public String getBinaryContentSchema() {
		return String.format("%s_binary_content", getEffectiveSchemaPrefix());
	}

	/**
	 * Get the name of the portal implementation schema
	 *
	 * @return name of portal implementation schema
	 */
	@JsonIgnore
	public String getImplementationSchema() {
		return String.format("%s_implementation", getEffectiveSchemaPrefix());
	}

	public URL getServerUrl() {
		return serverUrl;
	}

	public PortalConfig setServerUrl(URL serverUrl) {
		this.serverUrl = serverUrl;
		return this;
	}

	public String getStaticPath() {
		return staticPath;
	}

	public PortalConfig setStaticPath(String staticPath) {
		this.staticPath = staticPath;
		return this;
	}

	public StaticHandlerOptions getStaticHandlerOptions() {
		return staticHandlerOptions;
	}

	public PortalConfig setStaticHandlerOptions(StaticHandlerOptions staticHandlerOptions) {
		this.staticHandlerOptions = staticHandlerOptions;
		return this;
	}

	public String getTemplatesPath() {
		return templatesPath;
	}

	public PortalConfig setTemplatesPath(String templatesPath) {
		this.templatesPath = templatesPath;
		return this;
	}

	public String getPollResultsTemplate() {
		if (pollResultsTemplate == null) {
			pollResultsTemplate = TEMPLATE_POLL_RESULTS;
		}

		return pollResultsTemplate;
	}

	public PortalConfig setPollResultsTemplate(String pollResultsTemplate) {
		this.pollResultsTemplate = pollResultsTemplate;

		return this;
	}

	public String getQueryPath() {
		return queryPath;
	}

	public PortalConfig setQueryPath(String queryPath) {
		this.queryPath = queryPath;
		return this;
	}

	public String getI18nPath() {
		return i18nPath;
	}

	public PortalConfig setI18nPath(String i18nPath) {
		this.i18nPath = i18nPath;
		return this;
	}

	/** @deprecated use {@link com.gentics.mesh.portal.config.option.SecurityOptions#getApiKey()} instead. */
	@Deprecated
	public String getPortalAPIKey() {
		return portalAPIKey;
	}

	public PortalConfig setPortalAPIKey(String portalAPIKey) {
		this.portalAPIKey = portalAPIKey;
		return this;
	}

	@Nonnull
	public MeshOptions getMeshOptions() {
		if (meshOptions == null) {
			meshOptions = new MeshOptions();
		}

		return meshOptions;
	}

	public PortalConfig setMesh(MeshOptions mesh) {
		this.meshOptions = mesh;
		return this;
	}

	public int getBreadcrumbsLevel() {
		return breadcrumbsLevel;
	}

	public PortalConfig setBreadcrumbsLevel(int breadcrumbsLevel) {
		this.breadcrumbsLevel = breadcrumbsLevel;
		return this;
	}

	public String getStartPage() {
		return startPage;
	}

	public PortalConfig setStartPage(String startPage) {
		this.startPage = startPage;
		return this;
	}

	@Nonnull
	public MailOptions getMailOptions() {
		if (mailOptions == null) {
			mailOptions = new MailOptions();
		}

		return mailOptions;
	}

	public PortalConfig setMailConfig(MailOptions mailOptions) {
		this.mailOptions = mailOptions;
		return this;
	}

	public DataProviderOptions getDataProviderOptions() {
		return dataProviderOptions;
	}

	public PortalConfig setDataProviderOptions(DataProviderOptions dataProviderOptions) {
		this.dataProviderOptions = dataProviderOptions;
		return this;
	}

	@Nonnull
	public RedirectOptions getRedirectOptions() {
		if (redirectOptions == null) {
			redirectOptions = new RedirectOptions();
		}

		return redirectOptions;
	}

	public PortalConfig setRedirectOptions(RedirectOptions redirectOptions) {
		this.redirectOptions = redirectOptions;
		return this;
	}

	public String getCookieHintPage() {
		return this.cookieHintPage;
	}

	public PortalConfig setCookieHintPage(String cookieHintPage) {
		this.cookieHintPage = cookieHintPage;
		return this;
	}

	@Nonnull
	public String getUploadDir() {
		if (StringUtils.isBlank(uploadDir)) {
			uploadDir = DEFAULT_UPLOAD_DIR;
		}

		return uploadDir;
	}

	public PortalConfig setUploadDir(String uploadDir) {
		this.uploadDir = uploadDir;
		return this;
	}

	public boolean isAutoDeleteUploads() {
		return autoDeleteUploads;
	}

	public PortalConfig setAutoDeleteUploads(boolean autoDeleteUploads) {
		this.autoDeleteUploads = autoDeleteUploads;
		return this;
	}

	public String getDefaultLanguage() {
		return defaultLanguage;
	}

	public PortalConfig setDefaultLanguage(String defaultLanguage) {
		this.defaultLanguage = defaultLanguage;
		return this;
	}

	public int getNetworkTimeout() {
		return networkTimeout;
	}

	public PortalConfig setNetworkTimeout(int networkTimeout) {
		this.networkTimeout = networkTimeout;

		return this;
	}

	@Nonnull
	public SecurityOptions getSecurityOptions() {
		if (securityOptions == null) {
			securityOptions = new SecurityOptions();
		}

		return securityOptions;
	}

	public PortalConfig setSecurityOptions(SecurityOptions securityOptions) {
		this.securityOptions = securityOptions;

		return this;
	}

	@NonNull
	public AuthenticationOptions getAuthOptions() {
		if (authOptions == null) {
			authOptions = new AuthenticationOptions();
		}

		return authOptions;
	}

	public PortalConfig setAuthOptions(AuthenticationOptions authOptions) {
		this.authOptions = authOptions;
		return this;
	}

	public ProxyOptions getProxyOptions() {
		return proxyOptions;
	}

	public PortalConfig setProxyOptions(ProxyOptions proxyOptions) {
		this.proxyOptions = proxyOptions;
		return this;
	}

	public CustomOptionsMap getCustomOptionsMap() {
		return customOptionsMap;
	}

	public PortalConfig setCustomOptionsMap(CustomOptionsMap customOptionsMap) {
		this.customOptionsMap = customOptionsMap;
		return this;
	}

	public NavigationOptions getNavigationOptions() {
		return navigationOptions;
	}

	public PortalConfig setNavigationOptions(NavigationOptions navigationOptions) {
		this.navigationOptions = navigationOptions;
		return this;
	}

	@Nonnull
	public CachingOptions getCachingOptions() {
		if (cachingOptions == null) {
			cachingOptions = new CachingOptions();
		}
		return cachingOptions;
	}

	public PortalConfig setCachingOptions(CachingOptions cachingOptions) {
		this.cachingOptions = cachingOptions;
		return this;
	}

	@Nonnull
	public MonitoringOptions getMonitoringOptions() {
		if (monitoringOptions == null) {
			monitoringOptions = new MonitoringOptions();
		}

		return monitoringOptions;
	}

	public PortalConfig setMonitoringOptions(MonitoringOptions monitoringOptions) {
		this.monitoringOptions = monitoringOptions;
		return this;
	}

	public CompatibilityOptions getCompatibilityOptions() {
		return compatibilityOptions;
	}

	public PortalConfig setCompatibilityOptions(CompatibilityOptions compatibilityOptions) {
		this.compatibilityOptions = compatibilityOptions;
		return this;
	}

	@Nonnull
	public SearchOptions getSearchOptions() {
		if (searchOptions == null) {
			searchOptions = new SearchOptions();
		}

		return searchOptions;
	}

	public PortalConfig setSearchOptions(SearchOptions searchOptions) {
		this.searchOptions = searchOptions;

		return this;
	}
	
	@Nonnull
	public FormsOptions getFormsOptions() {
		if (formsOptions == null) {
			formsOptions = new FormsOptions();
		}

		return formsOptions;
	}

	public PortalConfig setFormsOptions(FormsOptions formsOptions) {
		this.formsOptions = formsOptions;

		return this;
	}

	/** @deprecated Use {@link com.gentics.mesh.portal.config.option.SecurityOptions#getAdditionalResponseHeaders()} instead. */
	@Deprecated
	public Map<String, String> getAdditionalResponseHeaders() {
		if (additionalResponseHeaders == null) {
			additionalResponseHeaders = Collections.emptyMap();
		}

		return additionalResponseHeaders;
	}

	public PortalConfig setAdditionalResponseHeaders(Map<String, String> additionalResponseHeaders) {
		this.additionalResponseHeaders = additionalResponseHeaders;
		return this;
	}

	public String getFolderTemplateName() {
		return folderTemplateName;
	}

	public PortalConfig setFolderTemplateName(String folderTemplateName) {
		this.folderTemplateName = folderTemplateName;
		return this;
	}

	public boolean isTemplatesTolerateErrors() {
		return templatesTolerateErrors;
	}

	public PortalConfig setTemplatesTolerateErrors(boolean templatesTolerateErrors) {
		this.templatesTolerateErrors = templatesTolerateErrors;
		return this;
	}

	public List<RequiredQuery> getCheckedQueries() {
		if (checkedQueries == null) {
			checkedQueries = Collections.emptyList();
		}

		return checkedQueries;
	}

	public PortalConfig setCheckedQueries(List<RequiredQuery> checkedQueries) {
		this.checkedQueries = checkedQueries;

		return this;
	}

	public int getMaxHttpHeaderSize() {
		return maxHttpHeaderSize;
	}

	public PortalConfig setMaxHttpHeaderSize(int maxHttpHeaderSize) {
		this.maxHttpHeaderSize = maxHttpHeaderSize;

		return this;
	}

	@Override
	public void validate() {
		getMeshOptions().validate();
		getRedirectOptions().validate();
		getDataProviderOptions().validate();
		getNavigationOptions().validate();
		getCachingOptions().validate();
		getMonitoringOptions().validate();
		getSecurityOptions().validate();
		getAuthOptions().validate();
		getSearchOptions().validate();
		getFormsOptions().validate();

		if (StringUtils.isBlank(meshProject)) {
			throw new InvalidConfigurationException("meshProject", "Mesh project name was not configured in the server options");
		}

		if (serverUrl == null) {
			throw new InvalidConfigurationException("serverUrl", "The server URL was not configured in the server options");
		}

		if (StringUtils.isBlank(defaultLanguage)) {
			throw new InvalidConfigurationException("defaultLanguage", "The default language was not configured in the server options");
		}
	}

	public void overrideWithEnv() {
		Config.super.overrideWithEnv();

		getMailOptions().overrideWithEnv();
		getSecurityOptions().overrideWithEnv();
		getAuthOptions().overrideWithEnv();
		getProxyOptions().overrideWithEnv();
		getRedirectOptions().overrideWithEnv();
		getMeshOptions().overrideWithEnv();
		getDataProviderOptions().overrideWithEnv();
		getNavigationOptions().overrideWithEnv();
		getCachingOptions().overrideWithEnv();
		getMonitoringOptions().overrideWithEnv();
		getVertxOptions().overrideWithEnv();
		getStaticHandlerOptions().overrideWithEnv();
		getCompatibilityOptions().overrideWithEnv();
		getSearchOptions().overrideWithEnv();
		getFormsOptions().overrideWithEnv();
	}
}
