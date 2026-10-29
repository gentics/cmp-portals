<?php

namespace Gentics\PortalPhp\Tests;

use Gentics\Mesh\Client\MeshClient;
use Gentics\Mesh\Client\Rest\MeshRequest;
use Gentics\Mesh\Client\Rest\MeshResponse;
use Gentics\PortalPhp\Features\Helpers\Helper;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Providers\ServiceProvider;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\View;
use Illuminate\Http\Response;
use PHPUnit\Framework\MockObject\MockObject;

/**
 * Class TestCase
 * @package Gentics\PortalPhp\Tests
 */
class TestCase extends \Orchestra\Testbench\TestCase
{
    protected function setUp(): void
    {
        /** @see TestCase::resetHelperMacros() */
        $this->resetHelperMacros();

        parent::setUp();
    }

    protected function tearDown(): void
    {
        Artisan::call("cache:clear");
        Artisan::call("view:clear");
        Artisan::call('route:clear');
        Artisan::call('config:clear');

        parent::tearDown();
    }

    /**
     * Load package service provider
     * @param  \Illuminate\Foundation\Application $app
     * @return mixed
     */
    protected function getPackageProviders($app)
    {
        return [ServiceProvider::class];
    }

    /**
     * Define environment setup.
     *
     * @param  \Illuminate\Foundation\Application $app
     * @return void
     */
    protected function getEnvironmentSetUp($app)
    {
        $app['config']->set('portal.apiKey', '123');
        $app['config']->set('portal.branches.enabled', false);

        $app->setBasePath(__DIR__);

        // Reinitialize paths that depend on the base path
        $app->resourcePath('');
        $app->storagePath('');
        $app->publicPath('');

        View::addLocation(__DIR__ . '/resources/views');

        parent::getEnvironmentSetUp($app);
    }

    /**
     * Helper for callning a protected or private class method
     *
     * @param $object
     * @param string $name
     * @param array $parameters
     * @return mixed
     * @throws \ReflectionException
     */
    protected static function callNonePublicMethod($object, string $name, array $parameters)
    {
        $class = new \ReflectionClass(get_class($object));
        $method = $class->getMethod($name);
        $method->setAccessible(true);

        return $method->invokeArgs($object, $parameters);
    }

    /**
     * Create a mock on "send" on the given mocked Mesh client and return the given $body
     * @param string $body The body to return (normally JSON)
     * @param int $statusCode The HTTP status code
     * @param array $responseHeaders
     * @return MeshRequest A mock of MeshRequest
     */
    protected function createMockedMeshRequest(
        string $body,
        int $statusCode = Response::HTTP_OK,
        array $responseHeaders = []
    ) {
        /** @var MeshRequest|MockObject $mockedMeshRequest */
        $mockedMeshRequest = $this->createMock(MeshRequest::class);

        $mockedMeshRequest
            ->method('send')
            ->willReturn(new MeshResponse(new \GuzzleHttp\Psr7\Response($statusCode, $responseHeaders, $body)));

        return $mockedMeshRequest;
    }

    /**
     * @param MeshClient $mockedMeshClient
     * @param $callback
     */
    protected function mockMeshClientSend(MeshClient $mockedMeshClient, $callback)
    {
        $mockedMeshClient
            ->method('send')
            ->will($this->returnCallback(
                function (MeshRequest $request, array $options) use ($callback) {
                    $response = $callback($request, $options);

                    if (empty($response)) {
                        throw new Exception("mockMeshClientSend: Request to " . $request->getUri()->getPath()
                        . " was not handled");
                    }

                    return $response;
                }
            ));
    }

    /**
     * @return mixed
     */
    protected function createPortalPhpWithMockedMeshClient()
    {
        $portalPhp = $this->app->make(PortalPhp::class);
        $mockedMeshClient = $this->createMock(MeshClient::class);
        $portalPhp->setMeshClient($mockedMeshClient);
        $portalPhp->setAdminMeshClient($mockedMeshClient);

        return $portalPhp;
    }

    /**
     * Lodas the contents of a file in tests/resources/responses
     * @param string $path Path relative to the responses folder
     * @return false|string Content
     */
    protected function loadResponseFile(string $path)
    {
        return \file_get_contents(resource_path() . "/responses/" . $path);
    }

    /**
     * Get an array of request headers, a normal browser would send to the web server.
     * @return array Requests headers in $_SERVER format
     */
    protected function getBrowserRequestHeaders()
    {
        $requestHeaders = [
            'HTTP_ACCEPT'          => 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8',
            'HTTP_ACCEPT_ENCODING' => 'gzip, deflate, br',
            'HTTP_ACCEPT_LANGUAGE' => 'de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6,fr;q=0.5,fil;q=0.4,lb;q=0.3,ca;q=0.2',
            'HTTP_CACHE_CONTROL'   => 'no-cache',
            'HTTP_CONNECTION'      => 'keep-alive',
            'HTTP_DNT'             => '1',
            'HTTP_HOST'            => 'localhost:8080',
            'HTTP_REFERER'         => 'http://localhost:8080/',
            'HTTP_UPGRADE_INSECURE_REQUESTS' => '1',
            'HTTP_USER_AGENT'      => 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36',
        ];

        return $requestHeaders;
    }


    /**
     * Mocks any Mesh response and allows defining the HTTP status code and response headers
     * @param string $method Method of MeshClient to mock
     * @param string|null $responseFile Response file to load, will be used as response body
     * @param int $responseCode The response HTTP status code
     * @param array $responseHeaders HTTP Response headers in $_SERVER format
     * @param callable|null $callback Function that is called when the method is mocked
     *                                Parameters: ($mockedMeshRequest, array $parameters)
     */
    protected function mockResponse(
        string $method = "graphQL",
        ?string $responseFile = null,
        int $responseCode = Response::HTTP_OK,
        array $responseHeaders = [],
        ?callable $callback = null
    ) {
        $responseContent = empty($responseFile) ? "" : $this->loadResponseFile($responseFile);
        $adminMeshClient = $this->portalPhp->getMeshClient();

        $adminMeshClient
            ->method($method)
            ->will($this->returnCallback(function () use ($responseContent, $responseCode, $responseHeaders, $callback) {
                $mockedMeshRequest = $this->createMockedMeshRequest($responseContent, $responseCode, $responseHeaders);
                if ($callback !== null) {
                    call_user_func_array($callback, [&$mockedMeshRequest, func_get_args()]);
                }

                return $mockedMeshRequest;
            }));
    }

    /**
     * Reset static helper macros property
     *
     * Note: This is required because the Macroable trait stores the macros in a static
     * array, which does not resets between tests, however the Helpers service provider
     * initialized in every tests which cause the recreating of helpers (which is not
     * allowed if it has an existing name). In normal usage, this would never happen.
     */
    protected function resetHelperMacros()
    {
        $reflectionProperty = new \ReflectionProperty(Helper::class, 'macros');
        $reflectionProperty->setAccessible(true);
        $reflectionProperty->setValue([]);
    }

    /**
     * Get the number of files and folders in a directory
     * @param string $directoryPath
     * @return iterator_count Number of files and folders
     */
    public static function countFilesInDirectory(string $directoryPath)
    {
        return new iterator_count(FilesystemIterator($directoryPath, FilesystemIterator::SKIP_DOTS));
    }
}
