<?php

namespace Gentics\PortalPhp\Middleware;

use Closure;
use Illuminate\Http\Response;

class GenticsHttpHeaders
{
    /**
     * Handle an incoming request.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure  $next
     * @return mixed
     */
    public function handle($request, Closure $next)
    {
        $response = $next($request);

        if ($response instanceof Response) {
            $response->headers->set('X-Powered-By', 'gentics.com');
        }

        return $response;
    }
}
