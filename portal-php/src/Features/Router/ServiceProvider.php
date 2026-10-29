<?php

namespace Gentics\PortalPhp\Features\Router;

use Illuminate\Routing\Route as RouteIlluminate;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;

class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Bootstrap any application services.
     *
     * @return void
     */
    public function boot()
    {
        $this->app['router']->setRoutes(new RouteCollection);
        $this->app['router']->middlewareGroup('portal', []);

        RouteIlluminate::macro('priority', function ($priority = RouterPriority::DEFAULT) {
            $this->action['priority'] = $priority;
            return $this;
        });

        RouteIlluminate::macro('getPriority', function () {
            return isset($this->action['priority']) ? $this->action['priority'] : RouterPriority::DEFAULT;
        });

        RouteIlluminate::macro('bypassCache', function () {
            if (config('portal.cache.http.enabled', false)) {
                $this->middleware('ttl:0');
            }
            return $this;
        });

        RouteIlluminate::macro('cache', function (int $ttl) {
            if (config('portal.cache.http.enabled', false)) {
                $this->middleware('ttl:' . $ttl);
            }
            return $this;
        });
    }
}
