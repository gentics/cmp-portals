<?php
// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
        Route::namespace('\\Gentics\\PortalPhp\\Features\\Keycloak\\Http\\Controllers')->group(function () {
            Route::get(
                '/auth/redirect',
                'AuthController@redirect'
            )
            ->name('login')
            ->priority(50)
            ->bypassCache();

            Route::get(
                '/auth/logout',
                'AuthController@logout'
            )
            ->name('logout')
            ->priority(50)
            ->bypassCache();

            Route::get(
                '/auth/register',
                'AuthController@register'
            )
            ->name('register')
            ->priority(50)
            ->bypassCache();

            Route::get(
                '/auth/callback',
                'AuthController@callback'
            )
            ->priority(50)
            ->bypassCache();
        });
    });
