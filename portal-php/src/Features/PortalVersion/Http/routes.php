<?php
// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
        Route::namespace('\\Gentics\\PortalPhp\\Features\\PortalVersion\\Http\\Controllers')->group(function () {
            Route::get(
                '/api/version',
                'PortalVersionController@getVersion'
            )
            ->priority(50);

            Route::get(
                '/api/preview',
                'PortalVersionController@getVersionPreview'
            )
            ->priority(50);
        });
    });
