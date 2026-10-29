<?php
// Group by router prefix if exists
Route::namespace('\\Gentics\\PortalPhp\\Features\\Sitemap\\Http\\Controllers')
    ->prefix(routerPrefix())
    ->group(function () {
        Route::get(
            'sitemap.xml',
            'SitemapController@index'
        )->priority(50);
    });
