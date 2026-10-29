<?php

namespace Gentics\PortalPhp\Http\Controllers;

use Illuminate\Support\Arr;

/*
 * @group Public API
 */
class MaintenanceController extends BaseController
{
    /**
     * Authorize request with debugger key
     */
    protected function authorizedRequest()
    {
        $confKey = config('portal.debuggerKey', null);
        $reqKey = request()->get('authKey', null);

        if (is_string($confKey) &&
            !empty($confKey) &&
            is_string($reqKey) &&
            !empty($reqKey) &&
            $confKey === $reqKey) {
            \Log::debug(
                sprintf("[MAINT] Maintenance request authorized with debugger key")
            );
            return true;
        }

        \Log::debug(
            sprintf("[MAINT] Maintenance request refused (invalid or empty debugger key)")
        );

        return false;
    }

    /**
     * Checks if the Artisan command exists
     */
    protected function commandExists($name)
    {
        return Arr::has(\Artisan::all(), $name);
    }

    /**
     * Clear Cache authorized with the debugger key
     * @group Maintenance
     * @response 200 Laravel Cache cleared
     */
    public function clearCache()
    {
        $command = 'cache:clear';

        \Log::debug(
            sprintf("[MAINT] Laravel Cache Clearing requested")
        );

        if ($this->authorizedRequest()) {
            if ($this->commandExists($command)) {
                \Artisan::call($command);
            }
        }

        return response('', 200);
    }

    /**
     * Clear HTTP Cache authorized with the debugger key
     * @group Maintenance
     * @response 200 HTTP Cache cleared
     */
    public function clearHttpCache()
    {
        $command = 'httpcache:clear';

        \Log::debug(
            sprintf("[MAINT] HTTP Cache Clearing requested")
        );

        if ($this->authorizedRequest()) {
            if ($this->commandExists($command)) {
                \Artisan::call($command);
            }
        }

        return response('', 200);
    }
}
