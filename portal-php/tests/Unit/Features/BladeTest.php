<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Tests\TestCase;

class BladeTest extends TestCase
{
    public $parameters = [
        'bladeCode' => 'The cow is {{ $firstColor }} and @php echo $secondColorArgument; @endphp',
        'firstColor' => 'black',
        'secondColor' => 'white',
    ];
    public $expected = "Pre\nThe cow is black and white\nPost";
    public $emptyExpected = "Pre\n\nPost";

    protected function setUp(): void
    {
        parent::setUp();
        config(['portal.cache.blade.deleteCachedView' => false]);

    }

    public function testEvalDirective()
    {
        // Run it two times to also test it with the cached view
        for ($i = 1; $i <= 2; $i++) {
            $view = view('testEvalDirective', $this->parameters);
            $this->assertEquals($this->expected, trim($view->render()));
        }
    }

    public function testRenderBladeHelper()
    {
        // Run it two times to also test it with the cached view
        for ($i = 1; $i <= 2; $i++) {
            $view = view('testRenderBladeHelper', $this->parameters);
            $this->assertEquals($this->expected, trim($view->render()));
        }
    }

    public function testRenderEmptyBladeHelper()
    {
        // Run it two times to also test it with the cached view
        for ($i = 1; $i <= 2; $i++) {
            $view = view('testRenderEmptyBladeHelper');
            $this->assertEquals($this->emptyExpected, trim($view->render()));
        }
    }
}
