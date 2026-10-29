<?php

namespace Gentics\PortalPhp\Http\Controllers;

use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Storage;

class OpenApiController extends \Gentics\PortalPhp\Http\Controllers\ContentController
{
    /**
     * Generates an OpenAPI v3.0 specification for the current API implementation of Portal | php
     * @response <<binary>> YAML file
     */
    public function generateYaml()
    {
        Artisan::call('scribe:generate --force --scribe-dir=/portal/bootstrap/cache/.scribe');
        return response()->file('/portal/bootstrap/cache/scribe/docs/openapi.yaml');
    }
}
