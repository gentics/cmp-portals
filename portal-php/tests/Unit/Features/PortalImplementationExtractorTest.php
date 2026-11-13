<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\Features\PortalImplementation\PortalImplementationExtractor;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Filesystem\Filesystem;
use Illuminate\Support\Facades\Log;
use ZipArchive;

/**
 * Test cases for the PortalImplementationExtractor.
 */
class PortalImplementationExtractorTest extends TestCase
{
    /**
     * @var string
     */
    protected $baseDir;

    /**
     * @var array
     */
    protected $targets;

    /**
     * @var string
     */
    protected $stateDir;

    /**
     * @var PortalImplementationExtractor
     */
    protected $extractor;

    protected function setUp(): void
    {
        parent::setUp();

        $this->baseDir = sys_get_temp_dir() . '/portal-implementation-extractor-' . bin2hex(random_bytes(8));
        $this->targets = [
            'views' => $this->baseDir . '/my-views',
            'graphql' => $this->baseDir . '/my-graphql',
            'lang' => $this->baseDir . '/my-lang',
            'static' => $this->baseDir . '/my-static',
        ];
        $this->stateDir = $this->baseDir . '/state';
        $this->extractor = new PortalImplementationExtractor();
    }

    protected function tearDown(): void
    {
        (new Filesystem())->deleteDirectory($this->baseDir);

        parent::tearDown();
    }

    /**
     * Test that the known folders are extracted into their target folders, including subfolders.
     */
    public function testFoldersExtractedToTargets()
    {
        $extracted = $this->extractor->extract($this->zip([
            'views/page.blade.php' => 'page',
            'views/partials/header.blade.php' => 'header',
            'graphql/loadByPath.graphql' => 'query',
            'lang/en/messages.php' => 'messages',
            'static/css/main.css' => 'css',
        ]), $this->targets, $this->stateDir);

        $this->assertEquals(['views', 'graphql', 'lang', 'static'], $extracted);
        $this->assertContent('my-views/page.blade.php', 'page');
        $this->assertContent('my-views/partials/header.blade.php', 'header');
        $this->assertContent('my-graphql/loadByPath.graphql', 'query');
        $this->assertContent('my-lang/en/messages.php', 'messages');
        $this->assertContent('my-static/css/main.css', 'css');
        $this->assertSame([], glob($this->stateDir . '/tmp-*'), 'Temporary folder must be removed');
    }

    /**
     * Test that unknown folders and files in the root of the zip are ignored with one warning each.
     */
    public function testUnknownEntriesIgnored()
    {
        Log::spy();

        $extracted = $this->extractor->extract($this->zip([
            'views/page.blade.php' => 'page',
            'templates/page.hbs' => 'other',
            'templates/other.hbs' => 'other',
            'readme.txt' => 'readme',
        ]), $this->targets, $this->stateDir);

        $this->assertEquals(['views'], $extracted);
        $this->assertContent('my-views/page.blade.php', 'page');
        $this->assertFileDoesNotExist($this->baseDir . '/templates');
        $this->assertFileDoesNotExist($this->baseDir . '/readme.txt');
        Log::shouldHaveReceived('warning')->twice();
    }

    /**
     * Test that a folder public in the package is ignored, static files must be in the folder static.
     */
    public function testPublicFolderIgnored()
    {
        Log::spy();
        $this->write('my-static/site.css', 'instance site');

        $extracted = $this->extractor->extract($this->zip([
            'public/site.css' => 'implementation site',
            'public/js/app.js' => 'app',
        ]), $this->targets, $this->stateDir);

        $this->assertEquals([], $extracted);
        $this->assertContent('my-static/site.css', 'instance site');
        $this->assertFileDoesNotExist($this->baseDir . '/my-static/js/app.js');
        $this->assertFileDoesNotExist($this->baseDir . '/public');
        Log::shouldHaveReceived('warning')->once();
    }

    /**
     * Test that the existing contents of a target folder are replaced, and that target folders missing in the zip
     * are emptied (except the static folder), but not created.
     */
    public function testExistingFilesReplaced()
    {
        $this->write('my-views/page.blade.php', 'old page');
        $this->write('my-views/old/removed.blade.php', 'removed');
        $this->write('my-graphql/local.graphql', 'local query');
        $this->write('my-graphql/fragments/fragment.graphql', 'fragment');
        $this->write('my-static/index.php', 'index');

        $this->extractor->extract($this->zip(['views/page.blade.php' => 'new page']), $this->targets, $this->stateDir);

        $this->assertContent('my-views/page.blade.php', 'new page');
        $this->assertFileDoesNotExist($this->baseDir . '/my-views/old', 'Files not contained in the package must be removed');
        $this->assertDirectoryExists($this->baseDir . '/my-graphql', 'Folder missing in the package must still exist');
        $this->assertSame([], (new Filesystem())->allFiles($this->baseDir . '/my-graphql', true), 'Folder missing in the package must be empty');
        $this->assertFileDoesNotExist($this->baseDir . '/my-lang', 'Non-existing target folders must not be created');
        $this->assertContent('my-static/index.php', 'index');
    }

    /**
     * Test that an invalid zip file leaves all target folders untouched.
     */
    public function testInvalidZipLeavesFoldersUntouched()
    {
        $this->write('my-graphql/local.graphql', 'local query');
        $this->write('invalid.zip', 'no zip file');

        try {
            $this->extractor->extract($this->baseDir . '/invalid.zip', $this->targets, $this->stateDir);
            $this->fail('Extraction must fail');
        } catch (PortalPhpException $e) {
            $this->assertStringContainsString('not a valid zip file', $e->getMessage());
        }

        $this->assertContent('my-graphql/local.graphql', 'local query');
    }

    /**
     * Test that entries pointing outside their folder are rejected, and nothing is extracted.
     */
    public function testZipSlipRejected()
    {
        $this->write('my-graphql/local.graphql', 'local query');

        try {
            $this->extractor->extract($this->zip([
                'views/page.blade.php' => 'page',
                'views/../../evil.txt' => 'evil',
            ]), $this->targets, $this->stateDir);
            $this->fail('Extraction must fail');
        } catch (PortalPhpException $e) {
            $this->assertStringContainsString('points outside', $e->getMessage());
        }

        $this->assertFileDoesNotExist($this->baseDir . '/my-views');
        $this->assertFileDoesNotExist($this->baseDir . '/evil.txt');
        $this->assertFileDoesNotExist(dirname($this->baseDir) . '/evil.txt');
        $this->assertContent('my-graphql/local.graphql', 'local query');
    }

    /**
     * Test that the static folder of the package is copied over the static folder of the portal: files of the
     * portal instance are kept, overwritten files are restored when the portal implementation no longer contains
     * them, and files of the portal implementation are removed when it no longer contains them.
     */
    public function testStaticOverlay()
    {
        $this->write('my-static/index.php', 'index');
        $this->write('my-static/css/site.css', 'instance site');

        // The portal implementation overwrites site.css and adds app.js
        $this->extractor->extract($this->zip([
            'static/css/site.css' => 'implementation site',
            'static/js/app.js' => 'app 1',
        ]), $this->targets, $this->stateDir);

        $this->assertContent('my-static/index.php', 'index');
        $this->assertContent('my-static/css/site.css', 'implementation site');
        $this->assertContent('my-static/js/app.js', 'app 1');

        // The portal implementation no longer contains site.css, and changes app.js
        $this->extractor->extract($this->zip(['static/js/app.js' => 'app 2']), $this->targets, $this->stateDir);

        $this->assertContent('my-static/index.php', 'index');
        $this->assertContent('my-static/css/site.css', 'instance site');
        $this->assertContent('my-static/js/app.js', 'app 2');

        // The portal implementation has no static folder at all
        $this->extractor->extract($this->zip(['views/page.blade.php' => 'page']), $this->targets, $this->stateDir);

        $this->assertContent('my-static/index.php', 'index');
        $this->assertContent('my-static/css/site.css', 'instance site');
        $this->assertFileDoesNotExist($this->baseDir . '/my-static/js/app.js');
    }

    /**
     * Test that a file overwritten by several versions of the portal implementation is restored to the file of the
     * portal instance, not to an older version of the portal implementation.
     */
    public function testStaticOverlayRestoresInstanceFile()
    {
        $this->write('my-static/robots.txt', 'instance robots');

        $this->extractor->extract($this->zip(['static/robots.txt' => 'robots 1']), $this->targets, $this->stateDir);
        $this->extractor->extract($this->zip(['static/robots.txt' => 'robots 2']), $this->targets, $this->stateDir);

        $this->assertContent('my-static/robots.txt', 'robots 2');

        $this->extractor->extract($this->zip(['static/other.txt' => 'other']), $this->targets, $this->stateDir);

        $this->assertContent('my-static/robots.txt', 'instance robots');
    }

    /**
     * Assert the content of a file relative to the base folder.
     *
     * @param string $path
     * @param string $expected
     */
    protected function assertContent(string $path, string $expected)
    {
        $this->assertFileExists($this->baseDir . '/' . $path);
        $this->assertEquals($expected, file_get_contents($this->baseDir . '/' . $path));
    }

    /**
     * Write a file relative to the base folder.
     *
     * @param string $path
     * @param string $content
     */
    protected function write(string $path, string $content)
    {
        $file = $this->baseDir . '/' . $path;
        (new Filesystem())->ensureDirectoryExists(dirname($file));
        file_put_contents($file, $content);
    }

    /**
     * Create a zip file with the given entries.
     *
     * @param array $entries Map of entry names to contents
     * @return string Path to the zip file
     */
    protected function zip(array $entries): string
    {
        (new Filesystem())->ensureDirectoryExists($this->baseDir);
        $file = $this->baseDir . '/package-' . bin2hex(random_bytes(4)) . '.zip';
        $zip = new ZipArchive();
        $zip->open($file, ZipArchive::CREATE);

        foreach ($entries as $name => $content) {
            $zip->addFromString($name, $content);
        }

        $zip->close();

        return $file;
    }
}
