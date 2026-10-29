<?php

namespace Gentics\PortalPhp\Features\ErrorHandler;

use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Illuminate\Contracts\Debug\ExceptionHandler;

/**
 * Class ServiceProvider
 * @package Gentics\PortalPhp\Features\ErrorHandler
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
            __DIR__ . '/../../../resources/graphql/loadError.graphql' => resource_path('graphql/loadError.graphql'),
        ], 'gpphp-graphql');
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        // Register Portal PHP ErrorHandler
        $this->app->singleton(ExceptionHandler::class, Handler::class);

        $this->app->singleton(MeshClientErrorHandler::class, function () {
            return new MeshClientErrorHandler();
        });
    }
}
