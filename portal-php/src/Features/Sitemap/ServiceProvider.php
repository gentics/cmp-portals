<?php

namespace Gentics\PortalPhp\Features\Sitemap;

use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Spatie\Sitemap\SitemapServiceProvider;

/**
 * Class ServiceProvider
 * @package Gentics\PortalPhp\Features\Sitemap
 */
class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Bootstrap any application services.
     *
     * @return void
     */
    public function boot()
    {
        $this->loadRoutesFrom(realpath(__DIR__ . '/Http/routes.php'));

        $this->publishes([
            __DIR__ . '/../../../resources/graphql/loadAllForSitemap.graphql'
                => resource_path('graphql/loadAllForSitemap.graphql'),
        ], 'gpphp-graphql');
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        $this->app->register(SitemapServiceProvider::class);

        $this->app->singleton(SitemapGenerator::class, function () {
            return new SitemapGenerator();
        });

        $this->app->singleton(SitemapHandler::class, function ($app) {
            return new SitemapHandler($app[PortalPhp::class], $app[SitemapGenerator::class]);
        });
    }
}
