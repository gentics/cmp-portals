<?php

namespace Gentics\PortalPhp\Tests\Unit;

use Gentics\PortalPhp\Http\Controllers\CmsController;
use Gentics\PortalPhp\Http\Controllers\ContentController;
use Gentics\PortalPhp\RenderMode;
use Gentics\PortalPhp\Tests\TestCase;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Http\Request;
use Illuminate\Routing\Route;

/**
 * Class PortalPhpTest
 * @package Gentics\PortalPhp\Tests\Unit
 */
class PortalPhpTest extends TestCase
{

    /**
     * Test reading a config value
     *
     * @throws \ReflectionException
     * @throws \Throwable
     */
    public function testConfig()
    {
        $expectedValue = "testvalue";
        $portalPhp = $this->app->make(PortalPhp::class);
        config(["portal.test" => $expectedValue]);

        $this->assertEquals(
            $expectedValue,
            self::callNonePublicMethod($portalPhp, 'config', ["test"])
        );
    }

    /**
     * Test getting the template name of a page by using the template path value
     *
     * @throws \Throwable
     */
    public function testGetTemplateName()
    {
        $expectedTemplateName = "testTemplateName";
        $portalPhp = $this->app->make(PortalPhp::class);

        $testData = ['data' => ['node' => ['fields' => ['customTemplateName' => $expectedTemplateName]]]];

        $templateName = $portalPhp->getTemplateName($testData, 'data.node.fields.customTemplateName');

        $this->assertEquals($expectedTemplateName, $templateName);
    }

    /**
     * Test loading a page by path
     *
     * @throws \Throwable
     */
    public function testLoadByPath()
    {
        $projectName = 'test';
        config(["portal.projectName" => $projectName]);

        $portalPhp    = $this->createPortalPhpWithMockedMeshClient();

        $responseJson = $this->loadResponseFile("/testLoadByPath.json");

        $portalPhp->getMeshClient()
            ->expects($this->once())
            ->method("graphQL")
            ->with($this->equalTo($projectName))
            ->willReturn($this->createMockedMeshRequest($responseJson));

        // Load from Rest API
        $response = $portalPhp->loadByPath("graphql/loadBypath.graphql", $projectName);

        $this->assertEquals($response->toArray()['data']['node']['fields']['name'], "Contentpage");
    }

    /**
     * Test loading a binary by its path
     *
     * @throws \Throwable
     */
    public function testLoadBinaryByPath()
    {
        $projectName = 'test';
        config([
            "portal.projectName" => $projectName,
            "portal.compatibility.useWebrootField" => false,
        ]);

        $portalPhp    = $this->createPortalPhpWithMockedMeshClient();

        $responseData = \file_get_contents(resource_path() . "/assets/testfile.txt");

        $portalPhp->getMeshClient()
            ->expects($this->once())
            ->method("webroot")
            ->with($this->equalTo($projectName))
            ->willReturn($this->createMockedMeshRequest($responseData));

        
        // Load from Rest API
        $response = $portalPhp->loadBinaryByPath("graphql/loadBypath.graphql", $projectName);

        $this->assertEquals(trim($response->getBody()->getContents()), "This is some sample content");
    }

    /**
     * Test loading a binary by its path with webrootField
     *
     * @throws \Throwable
     */
    public function testLoadBinaryByPathWebRootField()
    {
        $projectName = 'test';
        config([
            "portal.projectName" => $projectName,
            "portal.compatibility.useWebrootField" => true,
        ]);

        $portalPhp    = $this->createPortalPhpWithMockedMeshClient();

        $responseData = \file_get_contents(resource_path() . "/assets/testfile.txt");

        $portalPhp->getMeshClient()
            ->expects($this->once())
            ->method("webrootField")
            ->with($this->equalTo($projectName))
            ->willReturn($this->createMockedMeshRequest($responseData));

        
        // Load from Rest API
        $response = $portalPhp->loadBinaryByPath("graphql/loadBypath.graphql", $projectName);

        $this->assertEquals(trim($response->getBody()->getContents()), "This is some sample content");
    }

    /**
     * Test renderMode method in CMS mode and in Content mode
     */
    public function testRenderMode()
    {
        $classes      = [CmsController::class, ContentController::class];
        $renderModes  = RenderMode::getKeys();
        $currentClass = null;
        $currentMode  = null;

        $request = $this->createMock(Request::class);
        $route = $this->createMock(Route::class);

        $route->method('getAction')->willReturnCallback(function () use (&$currentClass) {
            return [ 'controller' => $currentClass . '@renderModeTest' ];
        });

        $request
            ->expects($this->any())
            ->method('route')->willReturnCallback(function () use (&$route) {
                return $route;
            });

        $request
            ->expects($this->any())
            ->method('query')->willReturnCallback(function (string $queryName) use (&$currentMode) {
                if ($queryName === 'renderMode') {
                    return $currentMode;
                }

                return null;
            });

        // Replace request with mocked version
        $this->app['request'] = $request;

        // Loop through in Content and CMS mode with all modes and assert
        foreach ($classes as $testClass) {
            foreach ($renderModes as $key => $renderMode) {
                $portalPhp = new PortalPhp();
                $currentMode = strtolower($renderMode);
                $currentClass = $testClass;

                if ($testClass === CmsController::class) {
                    $this->assertEquals($key, $portalPhp->renderMode());
                } else {
                    // In content controller it always should return Publish
                    $this->assertEquals(RenderMode::PUBLISH, $portalPhp->renderMode());
                }
            }
        }
    }
}
