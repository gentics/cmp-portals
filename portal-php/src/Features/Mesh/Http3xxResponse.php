<?php
namespace Gentics\PortalPhp\Features\Mesh;

use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Features\HttpCache\AppCache;

class Http3xxResponse extends Response
{
    /**
     * Creates a Laravel response with  the HTTP status code and the whitelisted HTTP
     * headers of the mesh response.
     * @return \Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     */
    public function createResponse()
    {
        AppCache::bypassWhen('response3xx');

        if (!$this->meshResponse->hasHeader(Mesh::ETAG_CACHE_MARKER_HEADER)) {
            PortalPhp::devLog('[ETAG-CACHE] 3xx Response received, with etag. Store in cache');
            $this->cacheHeaderForEtag();
        }

        return response('', $this->meshResponse->getStatusCode(), $this->getFilteredResponseHeaders());
    }
}
