<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\ErrorHandler\Handler;
use Gentics\PortalPhp\Features\Keycloak\AuthenticationErrorHandler;
use Gentics\PortalPhp\Features\Keycloak\ServiceProvider as AuthenticationService;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Contracts\Debug\ExceptionHandler;
use NunoMaduro\Collision\Adapters\Laravel\ExceptionHandler as CollisionExceptionHandler;

/**
 * Class KeycloakWrappedExceptionHandlerTest
 * @package Gentics\PortalPhp\Tests\Unit\Features
 */
class KeycloakWrappedExceptionHandlerTest extends TestCase
{
    /**
     * In console runs nunomaduro/collision replaces the ExceptionHandler binding with a wrapper around the
     * Portal | php Handler. Booting the Keycloak feature must still register its error handler for the wrapped Handler.
     */
    public function testAuthenticationErrorHandlerIsRegisteredWhenExceptionHandlerIsWrapped()
    {
        config(['portal.authentication.keycloak' => [
            'authUrl' => 'http://keycloakserver/auth',
            'realm' => '',
            'client_id' => '',
            'client_secret' => '',
            'redirect' => 'http://portal/auth/callback',
            'logoutEndpoint' => '/auth/logout',
            'loginEndpoint' => '/auth/redirect',
            'registerEndpoint' => '/auth/register'
        ]
        ]);

        /** @var Handler $portalHandler */
        $portalHandler = $this->app->make(ExceptionHandler::class);

        $this->app->singleton(ExceptionHandler::class, function ($app) use ($portalHandler) {
            return new CollisionExceptionHandler($app, $portalHandler);
        });

        $this->app->register(AuthenticationService::class);

        $this->assertTrue($portalHandler->hasErrorHandler(AuthenticationErrorHandler::class));
    }
}
