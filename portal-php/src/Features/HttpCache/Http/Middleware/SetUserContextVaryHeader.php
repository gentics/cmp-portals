<?php

namespace Gentics\PortalPhp\Features\HttpCache\Http\Middleware;

use Closure;
use Illuminate\Http\Response;

class SetUserContextVaryHeader
{
    /**
     * Set the User-Context Vary header for incoming request
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure  $next
     * @param  int $seconds
     * @return mixed
     */
    public function handle($request, Closure $next, $seconds = null)
    {
        $response = $next($request);

        if ($response instanceof Response) {
            $app = app();
            $response->headers->set('Vary', $app['portal.cache.http.userContextHeader']);
        }

        return $response;
    }
}
