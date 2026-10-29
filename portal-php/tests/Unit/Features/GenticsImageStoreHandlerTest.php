<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Tests\TestCase;
use Gentics\PortalPhp\Features\GenticsImageStore\GenticsImageStoreHandler;
use Illuminate\Http\Response;
use Illuminate\Support\Arr;

class GenticsImageStoreHandlerTest extends TestCase
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
    }

    /**
     * Test getParamsFromRequest method with mocked requests
     *
     * @throws \ReflectionException
     */
    public function testGetParamsFromRequest()
    {
        $testParams = [
            '/GenticsImageStore/200/auto/prop/test/demo.jpg' => [
                'routeParams' => [
                    'width' => 200,
                    'height' => 'auto',
                    'mode' => 'prop',
                    'path' => 'test/demo.jpg'
                ],
                'expected' => [
                    'w' => 200,
                    'h' => 'auto',
                    'resize' => 'prop'
                ]
            ],
            '/GenticsImageStore/auto/200/prop/test/demo.jpg' => [
                'routeParams' => [
                    'width' => 'auto',
                    'height' => 200,
                    'mode' => 'prop',
                    'path' => 'test/demo.jpg'
                ],
                'expected' => [
                    'w' => 'auto',
                    'h' => 200,
                    'resize' => 'prop'
                ]
            ],
            '/GenticsImageStore/300/200/prop/test/demo.jpg' => [
                'routeParams' => [
                    'width' => 300,
                    'height' => 200,
                    'mode' => 'prop',
                    'path' => 'test/demo.jpg'
                ],
                'expected' => [
                    'w' => 300,
                    'h' => 200,
                    'resize' => 'prop'
                ]
            ],
            '/GenticsImageStore/300/200/cropandresize/force/100/90/80/70/test/demo.jpg' => [
                'routeParams' => [
                    'width' => 300,
                    'height' => 200,
                    'mode' => 'force',
                    'path' => 'test/demo.jpg',
                    'topleft_x' => 100,
                    'topleft_y' => 90,
                    'cropwidth' => 80,
                    'cropheight' => 70,
                ],
                'expected' => [
                    'w' => 300,
                    'h' => 200,
                    'rect' => '100,90,80,70',
                    'crop' => 'rect',
                    'resize' => 'force'
                ]
            ],
            '/GenticsImageStore/300/200/smart/test/demo.jpg?fpz=1.2' => [
                'routeParams' => [
                    'width' => 300,
                    'height' => 200,
                    'mode' => 'smart',
                    'path' => 'test/demo.jpg'
                ],
                'inputParams' => [
                    'fpz' => 1.2
                ],
                'expected' => [
                    'w' => 300,
                    'h' => 200,
                    'fpz' => 1.2,
                    'crop' => 'fp',
                    'resize' => 'smart'
                ]
            ],
            '/GenticsImageStore/300/200/prop/test/demo.jpg?fpz=1.2&fpx=0.3&fpy=0.6' => [
                'routeParams' => [
                    'width' => 300,
                    'height' => 200,
                    'mode' => 'prop',
                    'path' => 'test/demo.jpg'
                ],
                'inputParams' => [
                    'fpz' => 1.2,
                    'fpx' => 0.3,
                    'fpy' => 0.6,
                ],
                'expected' => [
                    'w' => 300,
                    'h' => 200,
                    'fpz' => 1.2,
                    'fpx' => 0.3,
                    'fpy' => 0.6,
                    'crop' => 'fp',
                    'resize' => 'prop'
                ]
            ],
        ];

        foreach ($testParams as $requestUrl => $params) {
            $gisHandler = $this->app->make(GenticsImageStoreHandler::class);

            $routeParams = Arr::get($params, 'routeParams', []);
            $inputParams = Arr::get($params, 'inputParams', []);

            $this->assertEquals(
                $params['expected'],
                self::callNonePublicMethod($gisHandler, 'getParamsFromRequest', [
                    $routeParams,
                    $inputParams,
                ])
            );
        }
    }
}
