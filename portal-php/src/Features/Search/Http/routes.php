<?php
// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
        Route::namespace('\\Gentics\\PortalPhp\\Features\\Search\\Http\\Controllers')->group(function () {
            Route::any(
                '/api/search/{any?}',
                'SearchController@api'
            )
            ->where(['any' => '.*'])
            ->priority(50)
            ->bypassCache();

            Route::prefix('search')->group(function () {
                Route::any('/', ['as' => 'search', 'uses' => 'SearchController@index'])
                ->priority(50);
            });
        });
    });
