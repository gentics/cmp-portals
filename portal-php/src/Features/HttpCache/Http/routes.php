<?php
// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
        Route::namespace('\\Gentics\\PortalPhp\\Features\\HttpCache\\Http\\Controllers')->group(function () {
            Route::get(
                '/_gtx_user_context_hash',
                'UserContextController@hash'
            )
            ->name('usercontext')
            ->priority(50);
        });
    });
