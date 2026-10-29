<?php

namespace Gentics\PortalPhp\Tests\Unit\Middleware;

use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Http\Response;
use Symfony\Component\HttpKernel\Exception;

class DebuggerTest extends TestCase
{
    const DEBUGGER_KEY = 'enabled';
    const DEBUGGER_CONFIG_FIELD = 'portal.debuggerKey';
    const DEBUGGER_QUERY_FIELD = 'debug';

    /**
     * @var string The project name to use
     */
    protected $projectName       = 'test';

    /**
     * @var \Gentics\PortalPhp\PortalPhp|null Instance
     */
    protected $portalPhp         = null;

    protected function setUp(): void
    {
        parent::setUp();

        config(["portal.projectName" => $this->projectName]);
        $this->portalPhp = $this->createPortalPhpWithMockedMeshClient();
        $this->withoutExceptionHandling();
    }

    /**
     * Test that the request has a regular error page
     */
    public function testErrorPage()
    {
        $this->expectException(Exception\NotFoundHttpException::class);
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);
        $response = $this->call(
            'GET',
            '/nonexisting.html',
            [],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );
    }

    /**
     * Test that the debugger-page is not being shown when the debuggerKey is set to null
     */
    public function testDisabledDebuggerPage()
    {
        $this->expectException(Exception\NotFoundHttpException::class);
        $this->app['config']->set(self::DEBUGGER_CONFIG_FIELD, null);
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);
        $response = $this->call(
            'GET',
            '/nonexisting.html',
            [self::DEBUGGER_QUERY_FIELD => self::DEBUGGER_KEY],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertDontSee('trace-as-html');
    }

    /**
     * Test that the debugger-page is not being shown when the debuggerKey is incorrect
     */
    public function testIncorrectKeyDebuggerPage()
    {
        $this->expectException(Exception\NotFoundHttpException::class);
        $this->app['config']->set(self::DEBUGGER_CONFIG_FIELD, self::DEBUGGER_KEY);
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);
        $response = $this->call(
            'GET',
            '/nonexisting.html',
            [self::DEBUGGER_QUERY_FIELD => 'something-else'],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertDontSee('trace-as-html');
    }

    /**
     * Test that the debugger-page should be shown when the proper key
     * is provided
     */
    public function testEnabledDebuggerPage()
    {
        $this->expectException(Exception\NotFoundHttpException::class);
        $this->app['config']->set(self::DEBUGGER_CONFIG_FIELD, self::DEBUGGER_KEY);
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);
        $response = $this->call(
            'GET',
            '/nonexisting.html',
            [self::DEBUGGER_QUERY_FIELD => self::DEBUGGER_KEY],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertSee('trace-as-html');
    }
}
