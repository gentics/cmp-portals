<?php
namespace Gentics\PortalPhp\Features\Mesh;

use Psr\Http\Message\StreamInterface;
use GuzzleHttp\Psr7\StreamWrapper;
use Illuminate\Http\Response as ResponseIlluminate;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Features\HttpCache\AppCache;

class BinaryResponse extends Response
{
    /**
     * @param array $additionalHttpHeaders
     * @return \Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     */
    public function createResponse($additionalHttpHeaders = [])
    {
        $this->cacheHeaderForEtag();
        $httpHeaders = array_merge($this->getFilteredResponseHeaders(), $additionalHttpHeaders);

        return $this->streamGuzzleStream(
            $this->meshResponse->getBody(),
            $httpHeaders,
            $this->meshResponse->getStatusCode()
        );
    }

    /**
     * @param StreamInterface $stream
     * @param array $httpHeaders
     * @param int $statusCode
     * @return \Symfony\Component\HttpFoundation\StreamedResponse
     */
    public function streamGuzzleStream(
        StreamInterface $stream,
        array $httpHeaders = [],
        int $statusCode = ResponseIlluminate::HTTP_OK
    ) {
        AppCache::bypassWhen('binary');

        PortalPhp::devLog(
            sprintf(
                '[MESH-BINARY] Streaming binary file - Status Code: %s',
                $statusCode
            )
        );

        return response()->stream(function () use ($stream) {
            while (ob_get_level() > 0) {
                ob_end_flush();
            }

            $resource = StreamWrapper::getResource($stream);
            fpassthru($resource);
        }, $statusCode, $httpHeaders);
    }
}
