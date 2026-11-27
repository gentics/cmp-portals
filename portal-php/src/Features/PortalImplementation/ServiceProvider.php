<?php

namespace Gentics\PortalPhp\Features\PortalImplementation;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\Features\PortalImplementation\Console\SyncCommand;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Console\Scheduling\Schedule;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Throwable;

/**
 * Registers the synchronization of the portal implementation from Mesh.
 */
class ServiceProvider extends ServiceProviderIlluminate
{
    /** Name of the scheduled synchronization */
    public const SCHEDULE_NAME = 'portal:implementation:sync';

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
            // Check for modifications of the portal implementation (requires the Laravel scheduler to run). This is a
            // callback instead of the command, because the scheduler discards the output of commands (including their
            // log, when logging to stdout/stderr). Exceptions of callbacks are reported (logged) by the scheduler.
            $this->callAfterResolving(Schedule::class, function (Schedule $schedule) {
                $schedule->call(function (PortalImplementationService $service) {
                    try {
                        $service->sync();
                    } catch (Throwable $e) {
                        throw new PortalPhpException(sprintf(
                            'Could not load portal implementation %s, using local files: %s',
                            $service->getKeyword(),
                            $e->getMessage()
                        ), 0, $e);
                    }
                })->name(self::SCHEDULE_NAME)->everyMinute()->withoutOverlapping();
            });
        }
    }
}
