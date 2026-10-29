<?php

namespace Gentics\PortalPhp\Features\Search;

use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadContent;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Traits\NeedsNavigation;
use Gentics\PortalPhp\Features\Mesh\Mesh;

/**
 * Class SearchHandler
 * @package Gentics\PortalPhp\Features\Search
 */
abstract class SearchHandler
{
    use NeedsNavigation;

    /**
     * This string needs to be replaced with query params in queries.
     *
     * @var string|"{searchQuery}"
     * @deprecated 1.1.0 This property is not used anymore by SearchHandler
     */
    protected static $searchQueryPlaceholder = "{searchQuery}";

    /**
     * @var PortalPhp|null
     */
    protected $portalPhp    = null;

    /**
     * @var \Gentics\Mesh\Client\MeshClient|null
     */
    protected $mesh         = null;

    /**
     * @var array The branch to search
     */
    protected $branch = null;

    /**
     * @var \Illuminate\Config\Repository|mixed|null
     */
    protected $projectName     = null;

    /**
     * SearchHandler constructor.
     * @param PortalPhp $portalPhp
     * @throws \Throwable
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
        $this->projectName = $this->portalPhp->config('projectName');
        $adminFallback = config('portal.compatibility.noAdminClientByDefault', true) === false;
        $this->mesh = $this->portalPhp->getMeshClient($adminFallback);
    }

    /**
     * Provides root node to allow display navigation and other root node
     * dependent features.
     *
     * @return mixed The Mesh response object of the current node
     * @throws \Throwable
     */
    public function getRootNode()
    {
        $path = '/';

        if (!is_null($this->branch)) {
            $path = $this->branch['pathPrefix'] . '/';
        }

        $responseData = Mesh::request(LoadContent::class)
            ->setPath($path)
            ->fetch()
            ->toArray()['data'];

        return $this->addNavigation($responseData, true, false, $path);
    }
}
