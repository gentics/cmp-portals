<?php

use Gentics\PortalPhp\Features\DataProvider\DataProvider;

Artisan::command('DataProvider:prefetch {datasource=all}', function ($datasource) {
    DataProvider::prefetch($datasource);
})->describe('Pre-Fetching DataProvider datasources');

Artisan::command('DataProvider:prefetchBranches', function () {
    DataProvider::prefetchBranches();
})->describe('Pre-Fetching DataProvider branches');
