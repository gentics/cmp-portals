<?php

use \Gentics\PortalPhp\Blade;
use Gentics\PortalPhp\Features\Blade\Directives;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\RenderMode;
use Illuminate\Contracts\Routing\UrlGenerator;

/*
 * Think twice if you really want to add new functions here, global functions pollute the global namespace!
 */

if (!function_exists('renderBlade')) {

    /**
     * Renders the given string with the blade engine
     * Note: Unlike the @eval directive, the Blade template passed will not
     * have access to variables defined in the parent template automatically.
     * But you can pass get_defined_vars() to forward them.
     *
     * @param string $php Blade template string
     * @param array $data Optional variables
     * @return false|string The compiled blade string
     * @throws Exception
     */
    function renderBlade($php = "", array $data = [])
    {
        return Directives::renderBladeString($php, $data);
    }
}

if (!function_exists('filterArrayWildcard')) {
    /**
     * Filters the array $original with the array $filter.
     * Each entry of $original is compared with eeach entry of $filter.
     * If one of the entries matches the filter, the entry is not removed.
     * @param array $original Input array
     * @param array $filter Filter list with wildcard support
     * @return array Filtered list
     */
    function filterArrayWildcard(array $original, array $filter)
    {
        $filtered = [];

        foreach ($original as $originalName => $originalValue) {
            foreach ($filter as $filterName) {
                if (fnmatch($filterName, $originalName, FNM_CASEFOLD)) {
                    $filtered[$originalName] = $originalValue;
                    continue;
                }
            }
        }

        return $filtered;
    }
}

if (!function_exists('renderMode')) {
    /**
     * Returns the current renderMode name in lowercase
     *
     * @return int|string
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    function renderMode()
    {
        $portalPhp = app()->make(PortalPhp::class);
        return strtolower(RenderMode::getKey($portalPhp->renderMode()));
    }
}

if (!function_exists('authEnabled')) {
    /**
     * Returns the state of the authentication feature
     *
     * @return boolean
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    function authEnabled()
    {
        $portalPhp = app()->make(PortalPhp::class);
        return $portalPhp->isAuthenticationEnabled();
    }
}

if (!function_exists('gisImage')) {

    /**
     * Corrects GenticsImageStore url in a string
     *
     * @param string|null $image String which may contain /GenticsImageStore/ url to prepend with routerPrefix
     * @return mixed
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    function gisImage(?string $image)
    {
        $portalPhp = app()->make(PortalPhp::class);

        if ($portalPhp->renderMode() === RenderMode::PUBLISH) {
            $prefix = empty($portalPhp->config('routerPrefix', '', true)) ? '' :
                '/' . $portalPhp->config('routerPrefix');
            $pattern = '/GenticsImageStore/';
            $image = str_replace($pattern, $prefix . $pattern, $image);
        }

        return $image;
    }
}

if (!function_exists('routerPrefix')) {

    /**
     * Returns the router prefix based on the configuration or environment variable
     *
     * @return mixed
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    function routerPrefix()
    {
        $portalPhp = app()->make(PortalPhp::class);

        $prefix = $portalPhp->config('routerPrefix', '', true);

        if (empty($prefix) && isset($_SERVER['GPPHP_BLADE_MODE']) && $_SERVER['GPPHP_BLADE_MODE'] === 'folder') {
            $prefix = $_SERVER['GPPHP_BLADE_PROJECT'];
        }

        return $prefix;
    }
}

if (! function_exists('assetRel')) {
    /**
     * Generate an absolute to root asset path for the application.
     * @see \Illuminate\Routing\UrlGenerator::asset For the $path and $secure params
     *
     * @param  string  $path
     * @param  bool|null  $secure
     * @param  bool $publishModeAware If true and the CMS is not in publish mode, remove the routerPrefix from the path
     * @return string
     */
    function assetRel($path, $secure = null, $publishModeAware = false)
    {
        return app('url')->assetRel($path, $secure, $publishModeAware);
    }
}



if (! function_exists('urlRel')) {
    /**
     * Generate an absolute to root url for the application.
     * @see \Illuminate\Routing\UrlGenerator::to For the $path, $parameters and $secure params
     *
     * @param  string  $path
     * @param  mixed  $parameters
     * @param  bool|null  $secure
     * @return \Illuminate\Contracts\Routing\UrlGenerator|string
     */
    function urlRel($path = null, $parameters = [], $secure = null)
    {
        if (is_null($path)) {
            $url = app(UrlGenerator::class);
        } else {
            $url = app(UrlGenerator::class)->toRel($path, $parameters, $secure);
        }

        return $url;
    }
}
