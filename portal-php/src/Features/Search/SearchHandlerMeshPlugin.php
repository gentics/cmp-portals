<?php

namespace Gentics\PortalPhp\Features\Search;

use Gentics\PortalPhp\Features\DataProvider\DataProvider;
use Gentics\PortalPhp\Features\Proxy\Filter\AddAuthorizationHeader;
use Gentics\PortalPhp\Features\Proxy\Filter\RemoveRoutePath;
use \Illuminate\Http\Request;
use Illuminate\Http\Response;

/**
 * Class SearchHandlerMeshPlugin
 * @package Gentics\PortalPhp\Features\Search
 */
class SearchHandlerMeshPlugin extends SearchHandler
{
    /**
     * It takes the query params from request and runs search, also
     * handles pagination.
     *
     * @param Request $request
     * @param int $page
     * @param string|null $branchName
     * @param string|null $language
     * @return array
     * @throws \GuzzleHttp\Exception\GuzzleException
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @throws \Throwable
     */
    public function handle(Request $request, int $page = 1, ?string $branchName = null, ?string $language = null)
    {
        $result = [];
        $searchQuery = $request->input('q');
        $searchTags = $request->input('t');

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
            $result = $this->runSearch($searchQuery, $searchTags, $branchName, $language, $page);
        }

        $searchContextQuery = [];

        if ($searchQuery) {
            $searchContextQuery = [ 'q' => $searchQuery ];
        }

        // Prepare results array
        $context = $this->prepareContext($result, $page, $searchContextQuery);

        return $context;
    }

    /**
     * @param String $query
     * @param String|null $tags
     * @param String|null $branchName
     * @param String|null $language
     * @param int $page
     * @return string|null
     * @throws \GuzzleHttp\Exception\GuzzleException
     */
    public function runSearch(String $query, ?String $tags, ?String $branchName, ?String $language, int $page)
    {
        $queryParams = [];
        $searchApiUrl = $this->projectName . '/plugins/search/search';

        $queryParams['q'] = $query;

        // Add tags if not empty
        if (!empty($tags)) {
            $queryParams['t'] = $tags;
        }

        // Add branchName if specified
        if (!empty($branchName)) {
            $queryParams['branch'] = $branchName;
        }

        // Add language if specified
        if (!empty($language)) {
            $queryParams['l'] = $language;
        }

        if ($page > 0) {
            $queryParams['p'] = $page;
        }

        if (config('portal.meshNoWaitForIdle', true) === true) {
            $queryParams['wait'] = 'false';
        }

        $searchApiQuery = http_build_query($queryParams);
        $searchApiUrl .= '?' . $searchApiQuery;

        $response_data = null;

        $headers = [];

        $token = $this->getToken();
        if (!is_null($token)) {
            $headers['Authorization'] = "Bearer " . $token;
        }

        $response = $this->mesh->request('GET', $searchApiUrl, [
            'headers' => $headers
        ]);

        if ($response->getStatusCode() == Response::HTTP_OK) {
            $response_data = json_decode((string) $response->getBody(), true);
        }

        return $response_data;
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
        $resultContent = $result ?? [];
        $resultCount = 0;
        $resultData = [];

        // Check if minimal required details are there for the searchResults, then populate the variables
        if (isset($resultContent['results']) &&
            isset($resultContent['totalHits'])) {
            $resultData = $resultContent['results'];
            $resultCount = $resultContent['totalHits'];
        }

        // Get context for navigation and footer, etc
        $context = $this->getRootNode();

        // Add search data to the context
        $context['search'] = [
            'results' => $resultData,
            'resultCount' => $resultCount,
            'page' => $page,
            'query' => $queryArray,
            'searchtags' => $resultContent['searchtags'] ?? [],
            'hasMore' => $resultContent['hasMore'] ?? false,
            'didYouMean' => $resultContent['didYouMean'] ?? null
        ];

        return $context;
    }

    /**
     * Proxy requests to Mesh Plugin
     *
     * @return \Psr\Http\Message\ResponseInterface
     * @throws \Throwable
     */
    public function proxy()
    {
        /**
         * Proxy requests directly to the Search Plugin
         * with passing the current client token (or null, if not logged in).
         */
        return $this->mesh->proxy('/' . $this->projectName . '/plugins/search/', [
            new RemoveRoutePath(),
            new AddAuthorizationHeader($this->getToken())
        ]);
    }

    /**
     * Returns a token for the requests
     *
     * @return string|null The token for use with Search
     */
    protected function getToken()
    {
        if (false === $this->portalPhp->config('search.useAdminToken', false, true)) {
            $token = $this->portalPhp->getClientToken();
        } else {
            $token = $this->portalPhp->config('apiKey', null, true);
        }

        return $token;
    }
}
