<?php

namespace Gentics\PortalPhp\Http\Controllers;

use GuzzleHttp\Exception\GuzzleException;

/**
 * Class HealthController
 * @group Public API
 * @package Gentics\PortalPhp\Controllers
 */
class HealthController extends BaseController
{
    /**
     * Action to check if the portal is alive.
     * When this action is able to be called, then it is.
     * @group Health
     * @response 200 LIVE
     */
    public function live()
    {
        \Log::debug(
            sprintf("[HEALTH] Liveness check request")
        );
        return response('LIVE', 200);
    }

    /**
     * Action which forwards Mesh health status.
     * @group Health
     * @response 200 READY
     */
    public function ready()
    {
        \Log::debug(
            sprintf("[HEALTH] Readiness check request")
        );

        $mesh = $this->portalPhp->getAdminMeshClient();
        $timeout = $this->portalPhp->config('meshClientHealthCheckTimeout', 2.5, true);

        try {
            $time_start = microtime(true);
            $response = $mesh->healthReady($timeout)->send();
            $time_end = microtime(true);

            \Log::debug(
                sprintf(
                    "[HEALTH] Mesh Readiness response took %s ms with status %s",
                    round(($time_end - $time_start) * 1000),
                    $response->getStatusCode()
                )
            );

            if ($response->getStatusCode() === 200) {
                return response('READY', $response->getStatusCode());
            }
        } catch (GuzzleException $exception) {
            \Log::error(
                sprintf(
                    "[HEALTH] Mesh Readiness check error: %s",
                    $exception->getMessage()
                )
            );
        }

        return response('', 503);
    }
}
