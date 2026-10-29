<?php

namespace Gentics\PortalPhp\Features\HttpCache\Http\Middleware;

use Closure;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Support\Str;
use GuzzleHttp\Psr7\Response as GuzzleResponse;
use Gentics\PortalPhp\PortalPhp;

class SetTtl
{
    /**
     * Return if the current MIME-type is cacheable.
     *
     * @param Response $response
     * @return boolean
     */
    protected function isMimeTypeCacheable($response)
    {
        $mimeType = $response->headers->get('Content-Type');
        $configMimeTypes = config('portal.cache.http.mimeTypes', []);

        PortalPhp::devLog(
            sprintf(
                '[HTTP-CACHE] Check if the response mime-type (%s) is whitelisted [%s]',
                $mimeType,
                implode(',', $configMimeTypes)
            )
        );

        if (empty($configMimeTypes) || !is_array($configMimeTypes)) {
            return false;
        }

        foreach ($configMimeTypes as $configMimeType) {
            if (Str::startsWith($mimeType, $configMimeType)) {
                return true;
            }
        }
    }

    /**
     * Set the Time-To-Live in seconds on the incoming request
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure  $next
     * @param  int $seconds
     * @return mixed
     */
    public function handle($request, Closure $next, $seconds = null)
    {
        $response = $next($request);

        /**
         * Disable proxy caching when specifically asked for
         */
        if (config('portal.cache.http.__bypassInternal', false)) {
            PortalPhp::devLog('[HTTP-CACHE] Forced cache bypass, return correct headers');
            $response->headers->set('X-GTX-No-Store', 1);
            return $response;
        }

        /**
         * Disable proxy caching when its not allowed specifically by MIME-type
         */
        if (!$this->isMimeTypeCacheable($response)) {
            PortalPhp::devLog('[HTTP-CACHE] MIME-Type is not cacheable, return correct headers');
            $response->headers->set('X-GTX-No-Store', 1);
            return $response;
        }

        if ($response instanceof Response &&
            !($response instanceof StreamedResponse) && // Skip streamed binary responses
            !($response instanceof RedirectResponse) && // Skip redirects
            !($response instanceof GuzzleResponse) && // Skip Guzzle Reponses (proxy)
            $request instanceof Request &&
            $request->isMethodCacheable()) {
            // Get Default TTL
            if (is_null($seconds)) {
                $seconds = config('portal.cache.http.cacheTime', 60);
            }

            if (is_null($response->getTtl())) {
                PortalPhp::devLog(
                    sprintf('[HTTP-CACHE] HTTP Response is cacheable, setting TTL to %s', $seconds)
                );
                $response->setTtl($seconds);
            }
        } else {
            PortalPhp::devLog('[HTTP-CACHE] HTTP Response is not cacheable, passing the original response');
        }

        return $response;
    }
}
