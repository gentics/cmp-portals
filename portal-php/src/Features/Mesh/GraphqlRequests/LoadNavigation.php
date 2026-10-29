<?php
namespace Gentics\PortalPhp\Features\Mesh\GraphqlRequests;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\MeshException;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequest;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\Traits\WithCache;

/**
 * Class LoadNavigation
 * {@inheritDoc}
 * @method $this|LoadNavigation setPath(string $path)
 * @method $this|LoadNavigation setBranchName(string $branchName)
 * @method mixed getPath()
 * @method mixed getBranchName()
 * @package Gentics\PortalPhp\Features\Mesh\GraphqlRequests
 */
class LoadNavigation extends GraphqlRequest
{
    use WithCache {
        fetchCached as traitFetchCached;
    }

    /** List of Accessible Properties */
    protected $properties = [
        'branchName',
        'path',
    ];

    /** @var string Override the branchName variable to skip auto-detect. */
    protected $branchName;
    /** @var string Set request path */
    protected $path;

    /** @var string The prefix to be used for the cacheKey */
    protected $cacheKeyPrefix = "mesh:graphql_nav:";
    /** @var int Default cache timeout */
    protected $cacheTimeDefault = 15;
    /** @var string Cache timeout configuration key */
    protected $cacheTimeConfigKey = "portal.cache.navigation.cacheTime";

    public function fetch()
    {
        $parameters = [];
        $branchName = $this->finalBranchName();

        if (!empty($branchName)) {
            $parameters['branch'] = $branchName;
        }
        $parameters['version'] = 'published';

        $this->appendQueryParameters($parameters);
        $this->useMultilingualCurrentLanguageFilter();
        return parent::fetch();
    }

    /**
     * @return \Gentics\PortalPhp\Features\Mesh\GraphqlResponse|mixed
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @throws \Throwable
     */
    public function fetchCached()
    {
        // Try to get a cache identifier, if this fails then use navigation directly without cache.
        // The cache key is only supported from Gentics Mesh 0.31.0
        try {
            $this->getCacheIdentifier();
        } catch (MeshException $exception) {
            return $this->fetch();
        }

        return $this->traitFetchCached();
    }

    /**
     * Generates unique cacheKey identifier from user's rolesHash
     *
     * @return string
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     * @throws MeshException|\Throwable
     */
    protected function getCacheIdentifier()
    {
        $branchName = $this->finalBranchName();
        $response = Mesh::request(LoadUser::class)
            ->setAdminClient(false)
            ->fetchCached()
            ->toArray()['data'];

        $key = ($response['me']['rolesHash'] ?? 'null') . ($branchName ?? 'null');
        if ($this->isMultilingualCurrentLanguageFilterEnabled()) {
            $key .= $this->portalPhp->getUserLanguage();
        }
        return sha1($key);
    }

    /**
     * Get branch name
     * @throws \Throwable
     */
    protected function finalBranchName()
    {
        $branchName = $this->branchName;

        if (!empty($this->path) && empty($branchName)) {
            $path = Mesh::normalizePath($this->path);
            $branchName = $this->portalPhp->findBranchNameFromRequest($path);
        }

        return $branchName;
    }
}
