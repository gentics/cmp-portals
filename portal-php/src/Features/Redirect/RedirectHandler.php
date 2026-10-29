<?php

namespace Gentics\PortalPhp\Features\Redirect;

use Exception;
use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadContent;
use Gentics\PortalPhp\Features\Redirect\Contract\RedirectHelper;
use Gentics\PortalPhp\PortalPhp;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh\NotFoundException;
use Gentics\PortalPhp\Features\ErrorHandler\Contract\ErrorHandler;
use Symfony\Component\HttpFoundation\Response;
use Illuminate\Support\Str;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Cache;

/**
 * Class RedirectHandler
 * @package Gentics\PortalPhp\Features\Keycloak
 */
class RedirectHandler implements ErrorHandler
{
    protected const REDIRECTS_CACHE_KEY = "redirects_loadonce_";
    protected const DATA_PROVIDER_PREFIX = "dataProvider:";
    protected const DEFAULT_HELPER = BasicRedirectHelper::class;

    /**
     * @var PortalPhp
     */
    protected $portalPhp;

    /**
     * @var \Illuminate\Config\Repository|mixed
     */
    protected $projectName;

    /**
     * @var array|null An array of custom redirects.
     */
    protected $customRedirects;

    /**
     * @var string The key for the DataProvider or the path of the status page otherwise
     */
    protected $customSource;

    /**
     * @var RedirectsSourceMode Where to get the custom redirect map from, and when to refresh it
     */
    protected $customSourceMode;

    /**
     * @var int The default redirect type to be used (301 or 303).
     */
    protected $redirectType = Response::HTTP_SEE_OTHER;

    /**
     * @var RedirectHelper A helper class extracting the actual redirect map from a page.
     */
    protected $helper;

    /**
     * @var \Illuminate\Config\Repository|mixed
     */
    protected $config;

    /**
     * RedirectHandler constructor.
     * @param PortalPhp $portalPhp
     * @throws \Throwable
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
        $this->config = $portalPhp->config('redirects', [], true);

        $this->projectName = $portalPhp->config('projectName');

        // Init only if we have a configuration for redirects and it is enabled
        if (!empty($this->config) && Arr::get($this->config, 'enabled', false) === true) {
            $this->init();
        }
    }

    /**
     * @param $request
     * @param \Throwable $exception
     * @return bool|false|\Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector|mixed
     * @throws \Throwable
     */
    public function handle($request, \Throwable $exception)
    {
        // If we get a HttpException or a Mesh NotFoundException
        // and the StatusCode is 404, then try to redirect first
        if ((
            $exception instanceof NotFoundException ||
                $exception instanceof HttpException
        ) &&
            $exception->getStatusCode() === Response::HTTP_NOT_FOUND
        ) {
            // Handle only if we have a helper
            if ($this->helper !== null) {
                $hasRedirects = $this->prepareRedirect();
                if ($hasRedirects && $redirect = $this->redirect($request)) {
                    return $redirect;
                }
            }
        }

        // If the redirection is not possible, just let other handlers
        // to continue in the chain.
        return static::CANNOT_HANDLE_THIS_ERROR;
    }

    /**
     * Initializes the helper class
     */
    private function init()
    {
        // Load helper class
        $helper = app()->make(Arr::get($this->config, 'helperClass', static::DEFAULT_HELPER));
        $this->customSource = Arr::get($this->config, 'source', null);

        // Continue only if its a RedirectHelper
        if ($helper instanceof RedirectHelper && !is_null($this->customSource)) {
            $this->helper = $helper;
            $this->helper->init(Arr::get($this->config, 'helperConfig', []));


            if (Str::startsWith($this->customSource, static::DATA_PROVIDER_PREFIX)) {
                $this->customSourceMode = RedirectsSourceMode::DATAPROVIDER;
                $this->customSource = Str::after($this->customSource, static::DATA_PROVIDER_PREFIX);
            } else {
                $this->customSourceMode = Arr::get($this->config, 'reload', false) === true ?
                    RedirectsSourceMode::RELOAD : RedirectsSourceMode::LOADONCE;
            }

            $this->redirectType = $this->redirectStatusCode(Arr::get($this->config, 'permanentRedirects', null));
        }
    }

    /**
     * Fetches the source for the redirect map with the customSourceMode method.
     *
     * @return bool Returns true if the fetch was successful and non-empty, otherwise returns false
     * @throws \Throwable
     */
    private function prepareRedirect()
    {
        $customRedirectsData = [];

        switch ($this->customSourceMode) {
            // DataProvider mode: Fetch redirect map from the DataProvider key
            case RedirectsSourceMode::DATAPROVIDER:
                $customRedirectsData = forward_static_call([DataProvider::class, 'get' . $this->customSource]);
                break;
                // LoadOnce mode: Fetch redirect map from a predefined source and cache
            case RedirectsSourceMode::LOADONCE:
                $cacheKey = static::REDIRECTS_CACHE_KEY . $this->projectName;
                $cacheTime = Arr::get($this->config, 'loadOnceCacheTime', 5 * 60);
                $customRedirectsData = Cache::remember($cacheKey, now()->addSeconds($cacheTime), function () {
                    return $this->loadFromMesh();
                });
                break;
                // Reload mode: Fetch redirect map from a predefined source every time
            case RedirectsSourceMode::RELOAD:
                $customRedirectsData = $this->loadFromMesh();
                break;
        }

        // Check if the Mesh data has a node key.
        $customRedirects = Arr::get($customRedirectsData, 'node', []);

        // If we got an array, try to parse it for redirects
        if (is_array($customRedirects)) {
            $this->convertData($customRedirects);
        }

        return !empty($this->customRedirects);
    }

    /**
     * Checks if there is a redirect match, and returns with the Redirector or false if no match.
     *
     * @param $request
     * @return \Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector|false
     */
    private function redirect($request)
    {
        $currentPath = static::normalizeUrl($request->url());

        // Check if there is any suitable redirect match, and redirect with corresponding status code
        if ($this->customRedirects !== null && Arr::has($this->customRedirects, $currentPath)) {
            $redirect = Arr::get($this->customRedirects, $currentPath);
            $redirectType = $this->redirectStatusCode($redirect->getPermanent());
            return redirect($redirect->getNewUrl(), $redirectType);
        }

        return false;
    }

    /**
     * Based on the passed variable decides on the status code to be used
     *
     * @param $isPermanent
     * @return int Redirect HTTP Status Code
     */
    private function redirectStatusCode($isPermanent)
    {
        if ($isPermanent === true) {
            return Response::HTTP_MOVED_PERMANENTLY;
        } elseif ($isPermanent === false) {
            return Response::HTTP_SEE_OTHER;
        }

        // If neither true or false we got, return the current redirectType
        return $this->redirectType;
    }

    /**
     * Calls the helpers convertData method, and sets the customRedirects property if there are any.
     *
     * @param array $redirectsPage The array of redirect map
     */
    private function convertData(array $redirectsPage)
    {
        $redirects = $this->helper->convertData($redirectsPage);

        if ($redirects == null || empty($redirects)) {
            $this->customRedirects = null;
        } else {
            $this->customRedirects = $redirects;
        }
    }

    /**
     * Loads the page containing redirect data from Mesh.
     *
     * @return mixed
     * @throws \Throwable
     */
    private function loadFromMesh()
    {
        return Mesh::request(LoadContent::class)
            ->setPath($this->customSource)
            ->setAbortOnError(false)
            ->setAdminClient(true)
            ->fetch()
            ->toArray()['data'];
    }

    /**
     * Get a normalised version of the given URL without trailing slashes.
     *
     * @param string $url The URL to normalise
     * @return bool|mixed|null|string A normalised version of the given URL without trailing slashes
     */
    public static function normalizeUrl(string $url)
    {
        $path = parse_url($url, PHP_URL_PATH);

        if ($path === null) {
            return null;
        }

        if (Str::endsWith($path, "/")) {
            return substr($path, 0, -1);
        }

        return $path;
    }
}
