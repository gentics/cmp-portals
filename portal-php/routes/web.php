<?php

// The app must have full control over routes and be able to override any routes defined here below.
// Use 50 priority for built in routes and lower for wildcard paths which may need override.

// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {

        Route::namespace('\\Gentics\\PortalPhp\\Http\\Controllers')
            ->group(function () {

            Route::prefix('GenticsImageStore')
                ->name('genticsimagestore.')
                ->group(function () {

                    Route::get('{width}/{height}/{mode}/{path}', 'ContentController@genticsImageStore')
                        ->where([
                            'width' => '[0-9]+|auto',
                            'height' => '[0-9]+|auto',
                            'mode' => '^(?!cropandresize)([a-zA-Z]+)$',
                            'path' => '.*'
                        ])
                        ->name('resize')
                        ->priority(50);

                    Route::get(
                        '{width}/{height}/cropandresize/{mode}/{topleft_x}/{topleft_y}/{cropwidth}/{cropheight}/{path}',
                        'ContentController@genticsImageStore')
                        ->where([
                            'width' => '[0-9]+|auto',
                            'height' => '[0-9]+|auto',
                            'mode' => '[a-zA-Z]+',
                            'topleft_x' => '[0-9]+',
                            'topleft_y' => '[0-9]+',
                            'cropwidth' => '[0-9]+',
                            'cropheight' => '[0-9]+',
                            'path' => '.*'
                        ])
                        ->name('cropandresize')
                        ->priority(50);

                });
        });
});

// Mesh content does not need to be grouped, since it handles the segmentation
Route::namespace('\\Gentics\\PortalPhp\\Http\\Controllers')
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
    Route::any('{path}', 'ContentController@index')
        ->where('path', '.*')
        ->name('content')
        ->priority(0);
});