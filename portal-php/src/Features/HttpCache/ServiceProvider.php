<?php

namespace Gentics\PortalPhp\Features\HttpCache;

use Gentics\PortalPhp\Features\HttpCache\Http\Middleware\SetTtl;
use Gentics\PortalPhp\Features\HttpCache\Http\Middleware\SetUserContextVaryHeader;

use Barryvdh\StackMiddleware\StackMiddleware;

use Symfony\Component\HttpKernel\HttpCache\Store;
use Symfony\Component\HttpKernel\HttpCache\StoreInterface;

use Symfony\Component\HttpFoundation\Request as SymfonyRequest;

use FOS\HttpCache\SymfonyCache\CacheInvalidation as FOSCacheInvalidation;

use Illuminate\Contracts\Http\Kernel;
use Illuminate\Http\Request;
use Illuminate\Support\ServiceProvider as BaseServiceProvider;

class ServiceProvider extends BaseServiceProvider
{
    /**
     * Indicates if loading of the provider is deferred.
     *
     * @var bool
     */
    protected $defer = false;

    /**
     * Register the service provider.
     *
     * @return void
     */
    public function register()
    {
        $app = $this->app;

        $app['portal.cache.http.userContextHeader'] = 'X-GTX-User-Context-Hash';

        $app['portal.cache.http.options'] = array_replace(
            [
                'debug' => $app['config']->get('app.debug', false),
                'default_ttl' => $app['config']->get('portal.cache.http.cacheTime', 0),
                'stale_while_revalidate' => 20,
            ],
            $app['config']->get('portal.cache.http.options', [])
        );

        $app['http_cache.cache_dir'] = storage_path('framework/cache/httpcache');

        $app->singleton(Store::class, function ($app) {
            return new Store($app['http_cache.cache_dir']);
        });
        $app->alias(Store::class, StoreInterface::class);

        $app->alias(FOSCacheInvalidation::class, CacheInvalidation::class);

        // Bind Symfony Request to Laravel Request
        SymfonyRequest::setFactory(function (
            array $query = [],
            array $request = [],
            array $attributes = [],
            array $cookies = [],
            array $files = [],
            array $server = [],
            $content = null
        ) {
            return new Request($query, $request, $attributes, $cookies, $files, $server, $content);
        });

        $app->bind('command.httpcache.clear', function ($app) {
            return new Console\ClearCommand($app['files']);
        });
        $this->commands('command.httpcache.clear');
    }

    public function boot()
    {
        $this->loadRoutesFrom(realpath(__DIR__ . '/Http/routes.php'));

        $stack = $this->app->make(StackMiddleware::class);

        $stack->bind(
            AppCache::class,
            function ($app) {
                if (! $this->app['config']->get('portal.cache.http.enabled')) {
                    return $app;
                }

                return new AppCache(
                    $app,
                    $this->app->make(StoreInterface::class),
                    null,
                    $this->app['portal.cache.http.options']
                );
            }
        );

        $this->app[Kernel::class]
            ->pushMiddleware(AppCache::class);

        $this->app[Kernel::class]
            ->pushMiddleware(SetUserContextVaryHeader::class);

        $this->app[Kernel::class]
            ->pushMiddleware(SetTtl::class);

        $this->app[Kernel::class]->
                prependToMiddlewarePriority(CacheRequests::class);

        $this->app[Kernel::class]->
                appendToMiddlewarePriority(SetUserContextVaryHeader::class);

        $this->app[Kernel::class]->
                appendToMiddlewarePriority(SetTtl::class);
    }

    /**
     * Get the services provided by the provider.
     *
     * @return array
     */
    public function provides()
    {
        return ['portal.cache.http.options', 'command.httpcache.clear'];
    }
}
