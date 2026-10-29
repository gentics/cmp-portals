<?php
// Group by router prefix if exists
Route::prefix(routerPrefix())
    // Add routes to the portal middleware group
    ->middleware('portal')
    ->group(function () {
        Route::namespace('\\Gentics\\PortalPhp\\Features\\FormGenerator\\Http\\Controllers')->group(function () {
            Route::pattern('fg_formUuid', '[^/]*');
            Route::pattern('fg_language', '[^/]*');
            Route::pattern('fg_meshUuid', '[^/]*');
            Route::pattern('fg_index', '[\d]+');
            Route::pattern('forms3rdGenFunction', 'save|submit|load|validate|cancel');

            // add handler for 3rd gen forms functions
            Route::post(
                '/api/forms/form/{fg_formUuid}/{forms3rdGenFunction}',
                'FormGeneratorController3rdGen@manageForm'
            )
            ->priority(50)
            ->bypassCache();

            // add handler for getting binary thumbnails
            Route::get(
                '/api/forms/form/{fg_formUuid}/binary/{fg_meshUuid}',
                'FormGeneratorController3rdGen@binary'
            )
            ->priority(50)
            ->bypassCache();

            // add handler for getting multibinary thumbnails
            Route::get(
                '/api/forms/form/{fg_formUuid}/binary/{fg_meshUuid}/{fg_index}',
                'FormGeneratorController3rdGen@multibinary'
            )
            ->priority(50)
            ->bypassCache();

            // add handler for getting translations
            Route::get(
                '/api/forms/translations/{fg_formUuid}/{fg_language}',
                'FormGeneratorController3rdGen@translations'
            )
            ->priority(50)
            ->bypassCache();
        });
    });
