<?php
namespace Gentics\PortalPhp\Features\Mesh\GraphqlRequests;

use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\Traits\WithCache;

/**
 * Class LoadError
 * {@inheritDoc}
 * @package Gentics\PortalPhp\Features\Mesh\GraphqlRequests
 */
class LoadError extends LoadByPath
{
    use WithCache;

    /** @var string The prefix to be used for the cacheKey */
    protected $cacheKeyPrefix = "mesh:graphql_error:";
    /** @var int Default cache timeout */
    protected $cacheTimeDefault = 300;
    /** @var string Cache timeout configuration key */
    protected $cacheTimeConfigKey = "portal.cache.errorPage.cacheTime";

    /**
     * Generates unique cacheKey identifier from error page path
     *
     * @return string
     */
    protected function getCacheIdentifier()
    {
        $path = Mesh::normalizePath($this->path);
        return sha1($path);
    }
}
