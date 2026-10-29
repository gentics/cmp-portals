<?php

namespace Gentics\PortalPhp\Tests\Unit;

use Dflydev\ApacheMimeTypes\PhpRepository;
use Gentics\PortalPhp\Tests\TestCase;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Symfony\Component\HttpKernel\Exception;
use Illuminate\Http\File;
use Illuminate\Http\Response;

/**
 * Tests for ContentController
 */
class ContentControllerTest extends TestCase
{
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
     * Test GETting a page
     */
    public function testPage()
    {
        $this->mockResponse("graphQL", "/testLoadByPath.json");
        $response = $this->call(
            'GET',
            '/mysuperawesomepage.html',
            [],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertOk();
        $response->assertViewIs("contentpage");
    }

    /**
     * Test GETting a non existing page
     */
    public function testPageNotFound()
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
     * Test GETting a binary
     */
    public function testBinary()
    {
        $this->mockResponse(
            "webrootField",
            "/gentics-logo-28px.png",
            Response::HTTP_OK,
            ['Webroot-Response-Type' => 'binary']
        );
        $this->mockResponse(
            "webroot",
            "/gentics-logo-28px.png",
            Response::HTTP_OK,
            ['Webroot-Response-Type' => 'binary']
        );
        $response = $this->call(
            'GET',
            '/gentics-logo-28px.png',
            [],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertOk();
        $this->assertInstanceOf(
            StreamedResponse::class,
            $response->baseResponse
        );
    }

    /**
     * Test GETting a none existing binary
     */
    public function testBinaryNotFound()
    {
        $this->expectException(Exception\NotFoundHttpException::class);
        $this->mockResponse("webrootField", "/gentics-logo-28px.png", Response::HTTP_NOT_FOUND);
        $this->mockResponse("webroot", "/gentics-logo-28px.png", Response::HTTP_NOT_FOUND);
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);
        $response = $this->call(
            'GET',
            '/gentics-logo-28px.png',
            [],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );
    }

    /**
     * Test GETting a page that matches the binaryFilesGuesser and is assumed to be a binary first
     */
    public function testBinaryIsAPage()
    {
        $this->mockResponse(
            "webrootField",
            null,
            Response::HTTP_OK,
            ['Webroot-Response-Type' => 'node']
        );
        $this->mockResponse(
            "webroot",
            null,
            Response::HTTP_OK,
            ['Webroot-Response-Type' => 'node']
        );
        $this->mockResponse("graphQL", "/testLoadByPath.json");
        $response = $this->call(
            'GET',
            '/myawesomepicture.jpg',
            [],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertOk();
        $response->assertViewIs("contentpage");
    }

    /**
     * Test GETting a binary that doesn't match the binaryFilesGuesser and is assumed to be a page first
     */
    public function testPageIsABinaryAndNotModified()
    {
        $this->mockResponse("graphQL", "/pageIsBinary.json");
        $this->mockWebrootAndCheckCacheHeaders('/awesomepage.html', null);
        $this->mockWebrootAndCheckCacheHeaders('/awesomepage.html', null, true);
    }

    /**
     * Test GETting a binary that is already cached by the user client
     */
    public function testBinaryHttpNotModified()
    {
        $this->mockWebrootAndCheckCacheHeaders("/gentics-logo-28px.png", '/gentics-logo-28px.png');
        $this->mockWebrootAndCheckCacheHeaders("/gentics-logo-28px.png", '/gentics-logo-28px.png', true);
    }

    /**
     * Test GETting a binary with range header
     */
    public function testBinaryHttpRange()
    {
        $this->mockWebrootAndCheckRangeHeaders("/gentics-logo-28px.png", '/gentics-logo-28px.png');
        $this->mockWebrootAndCheckRangeHeaders("/gentics-logo-28px.png", '/gentics-logo-28px.png', true);
    }

    /**
     * Test GETting a GenticsImageStore binary
     */
    public function testGenticsImageStore()
    {
        $this->mockResponse(
            "webrootField",
            "/gentics-logo-28px.png",
            Response::HTTP_OK,
            ['Webroot-Response-Type' => 'binary']
        );
        $this->mockResponse(
            "webroot",
            "/gentics-logo-28px.png",
            Response::HTTP_OK,
            ['Webroot-Response-Type' => 'binary']
        );
        $response = $this->call(
            'GET',
            '/GenticsImageStore/510/auto/prop/gentics-logo-28px.png',
            [],
            [],
            [],
            $this->getBrowserRequestHeaders()
        );

        $response->assertOk();
        $this->assertInstanceOf(
            StreamedResponse::class,
            $response->baseResponse
        );
    }

    /**
     * Test GETting a GenticsImageStore resized image that is already cached by the user client
     */
    public function testGenticsImageStoreHttpNotModified()
    {
        $this->mockWebrootAndCheckCacheHeaders(
            '/GenticsImageStore/510/auto/prop/gentics-logo-28px.png',
            "/gentics-logo-28px.png"
        );
        $this->mockWebrootAndCheckCacheHeaders(
            '/GenticsImageStore/510/auto/prop/gentics-logo-28px.png',
            "/gentics-logo-28px.png",
            true
        );
    }

    /**
     * @param string $path
     * @param string|null $responseFile
     * @param bool $webrootField
     * @return \Illuminate\Http\Response
     */
    protected function mockWebrootAndCheckCacheHeaders(string $path, ?string $responseFile, bool $webrootField = false)
    {
        $mockedRequestHeaders = [];

        config(["portal.compatibility.useWebrootField" => $webrootField]);
        $this->mockResponse(
            $webrootField ? "webrootField" : "webroot",
            $responseFile,
            Response::HTTP_NOT_MODIFIED,
            ['Webroot-Response-Type' => 'binary'],
            function ($mockedMeshRequest, array $parameters) use (&$mockedRequestHeaders) {
                $mockedMeshRequest
                    ->method('addHeader')
                    ->will($this->returnCallback(function (string $key, string $value) use ($mockedMeshRequest, &$mockedRequestHeaders) {
                        $mockedRequestHeaders[$key] = $value;
                    }));
            }
        );

        $requestHeaders = array_merge(
            $this->getBrowserRequestHeaders(),
            [
                // Apache2 adds -gzip suffix, test whether it is removed
                "HTTP_IF_NONE_MATCH" => '"3dea5bee-gzip"',
                "HTTP_CACHE_CONTROL" => "max-age=0"]
        );

        $response = $this->call(
            'GET',
            $path,
            [],
            [],
            [],
            $requestHeaders
        );

        $response->assertStatus(Response::HTTP_NOT_MODIFIED);
        $this->assertArrayHasKey('cache-control', $mockedRequestHeaders);
        $this->assertEquals($mockedRequestHeaders['cache-control'], 'max-age=0');
        $this->assertArrayHasKey('if-none-match', $mockedRequestHeaders);
        $this->assertEquals($mockedRequestHeaders['if-none-match'], '"3dea5bee"');

        return $response;
    }

    /**
     * @param string $path
     * @param null|string $responseFile
     * @param bool $webrootField
     * @return \Illuminate\Foundation\Testing\TestResponse
     */
    protected function mockWebrootAndCheckRangeHeaders(string $path, ?string $responseFile, bool $webrootField = false)
    {
        $mockedRequestHeaders = [];

        $file = new File(resource_path() . "/responses/" . $path, false);
        $repository = new PhpRepository();
        $mimeType = $repository->findType(strtolower($file->getExtension()));
        $responseSize = $file->getSize();

        config(["portal.compatibility.useWebrootField" => $webrootField]);
        $this->mockResponse(
            $webrootField ? "webrootField" : "webroot",
            $responseFile,
            Response::HTTP_PARTIAL_CONTENT,
            [
                'Webroot-Response-Type' => 'binary',
                'Content-Type' => $mimeType,
                'Accept-Ranges' => 'bytes',
                'Content-Range' => 'bytes 0-' . ($responseSize-1) . '/' . $responseSize,
                'Content-Length' => $responseSize,
            ],
            function ($mockedMeshRequest, array $parameters) use (&$mockedRequestHeaders) {
                $mockedMeshRequest
                    ->method('addHeader')
                    ->will($this->returnCallback(function (string $key, string $value) use ($mockedMeshRequest, &$mockedRequestHeaders) {
                        $mockedRequestHeaders[$key] = $value;
                    }));
            }
        );

        $requestHeaders = array_merge(
            $this->getBrowserRequestHeaders(),
            [
                "HTTP_RANGE" => 'bytes=0-'
            ]
        );

        $response = $this->call(
            'GET',
            $path,
            [],
            [],
            [],
            $requestHeaders
        );

        $response->assertStatus(Response::HTTP_PARTIAL_CONTENT);
        $response->assertHeader('Content-Type', $mimeType);
        $response->assertHeader('Accept-Ranges', 'bytes');
        $response->assertHeader('Content-Range', 'bytes 0-' . ($responseSize-1) . '/' . $responseSize);
        $response->assertHeader('Content-Length', $responseSize);
        $this->assertArrayHasKey('range', $mockedRequestHeaders);
        $this->assertEquals($mockedRequestHeaders['range'], 'bytes=0-');

        return $response;
    }
}
