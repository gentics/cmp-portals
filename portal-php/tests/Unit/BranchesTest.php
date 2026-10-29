<?php

namespace Gentics\PortalPhp\Tests\Unit;

use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Http\Request;

/**
 * Class BranchesTest
 * @package Gentics\PortalPhp\Tests\Unit
 */
class BranchesTest extends TestCase
{
    /**
     * @var string
     */
    protected $projectName = 'test';

    /**
     * @var array
     */
    protected $httpHosts = [
        'channel2.example.domain' => 'Channel_2',
        'channel.example.domain'  => 'Channel_of_Portal_PHP_Reference',
        'master.example.domain'   => 'Portal_PHP_Reference',
    ];

    /**
     * @var array
     */
    protected $pageDirectories = [
        '/c2' => 'Channel_2',
        '/c'  => 'Channel_of_Portal_PHP_Reference',
        '/m'  => 'Portal_PHP_Reference',
    ];

    /**
     * Set up
     */
    protected function setUp(): void
    {
        parent::setUp();

        config(["portal.projectName" => $this->projectName]);
    }

    /**
     * Test data, see parameters of testLoadByPathWithBranch
     * @return array
     */
    public static function branchesDataProvider()
    {
        return [
            [true, true,  true,  "1.0",  "channel.domain",  "/c/page.html",  "Channel_of_Portal_PHP_Reference_1.0"],
            [true, true,  true,  "1.0",  "channel2.domain", "/c2/page.html", "Channel_2_1.0"],
            [true, true,  true,  "1.0",  "master.domain",   "/page.html",    "Portal_PHP_Reference"],
            [true, true,  true,  "2.0",  "master.domain",   "/page.html",    "Portal_PHP_Reference_2.0"],
            [true, true,  false, "1.0",  "channel.domain",  "/c/page.html",  "Channel_of_Portal_PHP_Reference_1.0"],
            [true, false, true,  "1.0",  "channel.domain",  "/c/page.html",  "Channel_of_Portal_PHP_Reference_1.0"],
            [true, true,  true,  null,   "channel.domain",  "/c/page.html",  "Channel_of_Portal_PHP_Reference_2.0"],
        ];
    }

    /**
     * Test loading a path with a specific request host, request path and implementationVersion
     * @dataProvider branchesDataProvider
     * @param bool $branchesEnabled
     * @param bool $matchHttpHost
     * @param bool $matchPageDirectory
     * @param string|null $implementationVersion
     * @param string|null $expectedBranchName
     * @param string $httpHost
     * @param string $path
     */
    public function testLoadByPathWithBranch(
        bool $branchesEnabled,
        bool $matchHttpHost,
        bool $matchPageDirectory,
        ?string $implementationVersion,
        string $httpHost,
        string $path,
        ?string $expectedBranchName
    ) {
        config(["portal.branches.enabled"               => $branchesEnabled]);
        config(["portal.branches.matchHttpHost"         => $matchHttpHost]);
        config(["portal.branches.matchPageDirectory"    => $matchPageDirectory]);
        config(["portal.branches.implementationVersion" => $implementationVersion]);

        $this->mockProjectBranches();
        $portalPhp = $this->createPortalPhpWithMockedMeshClient();

        // Change the host name
        $this->app->instance('request', Request::create("http://" . $httpHost));

        // Mock graphQL and add assert parameters
        $portalPhp->getMeshClient()
            ->expects($this->once())
            ->method("graphQL")
            ->with(
                $this->equalTo($this->projectName),
                $this->isType("array"),
                $this->callback(function (array $parameters) use ($expectedBranchName) {
                    $this->assertEquals($expectedBranchName, $parameters['branch'] ?? null);
                    return true;
                })
            )
            ->willReturn($this->createMockedMeshRequest($this->loadResponseFile("/testLoadByPath.json")));

        // Load from Rest API
        $portalPhp->loadByPath($path);
    }

    /**
     * Mock the findBranches method of the MeshClient
     */
    protected function mockProjectBranches()
    {
        $mockedDataProvider = $this->createMock(DataProvider::class);

        $this->app->singleton(DataProvider::class, function () use ($mockedDataProvider) {
            return $mockedDataProvider;
        });

        $branches = json_decode($this->loadResponseFile("/findBranches.json"), true)['data'];

        $mockedDataProvider
            ->method("retrieveBranches")
            ->willReturn($branches);
    }
}
