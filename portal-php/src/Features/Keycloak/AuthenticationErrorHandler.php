<?php

namespace Gentics\PortalPhp\Features\Keycloak;

use Exception;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh\PermissionException;
use Gentics\PortalPhp\PortalPhp;
use Symfony\Component\HttpFoundation\Response;
use Gentics\PortalPhp\Features\ErrorHandler\Contract\ErrorHandler;

/**
 * Class AuthenticationErrorHandler
 * @package Gentics\PortalPhp\Features\Keycloak
 */
class AuthenticationErrorHandler implements ErrorHandler
{
    /**
     * @param $request
     * @param \Throwable $exception
     * @return bool|\Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector|mixed
     */
    public function handle($request, \Throwable $exception)
    {
        if ($exception instanceof PermissionException) {
            $portalPhp = app()->make(PortalPhp::class);
            $userToken = $portalPhp->getClientToken();

            // Check if there is a logged in user or not
            if ($userToken === null) {
                return redirect(route('login', ['returnTo' => url()->full()]));
            } else {
                abort(Response::HTTP_FORBIDDEN, $exception->getMessage());
            }
        }

        return static::CANNOT_HANDLE_THIS_ERROR;
    }
}
