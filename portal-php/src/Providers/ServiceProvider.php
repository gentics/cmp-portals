<?php

namespace Gentics\PortalPhp\Providers;

use Gentics\PortalPhp\Console\Commands\GenerateOpenApiDocs;
use Gentics\PortalPhp\Features\Blade\Directives;
use Gentics\PortalPhp\Features\Keycloak\ServiceProvider as AuthenticationService;
use Gentics\PortalPhp\Features\Sitemap\ServiceProvider as SitemapService;
use Gentics\PortalPhp\Features\Redirect\ServiceProvider as RedirectService;
use Gentics\PortalPhp\Features\Likes\ServiceProvider as LikesService;
use Gentics\PortalPhp\Features\Comments\ServiceProvider as CommentsService;
use Gentics\PortalPhp\Features\FormGenerator\ServiceProvider as FormGeneratorService;
use Gentics\PortalPhp\Features\ErrorHandler\ServiceProvider as ErrorHandlerService;
use Gentics\PortalPhp\Features\Helpers\ServiceProvider as HelperService;
use Gentics\PortalPhp\Features\HttpCache\ServiceProvider as HttpCacheService;
use Gentics\PortalPhp\Features\Mesh\ServiceProvider as MeshService;
use Gentics\PortalPhp\Features\PortalVersion\ServiceProvider as PortalVersionService;
use Gentics\PortalPhp\Features\Router\ServiceProvider as RouterServiceProvider;
use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Features\GenticsImageStore\GenticsImageStoreHandler;
use Gentics\PortalPhp\Features\Search\ServiceProvider as SearchService;
use Gentics\PortalPhp\Middleware\GenticsHttpHeaders;
use Gentics\PortalPhp\Middleware\Debugger as DebuggerMiddleware;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\RenderMode;
use Illuminate\Contracts\Container\BindingResolutionException;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Blade;
use Illuminate\Foundation\AliasLoader;
use Illuminate\Support\Facades\View;
use Illuminate\Contracts\Http\Kernel;

class ServiceProvider extends \Illuminate\Support\ServiceProvider
{
    /**
     * Bootstrap services.
     *
     * @return void
     * @throws BindingResolutionException
     */
    public function boot()
    {
        $this->addAliases();

        $this->publishes([
            __DIR__ . '/../../config/portal.php' => config_path('portal.php'),
        ], 'gpphp-config');

        $this->publishes([
            __DIR__ . '/../../config/project.php' => config_path('project.php'),
        ], 'gpe-config');

        $this->app->booted(function () {
            $this->loadRoutesFrom(__DIR__ . '/../../routes/web.php');
            $this->loadRoutesFrom(__DIR__ . '/../../routes/api.php');

            if ($this->app->runningInConsole()) {
                $this->loadRoutesFrom(__DIR__ . '/../../routes/console.php');
                $this->commands([GenerateOpenApiDocs::class]);
            }
        });

        $this->addBladeDirectives();
        $this->addMiddlewares();
        $this->addViewVariables();
    }

    /**
     * Register services.
     *
     * @return void
     * @throws BindingResolutionException
     */
    public function register()
    {
        $this->mergeConfigFrom(
            __DIR__ . '/../../config/portal.php',
            'portal'
        );

        $this->mergeConfigFrom(
            __DIR__ . '/../../config/project.php',
            'project'
        );

        $this->app->register(RouterServiceProvider::class);

        $this->app->singleton(PortalPhp::class, function () {
            return new PortalPhp();
        });

        $this->app->register(ErrorHandlerService::class);
        $this->app->register(MeshService::class);

        $this->app->register(SearchService::class);

        $this->app->singleton(GenticsImageStoreHandler::class, function ($app) {
            return new GenticsImageStoreHandler($app[PortalPhp::class]);
        });

        $this->app->singleton(DataProvider::class, function ($app) {
            return new DataProvider($app[PortalPhp::class]);
        });

        $this->app->register(RedirectService::class);

        // Enable sitemap by default
        if (!$this->app['config']->get('portal.sitemap.disable', false)) {
            $this->app->register(SitemapService::class);
        }

        // Load Authentication service when configuration exists for that
        if ($this->isFeatureEnabled('authentication')) {
            $this->app->register(AuthenticationService::class);
        }

        // Load HttpCache service when enabled in configuration
        if ($this->app['config']->get('portal.cache.http.enabled')) {
            $this->app->register(HttpCacheService::class);
        }

        $this->app->register(LikesService::class);
        $this->app->register(CommentsService::class);
        $this->app->register(FormGeneratorService::class);
        $this->app->register(HelperService::class);
        $this->app->register(PortalVersionService::class);
    }

    /**
     * Add aliases.
     *
     * @return void
     */
    public function addAliases()
    {
        $loader = AliasLoader::getInstance();
        $loader->alias('DataProvider', DataProvider::class);
        $loader->alias(\Knuckles\Scribe\Writing\Writer::class, \Gentics\PortalPhp\Helpers\Writer::class);
    }

    /**
     * Add Blade directives
     *
     * @return void
     */
    public function addBladeDirectives()
    {
        // Add renderMode if directive
        Blade::if('renderMode', function (string $renderMode): bool {
            $portalPhp = $this->app->make(PortalPhp::class);
            return $portalPhp->renderMode() === RenderMode::getValue(strtoupper($renderMode));
        });

        Blade::directive('eval', \Closure::fromCallable([Directives::class, 'evalDirective']));
    }

    /**
     * Add Middlewares
     *
     * @return void
     */
    public function addMiddlewares()
    {
        $kernel = $this->app->make(Kernel::class);
        $kernel->pushMiddleware(GenticsHttpHeaders::class);
        $kernel->pushMiddleware(DebuggerMiddleware::class);
    }

    /**
     * Add View Variables
     *
     * @return void
     */
    public function addViewVariables()
    {
        /**
         * Add global $currentUser variable to templates
         */
        View::composer('*', function ($view) {
            if ($this->isFeatureEnabled('authentication')) {
                $view->with('currentUser', Auth::user());
            } else {
                $view->with('currentUser', null);
            }
        });
    }

    /**
     * A feature is enabled, when configuration key exists.
     * @param $feature
     * @return mixed
     * @throws BindingResolutionException
     */
    public function isFeatureEnabled($feature)
    {
        $portalPhp = $this->app->make(PortalPhp::class);
        return $portalPhp->isFeatureEnabled($feature);
    }
}
