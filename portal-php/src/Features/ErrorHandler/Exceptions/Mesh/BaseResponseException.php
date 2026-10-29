<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\MeshException;
use Psr\Http\Message\ResponseInterface;
use Illuminate\Support\Arr;
use Throwable;

/**
 * Class BaseGraphqlException
 * @package Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh
 */
class BaseResponseException extends MeshException
{
    /**
     * BaseGraphqlException constructor.
     * @param array $error
     * @param int $code
     * @param ResponseInterface $response
     * @param Throwable|null $previous
     * @param array $errors
     */
    public function __construct(
        array $error,
        ResponseInterface $response,
        Throwable $previous = null,
        array $errors = [],
        int $code = 0
    ) {
        parent::__construct(Arr::get($error, 'message', ''), $code, $previous, $errors, $response);
    }
}
