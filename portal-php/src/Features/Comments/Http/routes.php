<?php

// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
        Route::namespace('\\Gentics\\PortalPhp\\Features\\Comments\\Http\\Controllers')->group(function () {
            Route::any(
                '/api/comment/{any?}',
                'CommentsController@index'
            )
            ->where(['any' => '.*'])
            ->priority(50)
            ->bypassCache();
        });
    });
