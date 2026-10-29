<?php
namespace Gentics\PortalPhp\Features\PortalBlade;

use Gentics\PortalPhp\Features\Keycloak\ServiceProvider as AuthenticationService;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\RenderMode;
use Illuminate\Contracts\Container\BindingResolutionException;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Blade;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Gentics\PortalPhp\Features\Mesh\GraphqlLoader;

/**
 * Class ServiceProvider
 * @package Gentics\PortalPhp\Features\PortalBlade
 */
class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * @var null|ProjectRoot $projectRoot
     */
    protected $projectRoot = null;

    protected function overrideConfigFrom(string $path, string $key)
    {
        $config = $this->app['config']->get($key, []);

        $this->app['config']->set($key, array_merge($config, require $path));
    }

    /**
     * Bootstrap any application services.
     *
     * @throws \Throwable
     * @return void
     */
    public function boot()
    {
        if (!app()->runningInConsole()) {
            $projectRoot = $this->projectRoot->getProjectRoot();

            if (config('portal.routerPrefix', '') === '' && $this->projectRoot->getProjectMode() === 'folder') {
                config(['portal.routerPrefix' => $this->projectRoot->getProjectName()]);
            }

            $this->app->booted(function () use ($projectRoot) {
                $this->loadViewsFrom($projectRoot . '/resources/views/', 'project');
                $this->loadRoutesFrom($projectRoot . '/routes/web.php');

                $loader = require base_path('vendor/autoload.php');
                $loader->addPsr4('Project\\', $projectRoot);
            });
        }
    }

    /**
     * Register any application services.
     *
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @throws \Throwable
     * @return void
     */
    public function register()
    {
        $this->app->bind(ProjectRoot::class, function () {
            return new ProjectRoot();
        });

        $this->app->bind(GraphqlLoader::class, function () {
            return new ProjectGraphqlLoader();
        });

        $url = $this->app['url'];
        $this->app->singleton('url', function ($app) use ($url) {
            return new ProjectUrlGenerator($url, $app[PortalPhp::class]);
        });

        $this->projectRoot = $this->app->make(ProjectRoot::class);

        $projectRoot = $this->projectRoot->getProjectRoot();

        $allowedConfigs = [
            'app.php',
            'logging.php',
            'portal.php',
            'project.php'
        ];

        // Merge all configs
        foreach ($allowedConfigs as $configFile) {
            if (file_exists($projectRoot . '/config/' . $configFile)) {
                $this->overrideConfigFrom(
                    $projectRoot . '/config/' . $configFile,
                    pathinfo($configFile, PATHINFO_FILENAME)
                );
            }
        }

        // Initialize auth if enabled
        $this->initAuth();
    }

    /**
     * @throws BindingResolutionException
     */
    protected function initAuth()
    {
        // Load Authentication service when configuration exists for that
        if (!$this->app->bound(AuthenticationService::class) && $this->isFeatureEnabled('authentication')) {
            $this->app->register(AuthenticationService::class);
        }
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
