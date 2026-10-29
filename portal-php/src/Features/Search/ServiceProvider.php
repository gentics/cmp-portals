<?php

namespace Gentics\PortalPhp\Features\Search;

use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;

class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Bootstrap any application services.
     *
     * @return void
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    public function boot()
    {
        $this->loadRoutesFrom(realpath(__DIR__ . '/Http/routes.php'));

        $this->app->make(SearchHelper::class);
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        $this->app->singleton(SearchHelper::class, SearchHelper::class);

        $this->app->singleton(SearchHandler::class, function ($app) {
            // Load legacy search handler, if elasticSearch configuration enabled
            if ($this->app[PortalPhp::class]->isFeatureEnabled('elasticSearch')) {
                return new SearchHandlerLegacy($app[PortalPhp::class]);
            } else {
                return new SearchHandlerMeshPlugin($app[PortalPhp::class]);
            }
        });
    }
}
