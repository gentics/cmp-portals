<?php

namespace Gentics\PortalPhp;

use Gentics\Mesh\Client\MeshClient;
use Gentics\Mesh\Client\Rest\MeshResponse;
use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Features\ErrorHandler\MeshClientErrorHandler;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\PortalBlade\ServiceProvider as PortalBlade;
use Gentics\PortalPhp\Features\PortalBlade\ProjectRoot;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadByPath;
use Gentics\PortalPhp\Features\Mesh\GraphqlLoader;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Gentics\PortalPhp\Features\HttpCache\AppCache;

use Dflydev\ApacheMimeTypes\PhpRepository;
use Teto\HTTP\AcceptLanguage;
use GuzzleHttp\Exception\ClientException;
use Symfony\Component\HttpKernel\Exception\HttpException;

use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Illuminate\Http\File;

/**
 * Class PortalPhp
 * @package Gentics\PortalPhp
 */
class PortalPhp
{
    /**
     * An instance of the Mesh client
     *
     * @var MeshClient|null
     */
    protected $adminMeshClient = null;

    /**
     * An instance of the Mesh client
     *
     * @var MeshClient|null
     */
    protected $meshClient = null;

    /**
     * PortalPhp constructor.
     */
    public function __construct()
    {
    }

    /**
     * Retrieve mesh client token
     *
     * @return null|string
     */
    public function getClientToken()
    {
        $apiKeyForBase = null;

        // TODO: Better token retrieval
        if ($this->isAuthenticationEnabled() && Auth::user() !== null && Auth::user()->token !== null) {
            $apiKeyForBase = Auth::user()->token->toString() ?? null;
        }

        return $apiKeyForBase;
    }
    /**
     * Get the Mesh client
     *
     * @param boolean $fallbackToAdmin
     * @return MeshClient|null
     * @throws \Throwable
     */
    public function getMeshClient($fallbackToAdmin = false): ?MeshClient
    {
        if (!$this->isAuthenticationEnabled() && $fallbackToAdmin) {
            return $this->getAdminMeshClient();
        }

        if (null === $this->meshClient) {
            $apiKeyForBase = $this->getClientToken();
            $this->meshClient = $this->createBaseMeshClient($apiKeyForBase);
        }
        return $this->meshClient;
    }

    /**
     * Get the Admin Mesh client
     *
     * @return MeshClient|null
     * @throws \Throwable
     */
    public function getAdminMeshClient(): ?MeshClient
    {
        if (null === $this->adminMeshClient) {
            $this->adminMeshClient = $this->createAdminMeshClient();
        }
        return $this->adminMeshClient;
    }

    /**
     * Set the Mesh client
     *
     * @param MeshClient|null $meshClient
     * @return PortalPhp
     */
    public function setMeshClient(?MeshClient $meshClient): PortalPhp
    {
        $this->meshClient = $meshClient;
        return $this;
    }

    /**
     * Set the Admin Mesh client
     *
     * @param MeshClient|null $meshClient
     * @return PortalPhp
     */
    public function setAdminMeshClient(?MeshClient $meshClient): PortalPhp
    {
        $this->adminMeshClient = $meshClient;
        return $this;
    }

    /**
     * @param array $data
     * @param null|string $path
     * @return mixed
     * @throws \Throwable
     */
    public function getTemplateName(array $data, ?string $path = null)
    {
        $templatePath = $path;
        if (empty($templatePath)) {
            $templatePath = $this->config('templateNamePath');
        }

        throw_if(
            empty($templatePath),
            Exceptions\PortalPhp\NoTemplatePathSpecifiedException::class
        );

        $templateName = Arr::get($data, $templatePath);
        throw_if(
            empty($templateName),
            Exceptions\PortalPhp\NoTemplateFoundException::class,
            $templatePath
        );

        return $templateName;
    }

    /**
     * @param string $path
     * @param null|string $projectName
     * @param array $queryParameters
     * @param null|string $contentSchema
     * @param null|string $folderSchema
     * @param null|string $binaryContentSchema
     * @param bool $abortOnError
     * @param null|string $folderPathOverride
     * @param bool|null $adminClient
     * @param string|null $overrideBranchName
     * @return MeshResponse
     * @throws \Throwable
     * @deprecated 1.0.0 This method is replaced by the GraphqlRequest feature.
     */
    public function loadByPath(
        string $path,
        ?string $projectName = null,
        array $queryParameters = [],
        ?string $contentSchema = null,
        ?string $folderSchema = null,
        ?string $binaryContentSchema = null,
        bool $abortOnError = true,
        ?string $folderPathOverride = null,
        ?bool $adminClient = false,
        ?string $overrideBranchName = null
    ): MeshResponse {
        $response = Mesh::request(LoadByPath::class)
            ->setPath($path)
            ->setProjectName($projectName)
            ->setParameters($queryParameters)
            ->setContentSchema($contentSchema)
            ->setFolderSchema($folderSchema)
            ->setBinaryContentSchema($binaryContentSchema)
            ->setAbortOnError($abortOnError)
            ->setFolderPath($folderPathOverride)
            ->setAdminClient($adminClient)
            ->setBranchName($overrideBranchName)
            ->fetch();

        return $response->getMeshResponse();
    }

    /**
     * @param string $path
     * @param null|string $overrideProjectName
     * @param array $queryParameters Additional parameters
     * @param bool|null $adminClient
     * @param string|null $overrideBranchName
     * @param bool $stream Stream Through Guzzle
     * @return MeshResponse
     * @throws \Throwable
     */
    public function loadBinaryByPath(
        string $path,
        ?string $overrideProjectName = null,
        array $queryParameters = [],
        ?bool $adminClient = false,
        ?string $overrideBranchName = null,
        ?bool $stream = true
    ) : MeshResponse {
        static::devLog('[ETAG-CACHE] Check if we have cached etag response');
        // Try to serve etag cache first
        $cachedEtagResponse = Mesh::tryServeEtagCachedResponse();

        if ($cachedEtagResponse) {
            AppCache::bypassWhen('response3xx');

            static::devLog('[ETAG-CACHE] We have etag response, serve it instead of requesting from Mesh');
            return $cachedEtagResponse;
        }

        static::devLog('[ETAG-CACHE] We do not have etag cached, request from Mesh');

        $path = Mesh::normalizePath($path, false);

        $projectName = $overrideProjectName;
        if (empty($projectName)) {
            $projectName = $this->config('projectName');
        };

        $meshClient = $adminClient ? $this->getAdminMeshClient() : $this->getMeshClient();
        $parameters = $queryParameters;
        $parameters['version'] = 'published';
        $branchName = $overrideBranchName;

        if (empty($branchName)) {
            $branchName = $this->findBranchNameFromRequest($path);
        }

        if (!empty($branchName)) {
            $parameters['branch'] = $branchName;
        }

        $useWebrootField = $this->config('compatibility.useWebrootField', true, true);
        if ($useWebrootField) {
            $request = $meshClient->webrootField(
                $projectName,
                Mesh::FIELD_BINARYCONTENT,
                $path,
                $parameters,
                $stream
            );
        } else {
            $request = $meshClient->webroot(
                $projectName,
                $path,
                $parameters,
                $stream
            );
        }

        $webRootHttpHeadersWhiteList = $this->config('webrootHttpHeaderWhiteList');

        if (isset($webRootHttpHeadersWhiteList['request'])) {
            // If http-cache enabled get headers from the raw values
            if (config('portal.cache.http.enabled')) {
                $originalPreRequestHeaders = array_change_key_case(getallheaders(), CASE_LOWER);
                $originalRequestHeaders = [];
                foreach ($originalPreRequestHeaders as $k => $v) {
                    $originalRequestHeaders[$k] = [$v];
                }
            } else {
                $originalRequestHeaders = request()->headers->all();
            }

            $httpRequestHeaders = filterArrayWildcard(
                $originalRequestHeaders,
                $webRootHttpHeadersWhiteList['request'] ?? []
            );

            $this->removeEtagSuffix($httpRequestHeaders);

            foreach ($httpRequestHeaders as $httpRequestHeaderKey => $httpRequestHeaderValue) {
                $request->addHeader(
                    $httpRequestHeaderKey,
                    $httpRequestHeaderValue[0]
                );
            }
        }

        try {
            return $request->send();
        } catch (ClientException $e) {
            throw new HttpException($e->getCode(), $e->getMessage());
        }
    }

    /**
     * @param MeshResponse $response
     * @param array $additionalHttpHeaders
     * @return \Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     * @deprecated 1.0.0 This method is replaced by BinaryResponse
     */
    public function streamResponse(MeshResponse $response, $additionalHttpHeaders = [])
    {
        return Mesh::response($response)->streamResponse($additionalHttpHeaders);
    }

    /**
     * @param MeshResponse $response
     * @return bool
     * @deprecated 1.0.0 This method is replaced by BinaryResponse
     */
    public function isBinaryResponse(MeshResponse $response): bool
    {
        return ($response->hasHeader('Webroot-Response-Type')
            && $response->getHeader('Webroot-Response-Type')[0] === 'binary');
    }

    /**
     * @param string $file
     * @param string $contentSchema
     * @param string $folderSchema
     * @param array $variables
     * @return array|null
     * @throws \Throwable
     * @deprecated 1.0.0 This method is replaced by the GraphqlLoader feature.
     */
    public function loadGraphqlQuery(
        string $file,
        string $contentSchema,
        string $folderSchema,
        array $variables = []
    ) {
        $replace = [
            "%contentSchema%" => $contentSchema,
            "%folderSchema%" => $folderSchema
        ];

        $queryParameters = $this->loadGraphqlQueryFromFile($file, $replace, $variables);

        return $queryParameters;
    }

    /**
     * @param string $file
     * @param array $replace
     * @param array $variables
     * @return array|null
     * @throws \Throwable
     * @deprecated 1.0.0 This method is replaced by the GraphqlLoader feature.
     */
    public function loadGraphqlQueryFromFile(
        string $file,
        array $replace = [],
        array $variables = []
    ) {
        $query = app()->make(GraphqlLoader::class);
        $queryParameters = $query
            ->fromTemplate($file)
            ->setPlaceholders($replace)
            ->setVariables($variables)
            ->buildAsQuery();

        return $queryParameters;
    }

    /**
     * Analyze the given path and check if the mimeType determined by the
     * file extension is listed as binary content type
     *
     * @param $path
     * @return bool
     * @throws \Throwable
     */
    public function guessIsBinaryContent($path)
    {
        $file = new File($path, false);
        $repository = new PhpRepository();
        $mimeType = $repository->findType(strtolower($file->getExtension()));

        $configMimeTypes = $this->config('binaryFilesGuesser.mimeTypes', true);

        if (empty($configMimeTypes) || !is_array($configMimeTypes)) {
            return false;
        }

        foreach ($configMimeTypes as $configMimeType) {
            if (Str::startsWith($mimeType, $configMimeType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Save the language in the user session
     *
     * @param string $code
     */
    public function saveUserLanguage(string $code)
    {
        if (request()->hasSession()) {
            request()->session()->put('portalPhp.languageCode', $code);
        }
    }

    /**
     * Get the user language from his session
     *
     * @return mixed
     */
    public function getUserLanguage()
    {
        if (request()->hasSession()) {
            return request()->session()->get('portalPhp.languageCode');
        }

        return null;
    }

    /**
     * Returns the default language
     *
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    public function getDefaultLanguage()
    {
        return $this->config('defaultLanguage', 'en');
    }

    /**
     * Tries to select the preferred language of the set of available languages or return null when fails.
     *
     * @param array $availableLanguages
     * @return string|null
     * @throws \Throwable
     */
    public function getPreferredLanguage(array $availableLanguages): ?string
    {
        $userLanguage = $this->getUserLanguage();
        $browserPreference = AcceptLanguage::get();
        $defaultLanguage = $this->getDefaultLanguage();

        // Check if the user language exists in the available languages
        if (!empty($userLanguage) && in_array($userLanguage, $availableLanguages)) {
            return $userLanguage;
        }

        // Check if the browser preferred language exists in the available languages
        if (!empty($browserPreference)) {
            foreach ($browserPreference as $pref) {
                if (in_array(strtolower($pref['language']), $availableLanguages)) {
                    return strtolower($pref['language']);
                }
            }
        }

        // Check if the default language exists in the available languages
        if (!empty($defaultLanguage) && in_array($defaultLanguage, $availableLanguages)) {
            return $defaultLanguage;
        }

        return null;
    }

    /**
     * @param array $startPageLanguages
     * @return mixed
     * @throws \Throwable
     */
    public function getLocalizedStartPagePath(array $startPageLanguages)
    {
        $localizedStartPages = Arr::pluck($startPageLanguages, 'path', 'language');
        $preferredLanguage = $this->getPreferredLanguage(array_keys($localizedStartPages));

        if (!is_null($preferredLanguage)) {
            return $localizedStartPages[$preferredLanguage];
        }

        return null;
    }

    /**
     * Create a new mesh Client.
     *
     * The API URL and API key will be read from the config.
     * @param $apiKey
     * @param $url
     * @return MeshClient
     * @throws \Throwable
     */
    protected function createMeshClient($apiKey = null, $url = null, $additionalClientConfig = []): MeshClient
    {
        $meshURL    = !is_null($url) ? $url : $this->config('url');
        $meshClientErrorHandler = app()->make(MeshClientErrorHandler::class);

        $clientConfiguration = [];
        $clientConfiguration['handler'] = $meshClientErrorHandler->createErrorHandlerStack();

        if (!$this->config('meshVerifySsl', true, true)) {
            // Disable SSL verification for Mesh client
            $clientConfigurationNoVerify = [
                'verify' => false
            ];
            $clientConfiguration = array_merge($clientConfiguration, $clientConfigurationNoVerify);
        }

        if ($this->config('meshUseHttp2', false, true)) {
            if (extension_loaded('curl') && (curl_version()["features"] & CURL_VERSION_HTTP2 !== 0)) {
                // Enable HTTP/2 connectivity to Mesh, if requested, and the environment supports it
                $clientConfigurationVersion = [
                    'version' => '2'
                ];
                $clientConfiguration = array_merge($clientConfiguration, $clientConfigurationVersion);
            }
        }

        $clientConfiguration = array_merge($clientConfiguration, $additionalClientConfig);

        $client = new MeshClient($meshURL, $clientConfiguration);
        // PHP will replace dots (.) in the names of multipart/form-data parts, so we need to fix the names, if they
        // belong to uploaded files
        $client->setMultipartKeyHandler(function (String $key): String {
            // 1. URL decode
            $decoded = urldecode($key);

            // 2. Pattern 1: $_properties["name"]_value (single file upload)
            //    Pattern 2: $_properties["name"]_values[0] (multi file upload)
            $pattern = '/^\$_properties\["([^"]+)"\]_values?\[?(\d+)?\]?$/';

            if (preg_match($pattern, $decoded, $matches)) {
                $name = $matches[1];

                // 3. Check whether single or multifile
                if (isset($matches[2])) {
                    // $_properties["name"]_values[0] → $.properties["name"].values[0]
                    $transformed = '$.properties["' . $name . '"].values[' . $matches[2] . ']';
                } else {
                    // $_properties["name"]_value → $.properties["name"].value
                    $transformed = '$.properties["' . $name . '"].value';
                }

                // 4. URL encode and return
                return urlencode($transformed);
            }

            // 5. return original key
            return $key;
        });

        $client->setTimeout($this->config('meshClientTimeout', 0, true));
        $client->setAPIKey($apiKey);

        return $client;
    }

    /**
     * Create a new mesh Client.
     *
     * The API URL and API key will be read from the config.
     * @param $apiKey
     * @return MeshClient
     * @throws \Throwable
     */
    protected function createBaseMeshClient($apiKey = null): MeshClient
    {
        $client = $this->createMeshClient($apiKey);
        $this->setMeshClient($client);

        return $client;
    }

    /**
     * Create a new admin mesh Client.
     *
     * The API URL and API key will be read from the config.
     * @return MeshClient
     * @throws \Throwable
     */
    protected function createAdminMeshClient(): MeshClient
    {
        $meshApiKey = $this->config('apiKey');

        $client = $this->createMeshClient($meshApiKey);
        $this->setAdminMeshClient($client);

        return $client;
    }

    /**
     * @param string $keyPath
     * @param null $default
     * @param bool $allowEmpty
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    public function config(string $keyPath, $default = null, $allowEmpty = false)
    {
        $value = config('portal.' . $keyPath, $default);
        throw_if(
            !$allowEmpty && empty($value),
            Exceptions\PortalPhpException::class,
            "The config value for portal." . $keyPath . " must be set!"
        );

        return $value;
    }

    /**
     * Finds the correct branch to use
     * @param string $path Path
     * @return null
     * @throws \Throwable
     */
    public function findBranchFromRequest(string $path)
    {
        // Requires leading slash to detect branch
        $path = Mesh::normalizePath($path);

        if (!$this->config("branches.enabled", false, true)) {
            return null;
        }

        $dataProvider = app()->make(DataProvider::class);
        $branches = $dataProvider->retrieveBranches();

        $request = request();
        $implementationVersion = $this->getImplementationVersion();

        // Sort branches by path length
        $branches = Arr::sort($branches, function ($branch, $key) {
            return strlen($branch['pathPrefix'])*-1;
        });

        foreach ($branches as $branch) {
            if ($this->config("branches.matchHttpHost", false, true)
                    && mb_strtolower($branch['hostname']) !== mb_strtolower($request->getHttpHost())) {
                continue;
            }

            if ($this->config("branches.matchPageDirectory", false, true)
                && !empty($branch['pathPrefix'])
                && !Str::startsWith($path, Mesh::normalizePath($branch['pathPrefix']))
                && $path != Mesh::normalizePath($branch['pathPrefix'])) {
                continue;
            }

            $branchImplementationVersion = $this->getBranchTagNameByTagFamily(
                $branch['tags'],
                "gcmsImplementationVersion"
            );
            if (!empty($implementationVersion)
                    && ($branchImplementationVersion === null
                        || $branchImplementationVersion !== $implementationVersion)) {
                continue;
            }

            return $branch;
        }

        return [];
    }

    /**
     * Finds the correct branch name to use
     * @param string $path Path
     * @return null
     * @throws \Throwable
     */
    public function findBranchNameFromRequest(string $path)
    {
        $branch = $this->findBranchFromRequest($path);
        if (!empty($branch) && array_key_exists('name', $branch)) {
            return $branch['name'];
        }

        return null;
    }

    /**
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    public function getImplementationVersion()
    {
        return $this->config("branches.implementationVersion", null, true);
    }

    /**
     * Converts a Gentics CMS channel UID into the associated branch name, using the Mesh branch API endpoint
     * @param string $uuid The Gentics CMS channel UID
     * @return string|null A branch name or null
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    public function gcmsChannelUuidToBranchName(string $uuid)
    {
        $dataProvider = app()->make(DataProvider::class);
        $branches = $dataProvider->retrieveBranches();

        foreach ($branches as $branch) {
            $gcmsChannelUuid = $this->getBranchTagNameByTagFamily($branch["tags"], "gcmsChannelUuid");
            if ($gcmsChannelUuid === $uuid) {
                return $branch['name'];
            }
        }

        return null;
    }

    /**
     * Given a tag family, it finds the associated tag name from the branch tags
     * @param array $tags Tags from branches API response
     * @param string $tagFamily Tag family
     * @return string|null The name or null
     */
    protected function getBranchTagNameByTagFamily(array $tags, string $tagFamily)
    {
        foreach ($tags as $tag) {
            if ($tag['tagFamily'] === $tagFamily) {
                return $tag['name'];
            }
        }
    }

    /**
     * Some webservers like Apache2 add a suffix to the Etag when the content is gzip-encoded.
     * Example: "66d01e4f-gzip"
     * This method removes the -gzip suffix from 'if-none-match'
     * Also see:
     * * https://bz.apache.org/bugzilla/show_bug.cgi?id=39727
     * * https://flameeyes.blog/2017/08/22/apache-etag-and-not-modified/
     * @param array $requestHeaders HTTP request headers
     */
    protected function removeEtagSuffix(array &$requestHeaders)
    {
        if (!isset($requestHeaders['if-none-match'][0])) {
            return;
        }

        $splitted = explode('-', trim($requestHeaders['if-none-match'][0], '"'), 2);

        if (count($splitted) == 2) {
            $requestHeaders['if-none-match'][0] = '"' . $splitted[0] . '"';
        }
    }

    /**
     * Returns the actual RenderMode
     *
     * @return int|string RenderMode
     */
    public function renderMode()
    {
        $route = app('request')->route();
        $action = $route === null ? null : $route->getAction();

        if ($action !== null && isset($action['controller'])) {
            $controller = class_basename($action['controller']);

            list($controller, $action) = explode('@', $controller);

            if ($controller == 'CmsController') {
                $cmsRenderMode = app('request')->query('renderMode', 'preview');
                return RenderMode::getValue(strtoupper($cmsRenderMode));
            }
        }

        return RenderMode::PUBLISH;
    }

    /**
     * A feature is enabled, when configuration key exists.
     * @param $feature
     * @return bool
     */
    public function isFeatureEnabled($feature)
    {
        $features = array_keys(config('portal', []));
        if (in_array($feature, $features)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the state of the authentication feature
     * @return bool
     */
    public function isAuthenticationEnabled()
    {
        return $this->isFeatureEnabled('authentication');
    }

    /**
     * Returns project name of Portal | php or Portal | Essentials.
     *
     * @return string Project name
     */
    public function getProjectName()
    {
        $loadedProviders = app()->getLoadedProviders();

        if (isset($loadedProviders[PortalBlade::class])) {
            $projectRoot = app()->make(ProjectRoot::class);
            return $projectRoot->getProjectName();
        }

        return $this->config('projectName');
    }

    /**
     * Returns product name
     *
     * @return string Product name
     */
    public function getProductName()
    {
        $loadedProviders = app()->getLoadedProviders();

        if (isset($loadedProviders[PortalBlade::class])) {
            return 'Gentics Portal | essentials';
        }

        return 'Gentics Portal | php';
    }

    /**
     * Returns product version
     *
     * @return string Version
     */
    public function getProductVersion()
    {
        $version = 'unknown';
        $loadedProviders = app()->getLoadedProviders();

        if (isset($loadedProviders[PortalBlade::class])) {
            $packageName = 'gentics/portal-essentials';
        } else {
            $packageName = 'gentics/portal-php';
        }

        try {
            $versionData = \PackageVersions\Versions::getVersion($packageName);
        } catch (\Exception $e) {
        }

        if (is_string($versionData)) {
            $versionArr = explode('@', $versionData);

            if (substr($versionArr[0], 0, 4) === 'dev-') {
                $version = $versionArr[0] . '-g' . substr($versionArr[1], 0, 7);
            } else {
                $version = $versionArr[0];
            }
        }

        return $version;
    }

    /**
     * Builds a Cookie header string from array key value pairs
     */
    public static function cookieHeaderFromArray(array $cookieArray): string
    {
        return join("; ", array_map(
            fn (string $k, string $v) => "{$k}={$v}",
            array_keys($cookieArray),
            array_values($cookieArray)
        ));
    }

    /**
     * Logs to the configured channel
     */
    public static function devLog($text)
    {
        $logChannel = config('portal.devLogChannel', 'debug');

        // debug, info, notice, warning, error, critical, alert, emergency
        switch ($logChannel) {
            case 'emergency':
                \Log::emergency($text);
                break;
            case 'alert':
                \Log::alert($text);
                break;
            case 'critical':
                \Log::critical($text);
                break;
            case 'error':
                \Log::error($text);
                break;
            case 'warning':
                \Log::warning($text);
                break;
            case 'notice':
                \Log::notice($text);
                break;
            case 'info':
                \Log::info($text);
                break;
            case 'debug':
            default:
                \Log::debug($text);
                break;
        }
    }
}
