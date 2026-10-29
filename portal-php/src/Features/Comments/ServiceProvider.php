<?php

namespace Gentics\PortalPhp\Features\Comments;

use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;

/**
 * Class ServiceProvider
 * @package Gentics\PortalPhp\Features\Comments
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
