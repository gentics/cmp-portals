<?php

namespace Gentics\PortalPhp\Features\Redirect;

use Gentics\PortalPhp\Features\ErrorHandler\CollisionExceptionHandler;
use Gentics\PortalPhp\Features\ErrorHandler\Handler;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use \Illuminate\Routing\Router;

/**
 * Class ServiceProvider
 * @package Gentics\PortalPhp\Features\Redirect
 */
class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Bootstrap any application services.
     *
     * @param Router $router
     * @return void
     */
    public function boot(Router $router)
    {
        // Register RedirectHandler as exception handler
        $exceptionHandler = $this->app['Illuminate\Contracts\Debug\ExceptionHandler'];

        if ($exceptionHandler instanceof CollisionExceptionHandler) {
            $exceptionHandler = $exceptionHandler->exceptionHandler();
        }
        if ($exceptionHandler instanceof Handler) {
            $exceptionHandler->pushErrorHandler(RedirectHandler::class);
        }
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
    }
}
