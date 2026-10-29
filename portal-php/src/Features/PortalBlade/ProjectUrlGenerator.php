<?php
namespace Gentics\PortalPhp\Features\PortalBlade;

use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\RenderMode;
use Illuminate\Routing\UrlGenerator;
use Illuminate\Support\Str;

class ProjectUrlGenerator extends UrlGenerator
{
    /** @var PortalPhp|null */
    protected $portalPhp = null;

    /** @var bool */
    protected $disableHost = false;

    /** @var bool */
    protected $disableRouterPrefix = false;

    public function __construct(UrlGenerator $url, PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;

        $this->formatHostUsing = function ($root, $route = null) {
            if ($this->disableHost) {
                return '';
            }

            return $root;
        };

        $this->formatPathUsing = function ($path, $route = null) {
            if (!$this->disableRouterPrefix) {
                $prefix = $this->portalPhp->config('routerPrefix', '', true);

                if (!empty($prefix) && !Str::startsWith(ltrim($path, '/'), $prefix)) {
                    $path = '/' . $prefix . '/' . ltrim($path, '/');
                }
            }

            return $path;
        };

        parent::__construct($url->routes, $url->request);
    }

    /**
     * Generate the URL to an application asset.
     * @see \Illuminate\Routing\UrlGenerator::asset For the $path and $secure params
     *
     * @param  string  $path
     * @param  bool|null  $secure
     * @return string
     */
    public function asset($path, $secure = null)
    {
        if ($this->formatPathUsing) {
            $path = call_user_func($this->formatPathUsing, $path);
        }

        return parent::asset($path, $secure);
    }

    /**
     * Generate an absolute to root asset path for the application.
     * @see \Illuminate\Routing\UrlGenerator::asset For the $path and $secure params
     *
     * @param $path
     * @param null $secure
     * @param bool $publishModeAware If true and the CMS is not in publish mode, remove the routerPrefix from the path
     * @return string
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    public function assetRel($path, $secure = null, $publishModeAware = false)
    {
        $routerPrefixState = $this->disableRouterPrefix;

        if ($publishModeAware && $this->portalPhp->renderMode() !== RenderMode::PUBLISH) {
            $this->disableRouterPrefix = true;
        }

        $assetRoot = $this->assetRoot;
        $this->assetRoot = '/';

        $assetUrl = '/' . ltrim($this->asset($path, $secure), '/');

        $this->assetRoot = $assetRoot;
        $this->disableRouterPrefix = $routerPrefixState;


        return $assetUrl;
    }

    /**
     * Generate an absolute to root url for the application.
     * @see \Illuminate\Routing\UrlGenerator::to For the $path, $parameters and $secure params
     *
     * @param $path
     * @param array $extra
     * @param null $secure
     * @return string
     */
    public function toRel($path, $extra = [], $secure = null)
    {
        $hostState = $this->disableHost;
        $this->disableHost = true;

        $to = '/' . ltrim(parent::to($path, $extra, $secure), '/');

        $this->disableHost = $hostState;

        return $to;
    }
}
