<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\ErrorHandler\Handler;
use Gentics\PortalPhp\Features\Redirect\RedirectHandler;
use Gentics\PortalPhp\Features\Redirect\ServiceProvider as RedirectService;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Contracts\Debug\ExceptionHandler;
use NunoMaduro\Collision\Adapters\Laravel\ExceptionHandler as CollisionExceptionHandler;

/**
 * Class RedirectWrappedExceptionHandlerTest
 * @package Gentics\PortalPhp\Tests\Unit\Features
 */
class RedirectWrappedExceptionHandlerTest extends TestCase
{
    /**
     * In console runs nunomaduro/collision replaces the ExceptionHandler binding with a wrapper around the
     * Portal | php Handler. Booting the Redirect feature must still register its error handler for the wrapped Handler.
     */
    public function testRedirectHandlerIsRegisteredWhenExceptionHandlerIsWrapped()
    {
        // A fresh Handler, as the one bound during setUp already got the RedirectHandler on boot
        /** @var Handler $portalHandler */
        $portalHandler = $this->app->make(Handler::class);

        $this->app->singleton(ExceptionHandler::class, function ($app) use ($portalHandler) {
            return new CollisionExceptionHandler($app, $portalHandler);
        });

        (new RedirectService($this->app))->boot($this->app['router']);

        $this->assertTrue($portalHandler->hasErrorHandler(RedirectHandler::class));
    }
}
