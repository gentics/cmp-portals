<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\Mesh\NotFoundException;
use Gentics\PortalPhp\Tests\TestCase;
use GuzzleHttp\Psr7\Response as GuzzleResponse;
use Illuminate\Http\Request;
use Gentics\PortalPhp\Features\Redirect;
use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Symfony\Component\HttpFoundation\Response;

/**
 * Class RedirectTest
 * @package Gentics\PortalPhp\Tests\Unit\Features
 */
class RedirectTest extends TestCase
{
    /**
     * Test Redirect with DataProvider source, matching url, temporary code
     */
    public function testDataProviderRedirectMatchWithTemporaryRedirect()
    {
        $this->SetRedirectWithDataProvider(false);
        $this->SetDataProviderConfig();

        $mock = $this->GetDataProviderMock();
        $this->app->instance(DataProvider::class, $mock);

        $request = $this->GetRequestMock('http://example.com/testFrom');

        $handler = $this->app->make(Redirect\RedirectHandler::class);
        $response = $handler->handle($request, new NotFoundException([], new GuzzleResponse()));

        $this->assertEquals($response->getStatusCode(), Response::HTTP_SEE_OTHER);
        $this->assertEquals($response->headers->get('Location'), 'http://example.com/testTo');
    }

    /**
     * Test Redirect with DataProvider source, matching url, permanent code
     */
    public function testDataProviderRedirectMatchWithPermanentRedirect()
    {
        $this->SetRedirectWithDataProvider(true);
        $this->SetDataProviderConfig();

        $mock = $this->GetDataProviderMock();
        $this->app->instance(DataProvider::class, $mock);

        $request = $this->GetRequestMock('http://example.com/testFrom');

        $handler = $this->app->make(Redirect\RedirectHandler::class);
        $response = $handler->handle($request, new NotFoundException([], new GuzzleResponse()));

        $this->assertEquals($response->getStatusCode(), Response::HTTP_MOVED_PERMANENTLY);
        $this->assertEquals($response->headers->get('Location'), 'http://example.com/testTo');
    }

    /**
     * Test Redirect with DataProvider source, non-matching url
     */
    public function testDataProviderRedirectNoMatch()
    {
        $this->SetRedirectWithDataProvider(false);
        $this->SetDataProviderConfig();

        $mock = $this->GetDataProviderMock();
        $this->app->instance(DataProvider::class, $mock);

        $request = $this->GetRequestMock('http://example.com/testFromNoMatch');

        $handler = $this->app->make(Redirect\RedirectHandler::class);
        $response = $handler->handle($request, new NotFoundException([], new GuzzleResponse()));

        $this->assertFalse($response);
    }

    /**
     * Test Redirect with DataProvider source, non-matching exception
     */
    public function testDataProviderRedirectOtherException()
    {
        $this->SetRedirectWithDataProvider(false);
        $this->SetDataProviderConfig();

        $mock = $this->GetDataProviderMock();
        $this->app->instance(DataProvider::class, $mock);

        $request = $this->GetRequestMock('http://example.com/testFrom');

        $handler = $this->app->make(Redirect\RedirectHandler::class);
        $response = $handler->handle($request, new \Exception('Other Exception'));

        $this->assertFalse($response);
    }

    /**
     * Test Redirect with invalid DataProvider source
     */
    public function testInvalidDataProviderRedirect()
    {
        $this->SetRedirectWithDataProvider(false);
        $this->SetDataProviderConfig();

        $mock = $this->GetDataProviderMock([null]);
        $this->app->instance(DataProvider::class, $mock);

        $request = $this->GetRequestMock('http://example.com/testFrom');

        $handler = $this->app->make(Redirect\RedirectHandler::class);
        $response = $handler->handle($request, new NotFoundException([], new GuzzleResponse()));

        $this->assertFalse($response);
    }

    /**
     * Returns a DataProvider Mock with specified or sample redirect data
     *
     * @param null $returns
     * @return \PHPUnit\Framework\MockObject\MockObject
     */
    private function GetDataProviderMock($returns = null)
    {
        $sampleRedirectData = [
            'node' => [
                'fields' => [
                    'testredirecttag' => json_encode([
                        [
                            'oldUrl' => 'http://example.com/testFrom',
                            'newUrl' => 'http://example.com/testTo'
                        ]
                    ])
                ]
            ]
        ];

        $mock = $this->createMock(DataProvider::class);
        $mock
            ->expects($this->any())
            ->method('retrieveMeshData')
            ->with(
                $this->equalTo('redirectstest')
            )->willReturn($returns ?? $sampleRedirectData);
        return $mock;
    }

    private function GetRequestMock($url)
    {
        $request = $this->createMock(Request::class);
        $request->expects($this->any())
            ->method('url')
            ->willReturn($url);
        return $request;
    }

    /**
     * Set Redirect configuration in PortalPhp
     *
     * @param bool $isPermanent Set to permanent or temporary mode
     */
    private function SetRedirectWithDataProvider($isPermanent = false)
    {
        config(['portal.redirects' => [
            'enabled' => true,
            'permanentRedirects' => $isPermanent,
            'source' => 'dataProvider:redirectsTest',
            'helperConfig' => [
                'fieldName' => 'testredirecttag',
            ],
        ]]);
    }

    /**
     * Set DataProvider configuration in PortalPhp
     */
    private function SetDataProviderConfig()
    {
        config(['portal.dataProvider.meshData' => [
            'redirectsTest' => 'test/Location.html'
        ]]);
    }
}
