<?php

namespace Gentics\PortalPhp\Features\PortalImplementation;

use Gentics\PortalPhp\Features\PortalImplementation\Console\SyncCommand;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Console\Scheduling\Schedule;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;

/**
 * Registers the synchronization of the portal implementation from Mesh.
 */
class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        $this->app->singleton(PortalImplementationService::class, function ($app) {
            return new PortalImplementationService(
                $app[PortalPhp::class],
                [
                    PortalImplementationExtractor::FOLDER_VIEWS => resource_path('views'),
                    PortalImplementationExtractor::FOLDER_GRAPHQL => resource_path('graphql'),
                    PortalImplementationExtractor::FOLDER_LANG => lang_path(),
                    PortalImplementationExtractor::FOLDER_STATIC => public_path('static'),
                ],
                storage_path('app/portal-implementation')
            );
        });
    }

    /**
     * Bootstrap any application services.
     *
     * @return void
     */
    public function boot()
    {
        if ($this->app->runningInConsole()) {
            $this->commands([SyncCommand::class]);
        }

        if (!empty($this->app['config']->get('portal.portalImplementation'))) {
            // Check for modifications of the portal implementation (requires the Laravel scheduler to run)
            $this->callAfterResolving(Schedule::class, function (Schedule $schedule) {
                $schedule->command(SyncCommand::class)->everyMinute()->withoutOverlapping();
            });
        }
    }
}
