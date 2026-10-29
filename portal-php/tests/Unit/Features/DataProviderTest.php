<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\PortalPhp;
use Gentics\Mesh\Client\MeshClient;
use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Support\Facades\Cache;

class DataProviderTest extends TestCase
{
    /**
     * Tests if static method calling works for dataProvider key
     */
    public function testStaticMethodCalling()
    {
        config(['portal.dataProvider.meshData' => [
                'config' => 'test/Location.html'
            ]
        ]);

        $mock = $this->createMock(DataProvider::class);
        $mock
            ->expects($this->once())
            ->method('retrieveMeshData')
            ->with(
                $this->equalTo('config')
            );

        $this->app->instance(DataProvider::class, $mock);

        DataProvider::getConfig();
    }

    /**
     * Test retrieving the Mesh branches from the default configured project
     * @throws \Throwable
     */
    public function testRetrieveMeshBranchesFromCache()
    {
        $projectName = 'test';
        config(["portal.projectName" => $projectName]);

        $portalPhp    = $this->createPortalPhpWithMockedMeshClient();
        $dataProvider = new DataProvider($portalPhp);

        $branchesTestJson = $this->loadResponseFile("/findBranches.json");
        $branchesTestData = json_decode($branchesTestJson, true)['data'];

        $portalPhp->getMeshClient()
            ->expects($this->once())
            ->method("findBranches")
            ->with($this->equalTo($projectName))
            ->willReturn($this->createMockedMeshRequest($branchesTestJson));

        // Load from Rest API
        $branches = $dataProvider->retrieveBranches();
        $this->assertEquals($branchesTestData, $branches, "The returned branches have to match the test data");

        // Load again, but from cache
        $branches = $dataProvider->retrieveBranches();
        $this->assertEquals($branchesTestData, $branches, "The returned branches have to match the test data");
    }

    /**
     * Test retrieving the Mesh DataProviders ensured the cache is unique for all branches
     * @throws \Throwable
     */
    public function testRetrieveMeshDataFromCache()
    {
        $projectName = 'test';
        config([
            "portal.projectName" => $projectName,
            'portal.dataProvider.meshData' => [
                'test' => 'test/Location.html'
            ]
        ]);

        $testBranches = [
            'branch1' => 'cachedBranch1.json',
            'branch2' => 'cachedBranch2.json',
        ];

        // Loop over the test branches, fetch them through the mocked classes and allow them to get cached
        foreach ($testBranches as $branch => $json) {
            $branchTestJson = $this->loadResponseFile($json);
            $branchTestData = json_decode($branchTestJson, true)['data'];
            $portalPhp = $this->GetPortalPhpBranchesMock($branch, $branchTestJson);
            $this->app[PortalPhp::class] = $portalPhp;

            $dataProvider = new DataProvider($portalPhp);

            $this->assertFalse(Cache::has('dataprovider_meshdata_' . $branch . '_test'));

            $getMeshData = $dataProvider->retrieveMeshData('test', null, false);
            $this->assertEquals($getMeshData, $branchTestData, "Branch Data for ' . $branch . ' has different value than expected");
            $this->assertTrue(Cache::has('dataprovider_meshdata_' . $branch . '_test'));
        }

        // Check if the cached data differs
        $this->assertNotEquals(Cache::get('dataprovider_meshdata_branch1_test'), Cache::get('dataprovider_meshdata_branch2_test'));
    }

    private function GetPortalPhpBranchesMock($response, $json)
    {
        $portalPhp = $this->getMockBuilder(PortalPhp::class)
            ->onlyMethods(['findBranchNameFromRequest'])
            ->getMock();
        $mockedMeshClient = $this->createMock(MeshClient::class);
        $portalPhp->setMeshClient($mockedMeshClient);
        $portalPhp->setAdminMeshClient($mockedMeshClient);
        $portalPhp->getMeshClient()
            ->expects($this->once())
            ->method("graphQL")
            ->willReturn($this->createMockedMeshRequest($json));
        $portalPhp
            ->expects($this->any())
            ->method('findBranchNameFromRequest')
            ->willReturn($response);
        
        return $portalPhp;
    }
}
