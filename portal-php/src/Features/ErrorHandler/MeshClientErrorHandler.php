<?php

namespace Gentics\PortalPhp\Features\ErrorHandler;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;
use Symfony\Component\HttpFoundation\Response;

/**
 * Class MeshClientErrorHandler
 * @package Gentics\PortalPhp\Features\ErrorHandler
 */
class MeshClientErrorHandler
{

    /**
     * @const string MIME type for JSON request/response
     */
    const JSON_MIME_TYPE = 'application/json';

    /**
     * @return \GuzzleHttp\HandlerStack
     */
    public function createErrorHandlerStack()
    {
        // Add Request and Response Guzzle middlewares to handle errors
        $stack = \GuzzleHttp\HandlerStack::create();
        $stack->unshift(
            $this->createRequestErrorHandlerMiddleware()
        );
        $stack->unshift(
            $this->createResponseErrorHandlerMiddleware()
        );
        return $stack;
    }

    /**
     * @param array $errors
     * @param ResponseInterface|null $response
     * @throws Exceptions\Mesh\DataFetchingException
     * @throws Exceptions\Mesh\GenericGraphqlException
     * @throws Exceptions\Mesh\PermissionException
     */
    private function handleGraphqlErrors(array $errors, ResponseInterface $response = null)
    {
        $error = current($errors);

        switch ($error['type']) {
            case 'DataFetchingException':
                throw new Exceptions\Mesh\DataFetchingException(
                    $error,
                    $response,
                    null,
                    $errors,
                    Response::HTTP_NOT_FOUND
                );
                break;
            case 'missing_perm':
                throw new Exceptions\Mesh\PermissionException(
                    $error,
                    $response,
                    null,
                    $errors,
                    Response::HTTP_FORBIDDEN
                );
                break;
            default:
        }

        throw new Exceptions\Mesh\GenericGraphqlException(
            $error,
            $response,
            null,
            $errors,
            $response->getStatusCode()
        );
    }

    /**
     * @param array $error
     * @param ResponseInterface|null $response
     * @throws Exceptions\Mesh\GenericRestException
     * @throws Exceptions\Mesh\NotFoundException
     * @throws Exceptions\Mesh\PermissionException
     */
    private function handleRestError(array $error, ResponseInterface $response = null)
    {
        switch ($response->getStatusCode()) {
            case 404:
                throw new Exceptions\Mesh\NotFoundException(
                    $error,
                    $response,
                    null,
                    [$error],
                    Response::HTTP_NOT_FOUND
                );
                break;
            case 403:
                throw new Exceptions\Mesh\PermissionException(
                    $error,
                    $response,
                    null,
                    [$error],
                    Response::HTTP_FORBIDDEN
                );
                break;
            default:
        }

        throw new Exceptions\Mesh\GenericRestException(
            $error,
            $response,
            null,
            [$error],
            $response->getStatusCode()
        );
    }

    /**
     * @return callable
     */
    private function createResponseErrorHandlerMiddleware()
    {
        return \GuzzleHttp\Middleware::mapResponse(function (ResponseInterface $response) {
            // Workaround for non-seekable streams
            if (!$response->getBody()->isSeekable()) {
                $response = $response->withBody(new \GuzzleHttp\Psr7\CachingStream($response->getBody()));
            }
            $position = $response->getBody()->tell();
            $mimeType = trim(current(explode(";", $response->getHeaderLine('Content-Type'))));
            
            if ($mimeType === self::JSON_MIME_TYPE) {
                if (config('portal.debugLogOptions.meshResponse', false)) {
                    \Log::debug(
                        sprintf("[MESH] RESPONSE %s %s %s", $mimeType, $response->getStatusCode(), $response->getBody())
                    );
                    $response->getBody()->seek($position);
                }
                $json = json_decode($response->getBody(), true);

                if (isset($json['errors'])) {
                    $this->handleGraphqlErrors($json['errors'], $response);
                } elseif (isset($json['message']) && $response->getStatusCode() >= Response::HTTP_BAD_REQUEST) {
                    $this->handleRestError($json, $response);
                }
            }

            // Seek to original position to avoid effects of logging
            $response->getBody()->seek($position);
            return $response;
        });
    }

    /**
     * @return callable
     */
    private function createRequestErrorHandlerMiddleware()
    {
        return \GuzzleHttp\Middleware::mapRequest(function (RequestInterface $request) {
            if (config('portal.debugLogOptions.meshRequest', false)) {
                \Log::debug(sprintf("[MESH] REQUEST %s %s", $request->getMethod(), $request->getUri()));
            }
            return $request;
        });
    }
}
