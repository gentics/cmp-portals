<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh;

use Symfony\Component\HttpFoundation\Response;

/**
 * Class NotFoundException
 * @package Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh
 */
class NotFoundException extends BaseResponseException
{
    /** {@inheritdoc} */
    protected const STATUS_CODE = Response::HTTP_NOT_FOUND;
}
