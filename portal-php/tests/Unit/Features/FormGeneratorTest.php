<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\FormGenerator\FormGeneratorHandler;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Support\Facades\View;
use GuzzleHttp\Psr7\Response as Psr7Response;

class FormGeneratorTest extends TestCase
{
    public static $testProjectName = 'testproject';
    public static $testFormUuid = 'd8dce18bb73849e19ce18bb738e9e1ae';
    public static $testFormDataUuid = '6ff0c7158ee74a3cb0c7158ee7aa3c0e';
    public static $testLanguage = 'en';
    public static $testExternalId = 'ext-42';
    public static $testPluginName = 'forms';
    public static $testServePath = '/static/cpa/';
    public static $testCssFile = 'app-abc123.css';
    public static $testJsFile = 'app-xyz789.js';

    /**
     * @var \Gentics\PortalPhp\PortalPhp|null Instance
     */
    protected $portalPhp = null;

    /**
     * @var string|null Temporary directory containing fake formgen build output
     */
    protected $staticDir = null;

    protected function setUp(): void
    {
        parent::setUp();

        $this->staticDir = $this->createStaticFiles();

        config([
            'portal.projectName'             => static::$testProjectName,
            'portal.forms.staticFileDirectory' => $this->staticDir,
            'portal.forms.staticServePath'     => static::$testServePath,
        ]);

        $this->portalPhp = $this->createPortalPhpWithMockedMeshClient();
    }

    protected function tearDown(): void
    {
        if ($this->staticDir !== null && is_dir($this->staticDir)) {
            $this->removeDirectory($this->staticDir);
        }

        parent::tearDown();
    }

    /**
     * Render emits the formgen web app markup: CSS includes, a root div with
     * the form UUID, and JS module script tags. The HTTP-based render flow
     * was replaced by static-file inclusion, so no Mesh request is made.
     */
    public function testFormGeneratorRenderEmitsFormgenAppMarkup()
    {
        $fg = new FormGeneratorHandler($this->portalPhp);
        $markup = $fg->render(static::$testFormUuid);

        $this->assertNotNull($markup);

        $this->assertStringContainsString(
            '<link rel="stylesheet" href="' . static::$testServePath
                . 'formgen/_expo/static/css/' . static::$testCssFile . '"/>',
            $markup
        );

        $this->assertStringContainsString(
            '<div id="root" tabindex="0" data-formID="' . static::$testFormUuid . '"',
            $markup
        );

        $this->assertStringContainsString('></div>', $markup);

        $this->assertStringContainsString(
            '<script type="module" src="' . static::$testServePath
                . 'formgen/_expo/static/js/web/' . static::$testJsFile . '"></script>',
            $markup
        );
    }

    /**
     * Optional formDataUuid, language, externalId and pluginName become
     * data-* attributes on the root div so the web app can pick them up
     * (e.g. to resume a partially-filled form via data-formDataID).
     */
    public function testFormGeneratorRenderIncludesOptionalAttributes()
    {
        $fg = new FormGeneratorHandler($this->portalPhp);
        $markup = $fg->render(
            static::$testFormUuid,
            static::$testFormDataUuid,
            static::$testLanguage,
            false,
            '',
            static::$testExternalId,
            static::$testPluginName
        );

        $this->assertNotNull($markup);
        $this->assertStringContainsString('data-formDataID="' . static::$testFormDataUuid . '"', $markup);
        $this->assertStringContainsString('data-culture="' . static::$testLanguage . '"', $markup);
        $this->assertStringContainsString('data-externalId="' . static::$testExternalId . '"', $markup);
        $this->assertStringContainsString('data-plugin-name="' . static::$testPluginName . '"', $markup);
    }

    /**
     * The data-formDataID attribute is only emitted when formDataUuid is
     * provided; the default render() call must not include it.
     */
    public function testFormGeneratorRenderOmitsFormDataIDByDefault()
    {
        $fg = new FormGeneratorHandler($this->portalPhp);
        $markup = $fg->render(static::$testFormUuid);

        $this->assertNotNull($markup);
        $this->assertStringNotContainsString('data-formDataID', $markup);
    }

    /**
     * When showResults is true, render fetches poll result JSON from the forms
     * plugin and renders it through the configured poll results Blade template.
     */
    public function testFormGeneratorRenderShowsPollResults()
    {
        config([
            'portal.forms.pollResultsTemplate' => 'poll-results',
        ]);

        $this->portalPhp
            ->getMeshClient(false)
            ->expects($this->once())
            ->method('request')
            ->with(
                'GET',
                static::$testProjectName . '/plugins/forms/form/' . static::$testFormUuid . '/results',
                $this->callback(function (array $options) {
                    return isset($options['headers']['Accept'])
                        && $options['headers']['Accept'] === 'application/json';
                })
            )
            ->willReturn(new Psr7Response(
                200,
                [],
                json_encode([
                    'results' => [
                        [
                            'element' => [
                                'name' => 'input1',
                            ],
                            'total' => 1,
                        ],
                    ],
                ])
            ));

        $fg = new FormGeneratorHandler($this->portalPhp);

        $markup = $fg->render(
            static::$testFormUuid,
            null,
            null,
            true,
            'wn'
        );

        $this->assertSame('<div>Element: input1 = Total: 1</div>', $markup);
    }

    /**
     * Render returns null when the configured static directory is missing,
     * so a misconfigured deployment does not produce broken markup.
     */
    public function testFormGeneratorRenderReturnsNullWhenStaticDirectoryMissing()
    {
        config(['portal.forms.staticFileDirectory' => '/nonexistent/path/' . uniqid()]);

        $fg = new FormGeneratorHandler($this->portalPhp);
        $this->assertNull($fg->render(static::$testFormUuid));
    }
    /**
     * The @fgRender blade directive forwards to FormGeneratorHandler::render()
     * and emits the formgen app markup into the view.
     */
    public function testBladeDirective()
    {
        $this->app->instance(PortalPhp::class, $this->portalPhp);

        $view = View::make('testFormDirective', ['formUuid' => static::$testFormUuid]);
        $contents = $view->render();

        $this->assertStringContainsString(
            'data-formID="' . static::$testFormUuid . '"',
            $contents
        );
        $this->assertStringContainsString('formgen/_expo/static/css/', $contents);
        $this->assertStringContainsString('formgen/_expo/static/js/web/', $contents);
    }

    /**
     * Create a temporary directory mirroring the expected formgen build
     * layout with one CSS and one JS file.
     *
     * @return string Absolute path to the temporary directory
     */
    protected function createStaticFiles(): string
    {
        $base = sys_get_temp_dir() . '/formgen-test-' . uniqid() . '/';
        $cssDir = $base . 'formgen/_expo/static/css/';
        $jsDir  = $base . 'formgen/_expo/static/js/web/';

        mkdir($cssDir, 0777, true);
        mkdir($jsDir, 0777, true);

        file_put_contents($cssDir . static::$testCssFile, '');
        file_put_contents($jsDir . static::$testJsFile, '');

        return $base;
    }

    /**
     * Recursively remove a directory and its contents.
     */
    protected function removeDirectory(string $path): void
    {
        $iterator = new \RecursiveIteratorIterator(
            new \RecursiveDirectoryIterator($path, \FilesystemIterator::SKIP_DOTS),
            \RecursiveIteratorIterator::CHILD_FIRST
        );

        foreach ($iterator as $entry) {
            $entry->isDir() ? rmdir($entry->getPathname()) : unlink($entry->getPathname());
        }

        rmdir($path);
    }
}
