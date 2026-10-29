<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\Keycloak\ServiceProvider as AuthenticationService;
use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Contract\Authenticator;
use Gentics\PortalPhp\Features\Keycloak\Http\Middleware\Authenticate;
use Gentics\PortalPhp\Features\Keycloak\Storage\Keystore;
use Gentics\PortalPhp\Features\Keycloak\TokenRefresh;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Http\Request;

use Lcobucci\Clock\Clock;
use Lcobucci\JWT\Token\Parser;

use Lcobucci\JWT\Signer;
use Lcobucci\JWT\Signer\Key;
use Lcobucci\JWT\Signer\Key\InMemory;
use Lcobucci\JWT\Validation\Validator;

use DateTimeImmutable;
use Lcobucci\Clock\FrozenClock;

class KeycloakTest extends TestCase
{
    public $tokenValidDate = 1542710800;
    public $tokenInvalidDate = 1552710900;

    public function setUp(): void
    {
        parent::setUp();

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

        $this->app->register(AuthenticationService::class);
    }

    /**
     * Test case for Login with Token has Invalid signature, Invalid clock and Invalid refresh token behavior
     */
    public function testMiddlewareTokenUnparseableSignature()
    {
        $request = new Request();

        $request->cookies->add([
            'AUTH' => $this->unparseableToken(),
        ]);

        $authenticator = $this->mockAuthenticator();

        $middleware = $this->mockAuthenticateMiddleware([
            $this->app->make(TokenStorage::class),
            $this->app->make(Parser::class),
            $this->app->make(Validator::class),
            $this->invalidClock(),
            $this->app->make(Signer::class),
            $authenticator,
            $this->mockTokenRefreshFail(),
            $this->mockValidKeystore()
        ]);

        $nextCalled = false;
        $middleware->expects($this->never())->method('isValidSignature');
        $middleware->expects($this->never())->method('isNotExpired');
        $middleware->expects($this->never())->method('refresh');
        $authenticator->expects($this->never())->method('authUser');

        $middleware->handle($request, function ($r) use (&$nextCalled) {
            $nextCalled = true;
        });

        $this->assertTrue($nextCalled, '$next should be called');

        $this->assertNull(\Auth::user());
    }

    /**
     * Test case for Login with Token has Invalid signature, Invalid clock and Invalid refresh token behavior
     */
    public function testMiddlewareTokenInvalidSignature()
    {
        $request = new Request();

        $request->cookies->add([
            'AUTH' => $this->invalidToken(),
        ]);

        $authenticator = $this->mockAuthenticator();

        $middleware = $this->mockAuthenticateMiddleware([
            $this->app->make(TokenStorage::class),
            $this->app->make(Parser::class),
            $this->app->make(Validator::class),
            $this->invalidClock(),
            $this->app->make(Signer::class),
            $authenticator,
            $this->mockTokenRefreshFail(),
            $this->mockValidKeystore()
        ]);

        $nextCalled = false;
        $middleware->expects($this->once())->method('isValidSignature');
        $middleware->expects($this->never())->method('isNotExpired');
        $middleware->expects($this->never())->method('refresh');
        $authenticator->expects($this->never())->method('authUser');

        $middleware->handle($request, function ($r) use (&$nextCalled) {
            $nextCalled = true;
        });

        $this->assertTrue($nextCalled, '$next should be called');

        $this->assertNull(\Auth::user());
    }

    /**
     * Test case for Login with Token has Valid signature, Invalid clock and Invalid refresh token behavior
     */
    public function testMiddlewareTokenValidSignatureInvalidClockInvalidRefresh()
    {
        $request = new Request();

        $request->cookies->add([
            'AUTH' => $this->validToken(),
        ]);

        $authenticator = $this->mockAuthenticator();

        $middleware = $this->mockAuthenticateMiddleware([
            $this->app->make(TokenStorage::class),
            $this->app->make(Parser::class),
            $this->app->make(Validator::class),
            $this->invalidClock(),
            $this->app->make(Signer::class),
            $authenticator,
            $this->mockTokenRefreshFail(),
            $this->mockValidKeystore()
        ]);

        $nextCalled = false;
        $middleware->expects($this->once())->method('isValidSignature');
        $middleware->expects($this->once())->method('isNotExpired');
        $middleware->expects($this->once())->method('refresh');
        $authenticator->expects($this->never())->method('authUser');

        $middleware->handle($request, function ($r) use (&$nextCalled) {
            $nextCalled = true;
        });

        $this->assertTrue($nextCalled, '$next should be called');

        $this->assertNull(\Auth::user());
    }

    /**
     * Test case for Login with Token has Valid signature, Valid clock behavior
     */
    public function testMiddlewareTokenValidSignatureValidClock()
    {
        $request = new Request();

        $request->cookies->add([
            'AUTH' => $this->validToken(),
        ]);

        $authenticator = $this->mockAuthenticator();

        $middleware = $this->mockAuthenticateMiddleware([
            $this->app->make(TokenStorage::class),
            $this->app->make(Parser::class),
            $this->app->make(Validator::class),
            $this->validClock(),
            $this->app->make(Signer::class),
            $authenticator,
            $this->mockTokenRefreshFail(),
            $this->mockValidKeystore()
        ]);

        $nextCalled = false;
        $middleware->expects($this->once())->method('isValidSignature');
        $middleware->expects($this->once())->method('isNotExpired');
        $middleware->expects($this->never())->method('refresh');
        $authenticator->expects($this->once())->method('authUser');

        $middleware->handle($request, function ($r) use (&$nextCalled) {
            $nextCalled = true;
        });

        $this->assertTrue($nextCalled, '$next should be called');

        $this->assertNull(\Auth::user());
    }

    /**
     * Test case for Login with Token has Valid signature, Valid clock behavior
     */
    public function testMiddlewareTokenValidSignatureValidClockAuthenticated()
    {
        $request = new Request();

        $request->cookies->add([
            'AUTH' => $this->validToken(),
        ]);

        $middleware = $this->mockAuthenticateMiddleware([
            $this->app->make(TokenStorage::class),
            $this->app->make(Parser::class),
            $this->app->make(Validator::class),
            $this->validClock(),
            $this->app->make(Signer::class),
            $this->app->make(Authenticator::class),
            $this->mockTokenRefreshFail(),
            $this->mockValidKeystore()
        ]);

        $nextCalled = false;
        $middleware->expects($this->once())->method('isValidSignature');
        $middleware->expects($this->once())->method('isNotExpired');
        $middleware->expects($this->never())->method('refresh');

        $middleware->handle($request, function ($r) use (&$nextCalled) {
            $nextCalled = true;
        });

        $this->assertTrue($nextCalled, '$next should be called');

        $this->assertNotNull(\Auth::user());
        $this->assertEquals(\Auth::user()->username, 'testcase@dev.gentics.com');
    }

    private function invalidClock()
    {
        return new FrozenClock(DateTimeImmutable::createFromFormat('U', $this->tokenInvalidDate));
    }

    private function validClock()
    {
        return new FrozenClock(DateTimeImmutable::createFromFormat('U', $this->tokenValidDate));
    }

    private function validToken()
    {
        return \file_get_contents(resource_path() . "/responses/testAuthCookieValid.txt");
    }

    private function invalidToken()
    {
        return $this->validToken() . 'AAinvalidated0';
    }

    private function unparseableToken()
    {
        return $this->validToken() . 'invalidated';
    }

    private function mockValidKeystore()
    {
        $keystore = $this->createMock(Keystore::class);
        $keystore->method('get')
            ->willReturn(
                InMemory::plainText(\file_get_contents(resource_path() . "/responses/testKeycloakRealmPublicKey.txt"))
            );
        return $keystore;
    }

    private function mockTokenRefreshFail()
    {
        $tokenRefreshFail = $this->createMock(TokenRefresh::class);
        $tokenRefreshFail->method('refreshIDToken')
            ->willReturn('InVaLiD');
        return $tokenRefreshFail;
    }

    private function mockAuthenticator()
    {
        $authenticator = $this->getMockBuilder(Authenticator::class)
            ->onlyMethods(['authUser'])
            ->getMock();
        return $authenticator;
    }

    private function mockAuthenticateMiddleware(array $args)
    {
        $middleware = $this->getMockBuilder(Authenticate::class)
            ->enableOriginalConstructor()
            ->setConstructorArgs($args)
            ->onlyMethods(['isValidSignature', 'isNotExpired', 'refresh'])
            ->enableProxyingToOriginalMethods()
            ->getMock();
        return $middleware;
    }
}
