<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\Mesh\Client\MeshClient;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlLoader;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadNavigation;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadUser;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Support\Facades\Cache;
use PHPUnit\Framework\MockObject\MockObject;
use Psr\Container\ContainerExceptionInterface;
use Psr\Container\NotFoundExceptionInterface;

class MeshTest extends TestCase
{
    public static $fragmentsBundle = [
        'loaderTest.graphql',
        'fragments/fragment1.graphql',
        'fragments/fragment3.graphql',
        'fragments/fragmentsFragment.graphql',
    ];

    public static $testProjectName = 'testproject';
    public static $testUsername = 'testuser';
    public static $testRolesHash = 'd34db33f';

    /**
     * Tests loading navigation from GraphQL cached and non-cached
     *
     * @throws \Throwable
     */
    public function testLoadNavigation()
    {
        config([
            "portal.projectName" => static::$testProjectName
        ]);

        // Mocked Mesh for LoadUser
        $portalPhpUser = new PortalPhp();
        /** @var MeshClient|MockObject $mockedMeshClient */
        $mockedMeshClient = $this->createMock(MeshClient::class);
        $portalPhpUser->setMeshClient($mockedMeshClient);
        $portalPhpUser->setAdminMeshClient($mockedMeshClient);

        $this->app->instance(LoadUser::class, new LoadUser($portalPhpUser));

        $userResponseJson = $this->loadResponseFile("/testLoadUser.json");

        $portalPhpUser->getMeshClient()
            ->expects($this->any())
            ->method("graphQL")
            ->with($this->equalTo(static::$testProjectName))
            ->willReturn($this->createMockedMeshRequest($userResponseJson));

        $portalPhp    = $this->createPortalPhpWithMockedMeshClient();
        $this->app->instance(PortalPhp::class, $portalPhp);

        $responseJson = $this->loadResponseFile("/testLoadNavigation.json");

        $portalPhp->getMeshClient()
            ->expects($this->any())
            ->method("graphQL")
            ->with($this->equalTo(static::$testProjectName))
            ->willReturn($this->createMockedMeshRequest($responseJson));

        // Cache must be null first time
        $this->assertNull($this->getNavigationCache($portalPhp));

        // Fetch without cache
        Mesh::request(LoadNavigation::class)
            ->fetch();

        // Cache must be still null
        $this->assertNull($this->getNavigationCache($portalPhp));

        // Fetch with cache
        $response = Mesh::request(LoadNavigation::class)
            ->fetchCached()
            ->toArray()['data'];

        // Cached data equals to response
        $this->assertEquals($this->getNavigationCache($portalPhp)->toArray()['data'], $response);
        $this->assertEquals(
            count($response['project']['rootNode']['children']['elements']),
            count(json_decode($responseJson, true)['data']['project']['rootNode']['children']['elements'])
        );
    }

    /**
     * Tests loading user /me from GraphQL cached and non-cached
     *
     * @throws \Throwable
     */
    public function testLoadUser()
    {
        config([
            "portal.projectName" => static::$testProjectName
        ]);

        $portalPhp    = $this->createPortalPhpWithMockedMeshClient();
        $this->app->instance(PortalPhp::class, $portalPhp);

        $responseJson = $this->loadResponseFile("/testLoadUser.json");

        $portalPhp->getMeshClient()
            ->expects($this->any())
            ->method("graphQL")
            ->with($this->equalTo(static::$testProjectName))
            ->willReturn($this->createMockedMeshRequest($responseJson));

        // Cache must be null first time
        $this->assertNull($this->getUserCache($portalPhp));

        // Fetch without cache
        Mesh::request(LoadUser::class)
                    ->fetch();

        // Cache must be still null
        $this->assertNull($this->getUserCache($portalPhp));

        // Fetch with cache
        $response = Mesh::request(LoadUser::class)
            ->fetchCached()
            ->toArray()['data'];

        // Cached data equals to response
        $this->assertEquals($this->getUserCache($portalPhp)->toArray()['data'], $response);

        $this->assertEquals($response['me']['username'], static::$testUsername);
        $this->assertEquals($response['me']['rolesHash'], static::$testRolesHash);
    }

    public function testGraphqlLoader()
    {
        $placeholders = [
            '%contentSchema%' => 'testSchema'
        ];

        $gql = $this->app->make(GraphqlLoader::class)
            ->useCache(false)
            ->fromTemplate('loaderTest')
            ->setPlaceholders($placeholders)
            ->setVariables([
                'variable1' => 'value'
            ])
            ->buildAsQuery();

        $this->assertEquals($gql['variables']['variable1'], 'value');
        $this->assertEquals($gql['query'], $this->getFragmentsBundle($placeholders));
    }

    /**
     * @throws ContainerExceptionInterface
     * @throws NotFoundExceptionInterface
     */
    protected function getNavigationCache(PortalPhp $portal)
    {
        $branchName = 'null';
        $key = static::$testRolesHash . $branchName . $portal->getUserLanguage();
        return Cache::get('mesh:graphql_nav:' . sha1($key) . $portal->getProjectName());
    }

    protected function getUserCache(PortalPhp $portal)
    {
        return Cache::get('mesh:graphql_user:' . sha1('anonymous') . $portal->getProjectName());
    }

    protected function getFragmentsBundle($replace = [])
    {
        $fragments = '';

        foreach (static::$fragmentsBundle as $fragment) {
            $fragments .= file_get_contents(resource_path('graphql/' . $fragment));
        }

        $fragments = str_replace(array_keys($replace), array_values($replace), $fragments);
        return $fragments;
    }
}
