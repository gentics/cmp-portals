<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions;

use Psr\Http\Message\ResponseInterface;
use Throwable;

class MeshException extends PortalPhpException
{
    /**
     * @var int The status code to be returned, can be overridden per exception type
     */
    protected const STATUS_CODE = 500;

    /**
     * @var string Mesh response error message
     */
    protected $message;

    /**
     * @var array Mesh response errors array
     */
    protected $errors;

    /**
     * @var null|ResponseInterface MeshResponse object
     */
    protected $response;

    /**
     * MeshException constructor.
     *
     * @param string $message
     * @param int $code
     * @param Throwable|null $previous
     * @param array $errors
     * @param ResponseInterface|null $response
     */
    public function __construct(
        string $message = "",
        int $code = 0,
        Throwable $previous = null,
        array $errors = [],
        ResponseInterface $response = null
    ) {
        $this->message = $message;
        $this->errors = $errors;
        $this->response = $response;
        parent::__construct($message, $code, $previous);
    }

    /**
     * Returns Mesh Response
     *
     * @return null|ResponseInterface Returns the response object from Mesh
     */
    public function getResponse()
    {
        return $this->response;
    }

    /**
     * Returns the status code of Mesh Response
     *
     * @return int Returns 500 if child exception not override the STATUS_CODE const, otherwise depends on child
     */
    public function getStatusCode()
    {
        return static::STATUS_CODE;
    }

    /**
     * Logs the error
     */
    public function report()
    {
        \Log::error(sprintf('[MESH] %s [%s]', $this->message, json_encode(current($this->errors))));
    }
}
