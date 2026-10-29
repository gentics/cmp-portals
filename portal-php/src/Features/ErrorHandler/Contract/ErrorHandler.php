<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Contract;

use Exception;

interface ErrorHandler
{
    const CANNOT_HANDLE_THIS_ERROR = false;

    /**
     * @param $request
     * @param Exception $exception
     * @return mixed
     */
    public function handle($request, Exception $exception);
}
