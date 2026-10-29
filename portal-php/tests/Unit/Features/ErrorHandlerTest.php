<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\MeshException;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\Features\ErrorHandler\Handler;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Container\Container;
use Illuminate\Contracts\Session\Session;
use Illuminate\Http\Response;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;
use Illuminate\Http\Request;
use PHPUnit\Framework\MockObject\MockObject;

class ErrorHandlerTest extends TestCase
{
    /**
     * @var \Gentics\PortalPhp\PortalPhp|null Instance
     */
    protected $portalPhp         = null;

    protected function setUp(): void
    {
        parent::setUp();
        $this->portalPhp = $this->createPortalPhpWithMockedMeshClient();

        // Setup session
        config(['session.driver' => 'array']);
        $session = $this->app->make(Session::class);
        request()->setLaravelSession($session);
    }
    /**
     * Test Error Page localization with user saved language, rfc4647, and portal default language
     *
     * @throws \Throwable
     */
    public function testLocalizedErrorPages()
    {
        $tests = [
            /**
             * Expected Error language: hu
             * Default language: null
             * User session language: null
             * Accept-Language: hu-HU, de-DE;q=0.9
             */
            [
                'expectedResponse' => 'testLoadError_404hu.json',
                'headers'          => ['HTTP_ACCEPT_LANGUAGE' => 'hu-HU, de-DE;q=0.9']
            ],
            /**
             * Expected Error language: hu
             * Default language: hu
             * User session language: null
             * Accept-Language: fr-CH, fr;q=0.9
             */
            [
                'expectedResponse' => 'testLoadError_404hu.json',
                'config'           => ['portal.defaultLanguage' => 'hu'],
                'headers'          => ['HTTP_ACCEPT_LANGUAGE' => 'fr-CH, fr;q=0.9']
            ],
            /**
             * Expected Error language: en
             * Default language: hu
             * User session language: en
             * Accept-Language: en, hu-HU;q=0.9
             */
            [
                'expectedResponse' => 'testLoadError_404en.json',
                'config'           => ['portal.defaultLanguage' => 'hu'],
                'session'          => ['portalPhp' => ['languageCode' => 'en']],
                'headers'          => ['HTTP_ACCEPT_LANGUAGE' => 'en, hu-HU;q=0.9']
            ],
            /**
             * Expected Error language: en
             * Default language: hu
             * User session language: fr
             * Accept-Language: en, hu-HU;q=0.9
             */
            [
                'expectedResponse' => 'testLoadError_404en.json',
                'config'           => ['portal.defaultLanguage' => 'en'],
                'session'          => ['portalPhp' => ['languageCode' => 'fr']],
                'headers'          => ['HTTP_ACCEPT_LANGUAGE' => 'en, hu-HU;q=0.9']
            ],
        ];

        foreach ($tests as $test) {
            $testResponse = json_decode($this->loadResponseFile($test['expectedResponse']), true);

            // Set session
            $this->withSession($test['session'] ?? []);
            // Set server headers
            $_SERVER = array_merge($_SERVER, $test['headers'] ?? []);
            // Set configuration
            config($test['config'] ?? []);

            $this->assertLocalizedErrorPage($testResponse['data']['node']['fields']['name']);
        }
    }

    /**
     * @param $contentShouldMatch
     * @throws \Throwable
     */
    public function assertLocalizedErrorPage($contentShouldMatch)
    {
        config([
            'app.debug' => false,
            'portal.exceptionHandler.httpCodes' => [ Response::HTTP_NOT_FOUND => '/errors/404.en.html' ]
        ]);

        $responseFiles = [
            '/errors/404.en.html' => 'testLoadError_404en.json',
            '/errors/404.hu.html' => 'testLoadError_404hu.json',
        ];

        $responseFile = $responseFiles['/errors/404.en.html'];

        $this->mockResponse(
            "graphQL",
            $responseFile,
            Response::HTTP_OK,
            [],
            function (&$mockedMeshRequest, array $parameters) use (&$responseFile, $responseFiles) {
                if (isset($parameters[1]['variables']['path'])) {
                    $responseFile = $responseFiles[$parameters[1]['variables']['path']];
                } else {
                    $responseFile = '';
                }

                if (!empty($responseFile)) {
                    $mockedMeshRequest = $this->createMockedMeshRequest(
                        $this->loadResponseFile($responseFile),
                        Response::HTTP_OK,
                        []
                    );
                }
            }
        );

        /** @var Request $request */
        $request   = $this->app->make(Request::class);

        /** @var Container|MockObject $container */
        $container = $this->createMock(Container::class);

        /** @var Handler|MockObject $handler */
        $handler = $this->getMockBuilder(Handler::class)
            ->enableOriginalConstructor()
            ->setConstructorArgs([
                $container,
                $this->portalPhp
            ])
            ->enableProxyingToOriginalMethods()
            ->onlyMethods(['render'])
            ->getMock();

        // Test with 404 status code
        $response = $handler->render($request, new NotFoundHttpException());
        $this->assertEquals($contentShouldMatch, trim($response->content()));
    }

    /**
     * Test to display exception in debug mode
     */
    public function testErrorHandlerDebugOnNotFoundHttpException()
    {
        config([
           'app.debug' => true
        ]);

        $request   = $this->app->make(Request::class);
        $exceptions = $this->getExceptionsToTest();

        $handler = $this->getHandlerMock();
        $handler->expects($this->exactly(2))->method('renderDebugException');

        foreach ($exceptions as $exception) {
            $response = $handler->render($request, $exception);
            // Should not get a mesh error page
            $this->assertNotEquals($response, 'mesherrorpage');
        }
    }

    /**
     * Test to display exception in non debug mode
     */
    public function testErrorHandlerNonDebugOnNotFoundHttpException()
    {
        config([
            'app.debug' => false
        ]);

        $request   = $this->app->make(Request::class);
        $exceptions = $this->getExceptionsToTest();

        $handler = $this->getHandlerMock();
        $handler->expects($this->never())->method('renderDebugException');

        foreach ($exceptions as $exception) {
            $response = $handler->render($request, $exception);
            // Should not get a debug error page
            $this->assertNotEquals($response, 'debugerrorpage');
        }
    }

    protected function getExceptionsToTest()
    {
        return [
            new NotFoundHttpException(),
            new HttpException(Response::HTTP_UNAUTHORIZED),
            new PortalPhpException(),
            new MeshException()
        ];
    }

    protected function getHandlerMock()
    {
        /** @var Container|MockObject $container */
        $container = $this->createMock(Container::class);

        $handler = $this->getMockBuilder(Handler::class)
            ->enableOriginalConstructor()
            ->setConstructorArgs([
                $container,
                $this->portalPhp
            ])
            ->onlyMethods(['renderDebugException', 'renderExceptionPage'])
            ->getMock();

        $handler
            ->method('renderDebugException')
            ->willReturn('debugerrorpage');

        $handler
            ->method('renderExceptionPage')
            ->willReturn('mesherrorpage');

        return $handler;
    }
}
