<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\Mesh\Client\MeshClient;
use Gentics\Mesh\Client\Rest\MeshRequest;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\Features\PortalImplementation\Console\SyncCommand;
use Gentics\PortalPhp\Features\PortalImplementation\PortalImplementationService;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Tests\TestCase;
use GuzzleHttp\Psr7\Response as GuzzleResponse;
use Illuminate\Console\Scheduling\Schedule;
use Illuminate\Filesystem\Filesystem;
use Illuminate\Support\Facades\Artisan;
use PHPUnit\Framework\MockObject\MockObject;
use Psr\Http\Message\RequestInterface;
use ZipArchive;

/**
 * Test cases for the PortalImplementationService and the portal:implementation:sync command.
 */
class PortalImplementationServiceTest extends TestCase
{
    /**
     * @var string
     */
    protected $baseDir;

    /**
     * @var MeshClient|MockObject
     */
    protected $meshClient;

    /**
     * @var PortalImplementationService
     */
    protected $service;

    /**
     * @var array|null Node returned by the GraphQL query, null for none
     */
    protected $node;

    /**
     * @var array|null Errors returned by the GraphQL query
     */
    protected $graphqlErrors;

    /**
     * @var array GraphQL requests sent to Mesh (project, body, parameters)
     */
    protected $graphqlRequests = [];

    /**
     * @var array Download requests sent to Mesh (path, query)
     */
    protected $downloads = [];

    /**
     * @var string Content of the package
     */
    protected $package;

    protected function getEnvironmentSetUp($app)
    {
        parent::getEnvironmentSetUp($app);

        $app['config']->set('portal.portalImplementation', 'my-impl');
        $app['config']->set('portal.projectName', 'my-project');
    }

    protected function setUp(): void
    {
        parent::setUp();

        $this->baseDir = sys_get_temp_dir() . '/portal-implementation-service-' . bin2hex(random_bytes(8));

        /** @var PortalPhp $portalPhp */
        $portalPhp = $this->createPortalPhpWithMockedMeshClient();
        $this->meshClient = $portalPhp->getAdminMeshClient();

        $this->meshClient->method('graphQL')->willReturnCallback(function ($project, $body, $parameters) {
            $this->graphqlRequests[] = [$project, $body, $parameters];
            $data = ['data' => ['rootNode' => ['children' => ['elements' => $this->node ? [$this->node] : []]]]];

            if ($this->graphqlErrors) {
                $data = ['errors' => $this->graphqlErrors];
            }

            return $this->createMockedMeshRequest(json_encode($data));
        });

        $this->meshClient->method('findNodeByUuid')->willReturnCallback(function ($project, $uuid) {
            return new MeshRequest($this->meshClient, 'GET', 'http://mesh:8080/api/v1/' . $project . '/nodes/' . $uuid, [], null);
        });

        $this->meshClient->method('send')->willReturnCallback(function (RequestInterface $request, array $options) {
            $this->downloads[] = [$request->getUri()->getPath(), $options['query']];
            file_put_contents($options['sink'], $this->package);

            return new GuzzleResponse(200);
        });

        $this->service = new PortalImplementationService(
            $portalPhp,
            [
                'views' => $this->baseDir . '/views',
                'graphql' => $this->baseDir . '/graphql',
                'lang' => $this->baseDir . '/lang',
                'public' => $this->baseDir . '/public',
            ],
            $this->baseDir . '/state'
        );
        $this->app->instance(PortalImplementationService::class, $this->service);
    }

    protected function tearDown(): void
    {
        (new Filesystem())->deleteDirectory($this->baseDir);

        parent::tearDown();
    }

    /**
     * Test that the GraphQL query filters by the implementation schema and the keyword.
     */
    public function testQuery()
    {
        $this->service->sync();

        $this->assertCount(1, $this->graphqlRequests);
        [$project, $body, $parameters] = $this->graphqlRequests[0];

        $this->assertEquals('my-project', $project);
        $this->assertEquals(['version' => 'published'], $parameters);
        $this->assertEquals(['keyword' => 'my-impl'], $body['variables']);
        $this->assertStringContainsString('schema: {is: portalphp_implementation}', $body['query']);
        $this->assertStringContainsString(
            'fields: {portalphp_implementation: {keyword: {equals: $keyword}}}',
            $body['query']
        );
        $this->assertStringContainsString('... on portalphp_implementation', $body['query']);
    }

    /**
     * Test that a missing portal implementation keeps the local files.
     */
    public function testNotFoundKeepsLocalFiles()
    {
        $this->write('views/local.blade.php', 'local');

        $this->assertEquals(PortalImplementationService::STATUS_NOT_FOUND, $this->service->sync());
        $this->assertContent('views/local.blade.php', 'local');
        $this->assertEmpty($this->downloads);
    }

    /**
     * Test that a portal implementation without package keeps the local files.
     */
    public function testNoPackageKeepsLocalFiles()
    {
        $this->write('views/local.blade.php', 'local');
        $this->node = ['uuid' => 'uuid1', 'language' => 'en', 'fields' => ['package' => null]];

        $this->assertEquals(PortalImplementationService::STATUS_NOT_FOUND, $this->service->sync());
        $this->assertContent('views/local.blade.php', 'local');
        $this->assertEmpty($this->downloads);
    }

    /**
     * Test that the package is downloaded and extracted, and only downloaded again when its checksum changed.
     */
    public function testUpdateOnlyWhenChanged()
    {
        $this->write('views/local.blade.php', 'local');
        $this->setPackage('checksum1', ['views/page.blade.php' => 'version 1']);

        $this->assertEquals(PortalImplementationService::STATUS_UPDATED, $this->service->sync());
        $this->assertContent('views/page.blade.php', 'version 1');
        $this->assertFileDoesNotExist($this->baseDir . '/views/local.blade.php');
        $this->assertEquals(
            [['/api/v1/my-project/nodes/uuid1/binary/package', ['version' => 'published', 'lang' => 'en']]],
            $this->downloads
        );

        $this->assertEquals(PortalImplementationService::STATUS_UNCHANGED, $this->service->sync());
        $this->assertCount(1, $this->downloads, 'Unchanged package must not be downloaded again');

        $this->setPackage('checksum2', ['views/page.blade.php' => 'version 2']);

        $this->assertEquals(PortalImplementationService::STATUS_UPDATED, $this->service->sync());
        $this->assertContent('views/page.blade.php', 'version 2');
        $this->assertCount(2, $this->downloads);
        $this->assertSame([], glob($this->baseDir . '/state/package-*'), 'Downloaded package must be removed');
    }

    /**
     * Test that GraphQL errors fail the synchronization and keep the local files.
     */
    public function testGraphqlErrorsKeepLocalFiles()
    {
        $this->write('views/local.blade.php', 'local');
        $this->graphqlErrors = [['message' => 'Validation error']];

        try {
            $this->service->sync();
            $this->fail('Synchronization must fail');
        } catch (PortalPhpException $e) {
            $this->assertStringContainsString('Validation error', $e->getMessage());
        }

        $this->assertContent('views/local.blade.php', 'local');
    }

    /**
     * Test that an invalid package fails the synchronization, keeps the local files and is retried with the next
     * synchronization.
     */
    public function testInvalidPackageRetried()
    {
        $this->write('views/local.blade.php', 'local');
        $this->setPackage('checksum1', []);
        $this->package = 'no zip file';

        try {
            $this->service->sync();
            $this->fail('Synchronization must fail');
        } catch (PortalPhpException $e) {
            $this->assertStringContainsString('not a valid zip file', $e->getMessage());
        }

        $this->assertContent('views/local.blade.php', 'local');

        $this->setPackage('checksum1', ['views/page.blade.php' => 'page']);

        $this->assertEquals(PortalImplementationService::STATUS_UPDATED, $this->service->sync());
        $this->assertContent('views/page.blade.php', 'page');
    }

    /**
     * Test that nothing is loaded, when no portal implementation is configured.
     */
    public function testDisabled()
    {
        config(['portal.portalImplementation' => null]);

        $this->assertFalse($this->service->isEnabled());
        $this->assertEquals(PortalImplementationService::STATUS_DISABLED, $this->service->sync());
        $this->assertEmpty($this->graphqlRequests);
    }

    /**
     * Test the output and exit codes of the command.
     */
    public function testCommand()
    {
        $this->setPackage('checksum1', ['views/page.blade.php' => 'page']);

        $this->assertEquals(0, Artisan::call('portal:implementation:sync'));
        $this->assertStringContainsString('Portal implementation my-impl was updated', Artisan::output());
        $this->assertContent('views/page.blade.php', 'page');

        $this->assertEquals(0, Artisan::call('portal:implementation:sync'));
        $this->assertStringContainsString('Portal implementation my-impl is unchanged', Artisan::output());

        $this->graphqlErrors = [['message' => 'Validation error']];

        $this->assertEquals(1, Artisan::call('portal:implementation:sync'));
        $this->assertStringContainsString('Validation error', Artisan::output());
    }

    /**
     * Test that the command is scheduled every minute, when a portal implementation is configured.
     */
    public function testScheduled()
    {
        $events = array_values(array_filter(
            $this->app->make(Schedule::class)->events(),
            function ($event) {
                return str_contains($event->command, 'portal:implementation:sync');
            }
        ));

        $this->assertCount(1, $events);
        $this->assertEquals('* * * * *', $events[0]->expression);
        $this->assertTrue($events[0]->withoutOverlapping);
    }

    /**
     * Set the node returned by the GraphQL query and the content of its package.
     *
     * @param string $checksum
     * @param array $entries Map of entry names to contents
     */
    protected function setPackage(string $checksum, array $entries)
    {
        $this->node = [
            'uuid' => 'uuid1',
            'language' => 'en',
            'fields' => ['package' => ['binaryUuid' => 'binary1', 'fileName' => 'impl.zip', 'sha512sum' => $checksum]],
        ];

        $file = tempnam(sys_get_temp_dir(), 'portal-implementation-package-');
        $zip = new ZipArchive();
        $zip->open($file, ZipArchive::OVERWRITE);

        foreach ($entries as $name => $content) {
            $zip->addFromString($name, $content);
        }

        if (empty($entries)) {
            $zip->addEmptyDir('views');
        }

        $zip->close();
        $this->package = file_get_contents($file);
        unlink($file);
    }

    /**
     * @param string $path
     * @param string $expected
     */
    protected function assertContent(string $path, string $expected)
    {
        $this->assertFileExists($this->baseDir . '/' . $path);
        $this->assertEquals($expected, file_get_contents($this->baseDir . '/' . $path));
    }

    /**
     * @param string $path
     * @param string $content
     */
    protected function write(string $path, string $content)
    {
        $file = $this->baseDir . '/' . $path;
        (new Filesystem())->ensureDirectoryExists(dirname($file));
        file_put_contents($file, $content);
    }
}
