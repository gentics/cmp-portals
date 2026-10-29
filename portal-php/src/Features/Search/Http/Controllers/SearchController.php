<?php

namespace Gentics\PortalPhp\Features\Search\Http\Controllers;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\Features\Search\SearchHandlerMeshPlugin;
use Gentics\PortalPhp\Features\Search\SearchHandler;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Http\Controllers\BaseController;
use Gentics\PortalPhp\PortalPhp;

use Illuminate\Http\Request;

/*
 * @group Search
 */
class SearchController extends BaseController
{

    /**
     * @var string $searchView
     */
    protected $searchView = 'search';

    /**
     * @var SearchHandler|null
     */
    protected $handler = null;

    /**
     * SearchController constructor.
     * @param SearchHandler $handler
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    public function __construct(SearchHandler $handler)
    {
        $portalPhp = app()->make(PortalPhp::class);
        parent::__construct($portalPhp);

        $this->handler = $handler;
    }

    /**
     * Perform search
     *
     * @queryParam q string required Any query Example: whatever
     * @queryParam p int Page Example: 1
     * @queryParam b string Branch name Example: demo_branch
     * @response 303 A redirect to the search results page
     *
     * @param Request $request
     * @return \Illuminate\Contracts\View\Factory|\Illuminate\View\View
     * @throws \Throwable
     */
    public function index(Request $request)
    {
        $page = empty($request->query('p')) ? 0 : $request->query('p');

        // priority 1 - get custom branch name
        $branchName = empty($request->query('b')) ? null : $request->query('b');

        $path = $request->path();

        // priority 2 - get branch name from portal config
        if (empty($branchName)) {
            $path = Mesh::normalizePath($path);
            $branchName = $this->portalPhp->findBranchNameFromRequest($path);
        }

        // priority 3 - set project name as fallback branch
        if (empty($branchName)) {
            $branchName = config('portal.projectName', null);
        }

        $data = $this->handler->handle($request, $page, $branchName);

        return view($this->searchView, $data);
    }

    /**
     * Passthrough to Gentics Mesh API
     *
     * @urlParam any string required Any path, including the query parameters. Example: ?q=whatever
     * @response {
     *   "results": [
     *     {
     *        "raw": {
     *          "uuid": "1234567890abcdef0987654321",
     *          "project": [
     *            "name": "Demo"
     *          ],
     *          "fields": {
     *            "content": "whatever you have asked, here is an answer"
     *          }
     *        }
     *     }
     *   ],
     *   "totalHits": 1,
     *   "hasMore": false,
     *   "didYouMean": null
     * }
     *
     * @return \Psr\Http\Message\ResponseInterface
     * @throws \Throwable
     */
    public function api()
    {
        if (class_basename($this->handler) === class_basename(SearchHandlerMeshPlugin::class)) {
            return $this->handler->proxy();
        } else {
            throw new PortalPhpException('You must switch to the Mesh Plugin Search Handler to use the API');
        }
    }
}
