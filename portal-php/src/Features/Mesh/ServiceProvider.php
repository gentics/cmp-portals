<?php

namespace Gentics\PortalPhp\Features\Mesh;

use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;

/**
 * Class ServiceProvider
 * @package Gentics\PortalPhp\Features\Mesh
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
        $this->publishes([
            __DIR__ . '/../../../resources/graphql/loadByPath.graphql'
                            => resource_path('graphql/loadByPath.graphql'),
            __DIR__ . '/../../../resources/graphql/loadContent.graphql'
                            => resource_path('graphql/loadContent.graphql'),
            __DIR__ . '/../../../resources/graphql/loadNavigation.graphql'
                            => resource_path('graphql/loadNavigation.graphql'),
            __DIR__ . '/../../../resources/graphql/fragments/'
                            => resource_path('graphql/fragments'),
        ], 'gpphp-graphql');
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        $this->app->bind(GraphqlLoader::class, function () {
            return new GraphqlLoader();
        });

        $this->app->bind(GraphqlRequest::class, function ($app) {
            return new GraphqlRequest($app[PortalPhp::class]);
        });
    }
}
