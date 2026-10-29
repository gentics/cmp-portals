<?php

namespace Gentics\PortalPhp\Features\Search;

use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\Search;
use Gentics\PortalPhp\PortalPhp;
use \Illuminate\Http\Request;
use Gentics\PortalPhp\Features\Mesh\Mesh;

/**
 * Class SearchHandlerLegacy
 * @since 1.1.0
 * @package Gentics\PortalPhp\Features\Search
 */
class SearchHandlerLegacy extends SearchHandler
{
    /**
     * Predefined query keys, and their Elastic Search params
     *
     * @var array
     * @deprecated 1.1.0 This property is not used anymore by SearchHandler
     */
    protected $filterKeys = [];

    /**
     * Results per page
     *
     * @var int
     * @deprecated 1.1.0 This property is not used anymore by SearchHandler
     */
    protected $perPage = 25;

    /**
     * SearchHandler constructor.
     *
     * @param PortalPhp $portalPhp
     * @throws \Throwable
     */
    public function __construct(PortalPhp $portalPhp)
    {
        parent::__construct($portalPhp);
        $this->filterKeys = config('portal.elasticSearch.filterKeys', $this->filterKeys);
        $this->perPage = config('portal.elasticSearch.perPage', $this->perPage);
    }

    /**
     * It takes the query params from request and runs search, also
     * handles pagination.
     *
     * @param Request $request
     * @param int $page
     * @param string|null $branchName
     * @return array Gentics\PortalPhp\Features\Search\getContext
     * @throws \Throwable
     */
    public function handle(Request $request, int $page = 1, ?string $branchName = null)
    {
        $result = [];
        $searchQuery = $this->getSearchQueries($request);

        if ($branchName !== null) {
            $dataProvider = app()->make(DataProvider::class);
            $branches = $dataProvider->retrieveBranches();

            foreach ($branches as $branch) {
                if ($branch['name'] === $branchName) {
                    $this->branch = $branch;
                }
            }
        }

        if (!empty($searchQuery)) {
            // Run search with the queries, if there is any
            $result = $this->runSearch($searchQuery, $page, $this->perPage);
        }

        // Prepare results array
        $context = $this->prepareContext($result, $page, $searchQuery ?? []);

        return $context;
    }

    /**
     * Prepares the GraphQL query, calls Mesh with the query, then returns the response data
     *
     * @param array $searchQuery|[]
     * @param int $pageNumber|1
     * @param int $perPage|1
     * @param array $elasticSearchQuery|null
     * @return array Results array from Mesh response
     * @throws \Throwable
     */
    public function runSearch(
        array $searchQuery = [],
        int $pageNumber = 1,
        int $perPage = 1,
        array $elasticSearchQuery = null
    ) {
        // If no $elasticSearchQuery override then getBaseQuery
        if (is_null($elasticSearchQuery)) {
            $elasticSearchQuery = $this->getBaseQuery();
        }

        // Prepare queries from config with query params
        $preparedQueries = $this->prepareQuery($searchQuery);

        if (!empty($preparedQueries)) {
            $elasticSearchQuery = array_merge_recursive($elasticSearchQuery, $preparedQueries);
        }

        // Build GraphQL query, and embed json encoded Elastic Search query
        $response = Mesh::request(Search::class)
            ->setVariables([
                "searchQuery" => json_encode($elasticSearchQuery),
                "page" => $pageNumber,
                "perPage" => $perPage
            ]);

        if (!is_null($this->branch)) {
            $response = $response->setBranchName($this->branch['name']);
        }

        $response = $response->usePublishedVersion()
            ->fetch();

        $responseArray = $response->toArray();
        $responseData = $responseArray['data'];

        return $responseData;
    }

    /**
     * Parses the search queries and prepares the Elastic Search
     *
     * @param array $searchQuery|[]
     * @return array Array of Elastic Search queries
     * @deprecated 1.1.0 This property is not used anymore by SearchHandler
     */
    public function prepareQuery(array $searchQuery = [])
    {
        $elasticSearchQuery = [];
        $searchFilter = array_filter($searchQuery);

        // Collect valid search filter params, and make the elastic search query
        if (!empty($searchFilter)) {
            $filterQueries = $this->filterKeys;

            // Loop through all provided searchParams
            foreach ($searchFilter as $filterKey => $filterTerm) {
                // Check if its existing in the config
                if (isset($filterQueries[$filterKey])) {
                    $elasticSearchFilter = $filterQueries[$filterKey];

                    // Recursive search for the searchQuery placeholder, and replace with the filterTerm
                    array_walk_recursive($elasticSearchFilter, function (&$searchQueryPlaceholder) use ($filterTerm) {
                        if (strpos($searchQueryPlaceholder, SearchHandler::$searchQueryPlaceholder) !== false) {
                            $searchQueryPlaceholder = str_replace(
                                SearchHandler::$searchQueryPlaceholder,
                                $filterTerm,
                                $searchQueryPlaceholder
                            );
                        }
                    });

                    // Merge the query to all ElasticSearch query
                    $elasticSearchQuery = array_merge_recursive($elasticSearchQuery, $elasticSearchFilter);
                }
            }
        }

        return $elasticSearchQuery;
    }

    /**
     * Prepares display details for a view from search results
     *
     * @param array $result
     * @param int $page
     * @param array $queryArray|[]
     * @return array Array consists of the search details and rootNode.
     * @throws \Throwable
     */
    public function prepareContext(array $result, int $page, array $queryArray = [])
    {
        $resultContent = $result['content'] ?? [];
        $resultCount = 0;
        $pageCount = 0;
        $resultData = [];
        $currentPage = 0;

        // Check if minimal required details are there for the searchResults, then populate the variables
        if (isset($resultContent['elements']) &&
            isset($resultContent['totalCount']) &&
            isset($resultContent['pageCount']) &&
            isset($resultContent['currentPage'])) {
            $resultData = $resultContent['elements'];
            $resultCount = $resultContent['totalCount'];
            $pageCount = $resultContent['pageCount'];
            $currentPage = $resultContent['currentPage'];
        }

        // Get context for navigation and footer, etc
        $context = $this->getRootNode();

        // Add search data to the context
        $context['search'] = [
            'results' => $resultData,
            'resultCount' => $resultCount,
            'pageCount' => $pageCount,
            'currentPage' => $currentPage,
            'page' => $page,
            'query' => $queryArray
        ];

        return $context;
    }

    /**
     * Parse request for search queries which defined in the configuration
     *
     * @param Request $request
     * @return array Array of filterKeys
     * @deprecated 1.1.0 This property is not used anymore by SearchHandler
     */
    public function getSearchQueries(Request $request)
    {
        $searchQuery = [];

        // Loads ElasticSearch filterKey from config if its there, then get their names
        $filterKeys = array_keys($this->filterKeys);

        // Loop through the request's query params, and check if its exists in the filterKeys and the value is non-empty
        foreach ($request->query as $key => $value) {
            if (!empty($value) && in_array($key, $filterKeys)) {
                // If exists, add to the searchQuery
                $searchQuery[$key] = $value;
            }
        }

        return $searchQuery;
    }

    /**
     * Provides an Elastic Search base query
     *
     * @return array Base Elastic Search query with the configured schema name
     * @deprecated 1.1.0 This property is not used anymore by SearchHandler
     */
    public function getBaseQuery()
    {
        // TODO check if this can be improved or it is needed to be separated
        return [
            'query' => [
                'bool' => [
                    'must' => [
                        [
                            'term' => [
                                'schema.name.raw' => config('portal.schemas.content')
                            ]
                        ]
                    ]
                ]
            ]
        ];
    }
}
