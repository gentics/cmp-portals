<?php

// Group by router prefix if exists
Route::prefix(routerPrefix())->group(function () {
    Route::namespace('\\Gentics\\PortalPhp\\Http\\Controllers')
        ->name('api.')
        ->prefix('api')
        ->group(function () {

        // Basic health check API
        Route::prefix('health')->name('health.')->group(function() {
            $liveRoute = Route::get("live", 'HealthController@live')
                ->name('live')
                ->priority(50)
                ->bypassCache();

            $readyRoute = Route::get("ready", 'HealthController@ready')
                ->name('ready')
                ->priority(50)
                ->bypassCache();
        });

        // Maintenance API
        Route::prefix('maintenance')->name('maintenance.')->group(function() {
            $clearCacheRoute = Route::get("clearCache", 'MaintenanceController@clearCache')
                ->name('clearCache')
                ->priority(50)
                ->bypassCache();

            $clearHttpCacheRoute = Route::get("clearHttpCache", 'MaintenanceController@clearHttpCache')
                ->name('clearHttpCache')
                ->priority(50)
                ->bypassCache();
        });

        Route::post('preview/{folderPath?}', 'CmsController@index')
            ->where(['folderPath' => '.*'])
            ->name('preview')
            ->priority(50)
            ->bypassCache();

        if (config('portal.openApi.enable')) {
            // OpenAPI
            Route::get('openapi.yaml', 'OpenApiController@generateYaml')
	        ->name('openapi.yaml')
                ->priority(50)
                ->bypassCache();
            }
    });
});
