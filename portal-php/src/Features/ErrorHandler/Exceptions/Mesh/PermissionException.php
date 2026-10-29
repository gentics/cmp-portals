<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh;

use Symfony\Component\HttpFoundation\Response;

/**
 * Class PermissionException
 * @package Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh
 */
class PermissionException extends BaseResponseException
{
    /** {@inheritdoc} */
    protected const STATUS_CODE = Response::HTTP_FORBIDDEN;
}
