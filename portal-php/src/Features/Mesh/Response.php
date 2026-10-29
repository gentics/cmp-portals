<?php
namespace Gentics\PortalPhp\Features\Mesh;

use Gentics\Mesh\Client\Rest\MeshResponse;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Facades\Cache;
use Symfony\Component\HttpFoundation\Response as HTTPResponse;

abstract class Response
{
    /** @var MeshResponse Raw MeshResponse */
    protected $meshResponse;

    public function __construct(MeshResponse $data)
    {
        $this->meshResponse = $data;
    }

    /**
     * Get the raw MeshResponse
     *
     * @return MeshResponse
     */
    public function getMeshResponse()
    {
        return $this->meshResponse;
    }

    /**
     * Get the headers from the Mesh response filtered by the configured whilte list.
     *
     * @return array The white listed response headers
     */
    protected function getFilteredResponseHeaders()
    {
        $webRootHttpHeadersWhiteList = config('portal.webrootHttpHeaderWhiteList');
        $httpResponseHeadersWhiteList = $webRootHttpHeadersWhiteList['response'] ?? [];

        // Backwards compatibility start
        foreach ($webRootHttpHeadersWhiteList as $key => $value) {
            if (!in_array($key, ['request', 'response'])) {
                $httpResponseHeadersWhiteList[$key] = $value;
            }
        }
        // Backwards compatibility end

        return filterArrayWildcard($this->meshResponse->getHeaders(), $httpResponseHeadersWhiteList);
    }

    /**
     * Caches the headers for an etag response
     */
    protected function cacheHeaderForEtag()
    {
        if ($this->meshResponse->hasHeader('etag') &&
            $this->meshResponse->getStatusCode() === HTTPResponse::HTTP_NOT_MODIFIED
        ) {
            $cacheTimeDefault = 300;
            $cacheTime = (int)config('portal.cache.etag.cacheTime', $cacheTimeDefault);

            $etag = $this->meshResponse->getHeader('etag');
            $httpHeaders = $this->meshResponse->getHeaders();

            Cache::put(Mesh::ETAG_CACHE_KEY_PREFIX . current($etag), $httpHeaders, now()->addSeconds($cacheTime));
        }
    }
}
