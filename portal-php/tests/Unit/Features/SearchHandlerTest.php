<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\Search\SearchHandler;
use Gentics\PortalPhp\Features\Search\SearchHandlerLegacy;
use Gentics\PortalPhp\Features\Search\SearchHandlerMeshPlugin;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Arr;

class SearchHandlerTest extends TestCase
{
    /**
     * @var \Gentics\PortalPhp\PortalPhp|null Instance
     */
    protected $portalPhp         = null;

    protected function setUp(): void
    {
        parent::setUp();
        $this->portalPhp = $this->createPortalPhpWithMockedMeshClient();
    }

    /**
     * @return SearchHandlerLegacy
     * @throws \Throwable
     */
    protected function getLegacySearchHandler()
    {
        return new SearchHandlerLegacy($this->app[PortalPhp::class]);
    }

    /**
     * @return SearchHandlerMeshPlugin
     * @throws \Throwable
     */
    protected function getMeshPluginSearchHandler()
    {
        return new SearchHandlerMeshPlugin($this->app[PortalPhp::class]);
    }

    /**
     * Test prepareQuery method with different cases
     *
     * @throws \Throwable
     */
    public function testPrepareQuery()
    {
        // Simple Query
        $expectedQuery = 'testSearchQuery';

        // Prefixed Query
        $expectedQuery2 = 'Pre_testSearchQuery';

        config([
            "portal.elasticSearch.filterKeys.testKey" =>
                [
                    'query' => [
                        'bool' => [
                            'must' => [
                                [
                                    'multi_match' => [
                                        'query' => '{searchQuery}',
                                        'type' => 'phrase',
                                        'fields' => []
                                    ],
                                ],
                            ],
                        ],
                    ],
                ],
        ]);

        config([
            "portal.elasticSearch.filterKeys.testKey2" =>
                [
                    'query' => [
                        'bool' => [
                            'must' => [
                                [
                                    'multi_match' => [
                                        'query' => 'Pre_{searchQuery}',
                                        'type' => 'phrase',
                                        'fields' => []
                                    ],
                                ],
                            ],
                        ],
                    ],
                ],
        ]);

        $searchHandler = $this->getLegacySearchHandler();
        $preparedQuery = $searchHandler->prepareQuery(
            [
                'testKey' => $expectedQuery,
                'testKey2' => $expectedQuery,
            ]
        );

        $this->assertEquals(
            Arr::get($preparedQuery, 'query.bool.must.0.multi_match.query'),
            $expectedQuery
        );

        $this->assertEquals(
            Arr::get($preparedQuery, 'query.bool.must.1.multi_match.query'),
            $expectedQuery2
        );
    }

    /**
     * Test getSearchQueries method with mocked request
     *
     * @throws \Throwable
     */
    public function testGetSearchQueries()
    {
        $searchKey   = "testKey";
        $searchValue = "testValue";
        config(["portal.elasticSearch.filterKeys.{$searchKey}" => []]);

        $request = Request::create("http://example.com/anySearchRoute?{$searchKey}={$searchValue}", 'GET');

        $searchHandler = $this->getLegacySearchHandler();
        $searchQuery = $searchHandler->getSearchQueries($request);

        $this->assertEquals($searchQuery[$searchKey], $searchValue);
    }

    /**
     * Test runSearchApi method with mocked response and url validation
     * @throws \GuzzleHttp\Exception\GuzzleException
     * @throws \Throwable
     */
    public function testServerSideRequests()
    {
        // Search input test cases
        $queryCases = [
            [
                'searchInput' => [
                    'q' => 'Tesla',
                    't' => null,
                    'b' => null,
                    'l' => null,
                    'p' => 1
                ],
                'searchExpected' => [
                    'q' => 'Tesla',
                    'p' => 1
                ]
            ],
            [
                'searchInput' => [
                    'q' => 'Tesla',
                    't' => 'tag1,tag2',
                    'b' => null,
                    'l' => null,
                    'p' => 3
                ],
                'searchExpected' => [
                    'q' => 'Tesla',
                    't' => 'tag1,tag2',
                    'p' => 3
                ]
            ],
            [
                'searchInput' => [
                    'q' => 'Tesla',
                    't' => null,
                    'b' => 'TestBranch',
                    'l' => null,
                    'p' => 0
                ],
                'searchExpected' => [
                    'q' => 'Tesla',
                    'branch' => 'TestBranch',
                ]
            ],
            [
                'searchInput' => [
                    'q' => 'Tesla',
                    't' => null,
                    'b' => null,
                    'l' => 'de',
                    'p' => 0
                ],
                'searchExpected' => [
                    'q' => 'Tesla',
                    'l' => 'de',
                ]
            ],
            [
                'searchInput' => [
                    'q' => 'Tesla',
                    't' => null,
                    'b' => null,
                    'l' => 'de',
                    'p' => -1
                ],
                'searchExpected' => [
                    'q' => 'Tesla',
                    'l' => 'de',
                ]
            ],
            [
                'searchInput' => [
                    'q' => 'Tesla',
                    't' => null,
                    'b' => null,
                    'l' => 'de',
                    'p' => -1,
                    'wait' => false,
                ],
                'searchExpected' => [
                    'q' => 'Tesla',
                    'l' => 'de',
                    'wait' => 'false',
                ]
            ]
        ];

        // Api call count (reference to the array in the mock)
        $apiCalls = 0;
        $searchHandler = $this->getMeshPluginSearchHandler();

        $this->GetProxyRequestMock(function () use (&$apiCalls, $queryCases) {
            $queryString = http_build_query($queryCases[$apiCalls]['searchExpected']);
            return "portalphp/plugins/search/search?{$queryString}";
        }, 'testApiSearch.json');

        foreach ($queryCases as $caseKey => $queryCase) {
            $apiCalls = $caseKey;
            $queryParams = $queryCase['searchInput'];
            $sampleResponse = json_decode($this->loadResponseFile('testApiSearch.json'), true);

            // Wait for idle
            config(["portal.meshNoWaitForIdle" => false]);

            // Override if specified
            if (isset($queryParams['wait'])) {
                config(["portal.meshNoWaitForIdle" => !$queryParams['wait']]);
            }

            $responseBody = $searchHandler->runSearch(
                $queryParams['q'], // Query string
                $queryParams['t'], // Query tags
                $queryParams['b'], // Branch name
                $queryParams['l'], // Language
                $queryParams['p'] // Page
            );

            $this->assertEquals($responseBody, $sampleResponse);
        }
    }

    /**
     * Mock request method for search API test
     *
     * @param callable $callback
     * @param string $responseFile
     * @return mixed
     * @throws \Throwable
     */
    protected function getProxyRequestMock(callable $callback, $responseFile = "")
    {
        $responseContent = empty($responseFile) ? "" : $this->loadResponseFile($responseFile);

        return $this->portalPhp->getMeshClient()
            ->expects($this->any())
            ->method('request')
            ->willReturnCallback(function () use ($callback, $responseContent) {
                $shouldReturn = call_user_func_array($callback, []);
                $actuallyReturned = func_get_args()[1];
                $this->assertEquals($shouldReturn, $actuallyReturned);
                return new \GuzzleHttp\Psr7\Response(Response::HTTP_OK, [], $responseContent);
            });
    }
}
