<?php
namespace Gentics\PortalPhp\Features\Mesh;

use Gentics\Mesh\Client\Rest\MeshResponse;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\MeshException;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Str;

use GuzzleHttp\Psr7\Response as GuzzleResponse;
use Illuminate\Support\Facades\Request as LaravelRequest;
use Illuminate\Support\Facades\Cache;

class Mesh
{
    /** @var string Cache key prefix to use for etag headers */
    const ETAG_CACHE_KEY_PREFIX = "mesh:etag_header:";

    /** @var string Cache key prefix to use for etag headers */
    const ETAG_CACHE_MARKER_HEADER = "X-Portal-Etag-Cache";

    /** @var string Binary content field */
    const FIELD_BINARYCONTENT = "binarycontent";

    protected static $defaultRequestClass = GraphqlRequest::class;

    /** @var boolean Tells if Mesh response headers should be cached */
    protected static $cacheHeadersByEtag = true;

    /**
     * Request factory
     *
     * @param null|string $requestClass
     * @return mixed
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @throws MeshException
     */
    public static function request(?string $requestClass = null)
    {
        if (!is_null($requestClass)) {
            return app()->make($requestClass);
        }

        return app()->make(static::$defaultRequestClass);
    }

    /**
     * Response factory
     *
     * @param MeshResponse $response
     * @return Response
     */
    public static function response(MeshResponse $response)
    {
        if (static::isHttp3xxResponse($response)) {
            return new Http3xxResponse($response);
        } elseif (static::isBinary($response)) {
            return new BinaryResponse($response);
        } else {
            return new GraphqlResponse($response);
        }
    }

    /**
     * Tells if the provided response binary or not.
     *
     * @param MeshResponse $response
     * @return bool
     */
    protected static function isBinary(MeshResponse $response)
    {
        return ($response->hasHeader('Webroot-Response-Type')
            && $response->getHeader('Webroot-Response-Type')[0] === 'binary');
    }

    /**
     * Checks if the response has a HTTP status code >= 300 and < 400
     *
     * @param MeshResponse $response
     * @return bool True if 3xx, false otherwise
     */
    protected static function isHttp3xxResponse(MeshResponse $response)
    {
        $statusCode = (string)$response->getStatusCode();
        return Str::startsWith($statusCode, '3');
    }


    /**
     * Checks if the etag caching is enabled
     *
     * @return boolean
     */
    public static function isEtagCachingEnabled()
    {
        return static::$cacheHeadersByEtag;
    }

    /**
     * Replace etag suffixes may attached by frontend servers
     *
     * @return string
     */
    public static function cleanEtagSuffixes($etag)
    {
        $etag = is_array($etag) ? current($etag) : $etag;
        $etag = substr($etag, 1, (strrpos($etag, "-") - 1));
        return '"' . $etag . '"';
    }

    /**
     * Return If-None-Match header
     *
     * @return string
     */
    protected static function getIfNoneMatchHeader()
    {
        $headerToMatch = 'If-None-Match';
        $etag = LaravelRequest::header($headerToMatch);

        // Try to extract raw header when HttpCache used
        if (config('portal.cache.http.enabled')) {
            $rawHeaders = array_change_key_case(getallheaders(), CASE_LOWER);
            if (!$etag && isset($rawHeaders[strtolower($headerToMatch)])) {
                $etag = $rawHeaders[strtolower($headerToMatch)];
            }
        }

        return static::cleanEtagSuffixes($etag);
    }

    /**
     * Checks if the request header has an If-None-Match header and the cache key exists
     *
     * @return boolean
     */
    protected static function hasEtagCache()
    {
        $etag = static::getIfNoneMatchHeader();
        if ($etag) {
            return Cache::has(static::ETAG_CACHE_KEY_PREFIX . $etag);
        } else {
            return false;
        }
    }

    /**
     * Creates a fake MeshResponse with the cached details
     *
     * @return MeshResponse
     */
    protected static function createEtagCachedResponse()
    {
        $etag = static::getIfNoneMatchHeader();
        $cachedHeaders = Cache::get(static::ETAG_CACHE_KEY_PREFIX . $etag);
        $cachedHeaders = array_merge($cachedHeaders, [
            static::ETAG_CACHE_MARKER_HEADER => [1]
        ]);

        PortalPhp::devLog(
            sprintf(
                '[ETAG-CACHE] Requested & Found cached etag "%s", return 304 status',
                $etag
            )
        );

        return new MeshResponse(
            new GuzzleResponse(304, $cachedHeaders)
        );
    }

    /**
     * Tries to serve the request with 304 response by cached etag headers
     *
     * @return MeshResponse|boolean
     */
    public static function tryServeEtagCachedResponse()
    {
        if (static::isEtagCachingEnabled() && static::hasEtagCache()) {
            return static::createEtagCachedResponse();
        }

        return false;
    }

    /**
     * Return path without multiple leading slashes
     *
     * @param string $path
     * @param bool $leadingSlash
     * @return string
     */
    public static function normalizePath(string $path, bool $leadingSlash = true): string
    {
        return ($leadingSlash ? "/" : "") . ltrim($path, "/");
    }
}
