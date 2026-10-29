<?php

namespace Gentics\PortalPhp\Middleware;

use Closure;
use Illuminate\Http\Response;

class Debugger
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
        try {
            $confKey = config('portal.debuggerKey', null);
            $reqKey = $request->get('debug', null);

            if (is_string($confKey) &&
                !empty($confKey) &&
                is_string($reqKey) &&
                !empty($reqKey) &&
                $confKey === $reqKey) {
                // Updates the config to the updated value
                app('config')->set('app.debug', true);
                app('config')->set('app.debugForceLog', true);
            }
        } catch (\Exception $e) {
            // Ignore errors
        }

        return $next($request);
    }
}
