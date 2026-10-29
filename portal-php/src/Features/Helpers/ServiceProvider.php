<?php

namespace Gentics\PortalPhp\Features\Helpers;

use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Illuminate\Foundation\AliasLoader;

class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Should be extended if new Helper classes are added to Portal | php
     * @var array Default helpers to load
     */
    protected $helperClasses = [
        BreadcrumbHelper::class,
        NavigationHelper::class
    ];

    /**
     * Bootstrap any application services.
     *
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @return void
     */
    public function boot()
    {
        $this->addAliases();

        foreach ($this->helperClasses as $helperClass) {
            $this->app->make($helperClass);
        }

        $this->addPortalBaseUrlHelper();
    }

    /**
     * Adds the "portalBaseUrl" helper, returning the absolute base URL of the
     * portal (portal.baseUrl config, falling back to config('app.url')).
     *
     * @return void
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @throws \Throwable
     */
    protected function addPortalBaseUrlHelper()
    {
        Helper::add('portalBaseUrl', static function () {
            return rtrim(config('app.url'), '/');
        });
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        foreach ($this->helperClasses as $helperClass) {
            $this->app->singleton($helperClass, $helperClass);
        }
    }

    /**
     * Add aliases.
     *
     * @return void
     */
    public function addAliases()
    {
        $loader = AliasLoader::getInstance();
        $loader->alias('Helper', Helper::class);
    }
}
